package org.moboxlab.mbb.trpg.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.Lock;

import org.moboxlab.mbb.trpg.present.TrpgMessageTemplates;
import org.moboxlab.mbb.trpg.present.TrpgTitles;
import org.moboxlab.mbb.trpg.script.Script;
import org.moboxlab.mbb.trpg.script.ScriptAdmin;
import org.moboxlab.mbb.trpg.script.ScriptErrorCodes;
import org.moboxlab.mbb.trpg.script.ScriptException;
import org.moboxlab.mbb.trpg.script.ScriptGenerationService;
import org.moboxlab.mbb.trpg.script.ScriptImportMode;
import org.moboxlab.mbb.trpg.script.ScriptImportResult;
import org.moboxlab.mbb.trpg.script.ScriptImportService;
import org.moboxlab.mbb.trpg.script.ScriptInfo;
import org.moboxlab.mbb.trpg.script.ScriptIssue;
import org.moboxlab.mbb.trpg.script.ScriptValidationException;
import org.moboxlab.mbb.trpg.script.ScriptValidationResult;
import org.moboxlab.mbb.trpg.session.SessionActor;
import org.moboxlab.mbb.trpg.session.SessionResult;
import org.moboxlab.mbb.trpg.store.BlacklistRow;

/**
 * 库与权限 handler：script（KP 换本局剧本 + 剧本库管理）与 blacklist。
 *
 * <p>剧本库管理只对 BOT_ADMIN / OWNER 开放；黑名单由群管理员管本群，BOT_ADMIN / OWNER
 * 可在私聊按群号远程解封。S10 起 import / delete / gen 已启用：导入走分段暂存，
 * 生成异步执行、完成后按会话类型回执。</p>
 */
final class AdminCommandHandler {

    private static final int LIBRARY_PAGE_SIZE = 10;
    private static final String RETRY_NEXT = "请稍后重试。";
    private static final String PERMISSION_NEXT = "本指令仅限管理员使用。";
    /** 生成冷却的动作键（与其它指令共用同一份冷却登记）。 */
    private static final String GEN_ACTION = "script-gen";

    private AdminCommandHandler() {
    }

    static Map<TrpgSubcommand, TrpgSubcommandHandler> handlers() {
        Map<TrpgSubcommand, TrpgSubcommandHandler> map =
                new EnumMap<TrpgSubcommand, TrpgSubcommandHandler>(TrpgSubcommand.class);
        map.put(TrpgSubcommand.SCRIPT, new TrpgSubcommandHandler() {
            @Override
            public void handle(TrpgCommandContext context, SessionActor actor, String[] params) {
                script(context, actor, params);
            }
        });
        map.put(TrpgSubcommand.BLACKLIST, new TrpgSubcommandHandler() {
            @Override
            public void handle(TrpgCommandContext context, SessionActor actor, String[] params) {
                blacklist(context, actor, params);
            }
        });
        return Collections.unmodifiableMap(map);
    }

    static void script(TrpgCommandContext context, SessionActor actor, String[] params) {
        String action = arg(params, 1);
        String lower = action.toLowerCase(Locale.ROOT);
        boolean libraryAction = "list".equals(lower) || "show".equals(lower)
                || "import".equals(lower) || "delete".equals(lower) || "gen".equals(lower);
        // 判定顺序固定为权限先于参数（§5.4）：库管理先卡 BOT_ADMIN，再校验具体参数。
        if (libraryAction && !requireBotAdmin(context, actor)) {
            return;
        }
        if (action.isEmpty()) {
            context.rejectError(actor, "script-argument", "参数不对：缺少剧本 ID 或库管理动作",
                    "发送 /trpg help 查看用法。");
            return;
        }
        if ("list".equals(lower)) {
            listScripts(context, actor, params);
            return;
        }
        if ("show".equals(lower)) {
            showScript(context, actor, params);
            return;
        }
        if ("import".equals(lower)) {
            importScript(context, actor, params);
            return;
        }
        if ("delete".equals(lower)) {
            deleteScript(context, actor, params);
            return;
        }
        if ("gen".equals(lower)) {
            generateScript(context, actor, params);
            return;
        }
        // 其余情况按剧本 ID 处理：换本局剧本只允许群内、由局面层判定 KP 与招募期。
        if (!actor.inGroup()) {
            context.rejectError(actor, "script-private", "请在群内使用",
                    TrpgCommandService.PRIVATE_NEXT);
            return;
        }
        SessionResult result = context.sessions().changeScript(actor, action);
        if (!context.accept(actor, result)) {
            return;
        }
        context.reply(actor, TrpgMessageTemplates.scriptChanged(
                context.loadScript(result.snapshot() == null ? action : result.snapshot().scriptId())));
    }

    /**
     * /trpg script import [replace]：开始分段导入，或处理 end / cancel 控制字。
     *
     * <p>暂存期间的非控制字消息由消息入口直接吞掉，不会走到这里；因此本方法只负责
     * 「开始一段导入」与「没有暂存时显式发送 end / cancel」的失败回执。</p>
     */
    private static void importScript(TrpgCommandContext context, SessionActor actor,
                                     String[] params) {
        ScriptAdmin admin = context.scriptAdmin();
        if (admin == null) {
            context.rejectError(actor, "script-admin-unavailable", "剧本管理服务不可用，请稍后重试",
                    RETRY_NEXT);
            return;
        }
        ScriptImportService imports = admin.imports();
        String sub = arg(params, 2).toLowerCase(Locale.ROOT);
        long groupId = actor.groupId();
        long userId = actor.userId();
        if ("end".equals(sub)) {
            endImport(context, actor, imports);
            return;
        }
        if ("cancel".equals(sub)) {
            cancelImport(context, actor, imports);
            return;
        }
        if (imports.hasPending(groupId, userId)) {
            context.rejectNotice(actor, "script-import-pending",
                    "已有一段导入在进行：继续发送剧本内容即可，发 /trpg script import end 结束，"
                            + "/trpg script import cancel 取消");
            return;
        }
        boolean replace = "replace".equals(sub);
        if (!sub.isEmpty() && !replace) {
            context.rejectError(actor, "script-import-argument",
                    "参数不对：导入只支持 replace", "用法：/trpg script import [replace]");
            return;
        }
        try {
            imports.begin(groupId, userId, replace ? ScriptImportMode.REPLACE : ScriptImportMode.CREATE);
            context.reply(actor, TrpgMessageTemplates.scriptImportGuide(
                    replace ? ScriptImportMode.REPLACE : ScriptImportMode.CREATE,
                    imports.maxBytes(), context.config().scriptImportIdleMinutes(),
                    context.config().scriptImportMaxMinutes()));
        } catch (ScriptException error) {
            context.rejectError(actor, "script-import", error.code() + "：" + error.getMessage(),
                    "请稍后重试，或发送 /trpg script import cancel 清理暂存。");
        }
    }

    /** 指令层显式 end：暂存期内的正常路径由消息入口处理，这里兜住无暂存与直接发送。 */
    private static void endImport(TrpgCommandContext context, SessionActor actor,
                                  ScriptImportService imports) {
        long groupId = actor.groupId();
        long userId = actor.userId();
        try {
            ScriptImportResult result = imports.end(groupId, userId);
            context.reply(actor, TrpgMessageTemplates.scriptImportSuccess(result));
        } catch (ScriptValidationException error) {
            if (imports.hasPending(groupId, userId)) {
                ScriptValidationResult validation = error.validation();
                ScriptIssue first = validation == null || validation.errors().isEmpty()
                        ? null : validation.errors().get(0);
                context.reply(actor, TrpgMessageTemplates.scriptImportIssue(
                        imports.pendingBytes(groupId, userId), imports.maxBytes(), first));
            } else {
                context.reply(actor, TrpgMessageTemplates.scriptImportRejected(error.validation()));
            }
        } catch (ScriptException error) {
            context.rejectError(actor, "script-import-end", error.code() + "：" + error.getMessage(),
                    "发送 /trpg script import 开始一段导入。");
        }
    }

    private static void cancelImport(TrpgCommandContext context, SessionActor actor,
                                     ScriptImportService imports) {
        try {
            imports.cancel(actor.groupId(), actor.userId());
            context.reply(actor, TrpgMessageTemplates.scriptImportCancelled());
        } catch (ScriptException error) {
            context.rejectError(actor, "script-import-cancel", error.code() + "：" + error.getMessage(),
                    "当前没有待取消的导入。");
        }
    }

    /** /trpg script delete <id>：仅删除导入剧本，内置与使用中的剧本由剧本库拒绝。 */
    private static void deleteScript(TrpgCommandContext context, SessionActor actor,
                                     String[] params) {
        String scriptId = arg(params, 2);
        if (scriptId.isEmpty()) {
            context.rejectError(actor, "script-delete", "参数不对：缺少剧本 ID",
                    "用法：/trpg script delete <剧本ID>；发送 /trpg script list 查看可用剧本。");
            return;
        }
        ScriptInfo info = context.catalog().info(scriptId);
        if (info == null) {
            context.rejectError(actor, "script-missing", "剧本不存在：" + scriptId,
                    "发送 /trpg script list 查看可用剧本。");
            return;
        }
        try {
            context.catalog().delete(scriptId);
            context.reply(actor, TrpgMessageTemplates.scriptDeleted(info));
        } catch (ScriptException error) {
            context.rejectError(actor, "script-delete", deleteReason(error), "可换一个剧本，或先结束引用了该剧本的局。");
        }
    }

    private static String deleteReason(ScriptException error) {
        String code = error == null ? "" : error.code();
        if (ScriptErrorCodes.E_BUILTIN.equals(code)) {
            return ScriptErrorCodes.E_BUILTIN + "：内置剧本不能删除";
        }
        if (ScriptErrorCodes.E_IN_USE.equals(code)) {
            return ScriptErrorCodes.E_IN_USE + "：该剧本正被未归档的局引用，请先结束这些局";
        }
        if (ScriptErrorCodes.E_NOT_FOUND.equals(code)) {
            return ScriptErrorCodes.E_NOT_FOUND + "：剧本不存在或已被删除";
        }
        return code + "：" + (error == null ? "删除失败" : error.getMessage());
    }

    /** /trpg script gen <题材> [人数] [时长]：异步生成，完成后 @ 发起人。 */
    private static void generateScript(TrpgCommandContext context, SessionActor actor,
                                       String[] params) {
        ScriptAdmin admin = context.scriptAdmin();
        if (admin == null) {
            context.rejectError(actor, "script-admin-unavailable", "剧本生成服务不可用，请稍后重试",
                    RETRY_NEXT);
            return;
        }
        String theme = arg(params, 2);
        if (theme.isEmpty()) {
            context.rejectError(actor, "script-gen", "参数不对：缺少题材",
                    "用法：/trpg script gen <题材> [人数 1-20] [时长分钟 15-600]");
            return;
        }
        int players = intOrDefault(arg(params, 3), 3);
        int minutes = intOrDefault(arg(params, 4), 60);
        if (players < 1 || players > 20) {
            context.rejectError(actor, "script-gen-players", "参数不对：人数需在 1-20 之间",
                    "人数缺省 3，例如 /trpg script gen 雪山山庄 4 90");
            return;
        }
        if (minutes < 15 || minutes > 600) {
            context.rejectError(actor, "script-gen-minutes", "参数不对：时长需在 15-600 分钟之间",
                    "时长缺省 60，例如 /trpg script gen 雪山山庄 4 90");
            return;
        }
        long now = context.clock().nowMillis();
        long cooldownMillis = context.config().scriptGenCooldownSeconds() * 1000L;
        // 冷却按「发起人」计，不按群：同一个人在不同群共享 60 秒窗（README §240）。
        long remaining = context.cooldowns().remainingMillis(GEN_ACTION, actor.userId(),
                SessionActor.PRIVATE_GROUP_ID, now, cooldownMillis);
        if (remaining > 0L) {
            context.rejectNotice(actor, "script-gen-cooldown",
                    "生成冷却中，还需等待 " + ((remaining + 999L) / 1000L) + " 秒");
            return;
        }
        if (!admin.generation().tryReserve()) {
            context.rejectError(actor, "script-gen-busy",
                    ScriptErrorCodes.E_AI_BUSY + "：已有一个剧本在生成中，请等它完成后再试",
                    "生成通常需要几十秒，可以稍后再发送。");
            return;
        }
        context.cooldowns().mark(GEN_ACTION, actor.userId(), SessionActor.PRIVATE_GROUP_ID, now);
        final long groupId = actor.groupId();
        final long userId = actor.userId();
        final int finalPlayers = players;
        final int finalMinutes = minutes;
        context.reply(actor, TrpgMessageTemplates.scriptGenStarted(theme, players, minutes));
        try {
            admin.executor().execute(new Runnable() {
                @Override
                public void run() {
                    deliverGeneration(context, admin.generation(), theme, finalPlayers,
                            finalMinutes, groupId, userId);
                }
            });
        } catch (RuntimeException error) {
            admin.generation().releaseReservation();
            context.rejectError(actor, "script-gen-submit", "生成任务提交失败，请稍后重试",
                    RETRY_NEXT);
        }
    }

    /** 异步任务体：只调生成服务并发送结果，不碰局面与库锁。 */
    private static void deliverGeneration(TrpgCommandContext context, ScriptGenerationService generation,
                                          String theme, int players, int minutes, long groupId,
                                          long userId) {
        String message;
        try {
            message = TrpgMessageTemplates.scriptGenResult(
                    generation.generateReserved(theme, players, minutes, userId));
        } catch (RuntimeException error) {
            message = TrpgMessageTemplates.error("剧本生成异常：" + error.getMessage(),
                    "可换题材重试，或按 /trpg script import 手工导入。");
        } finally {
            // generateReserved 内部已释放；异常路径再兜一次，避免槽位永久占用。
            generation.releaseReservation();
        }
        try {
            if (groupId > SessionActor.PRIVATE_GROUP_ID) {
                context.presenter().sendGroupAt(groupId, userId, message);
            } else {
                context.presenter().sendPrivate(userId, message);
            }
        } catch (RuntimeException error) {
            // 发送失败只吞掉：结果已写入剧本库，用户可再用 script list 查看。
        }
    }

    private static int intOrDefault(String token, int fallback) {
        String text = token == null ? "" : token.trim();
        if (text.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    private static void listScripts(TrpgCommandContext context, SessionActor actor, String[] params) {
        List<ScriptInfo> infos = new ArrayList<ScriptInfo>();
        Lock lock = context.catalog().acquireReadLock();
        lock.lock();
        try {
            List<ScriptInfo> all = context.catalog().list();
            if (all != null) {
                infos.addAll(all);
            }
        } finally {
            lock.unlock();
        }
        int totalPages = Math.max(1, (infos.size() + LIBRARY_PAGE_SIZE - 1) / LIBRARY_PAGE_SIZE);
        int page = pageOf(arg(params, 2), totalPages);
        List<String> entries = new ArrayList<String>();
        int begin = (page - 1) * LIBRARY_PAGE_SIZE;
        for (int index = begin; index < Math.min(begin + LIBRARY_PAGE_SIZE, infos.size()); index++) {
            ScriptInfo info = infos.get(index);
            entries.add("《" + info.name() + "》（" + info.id() + "）· " + info.source().label());
        }
        context.reply(actor, TrpgMessageTemplates.scriptList(entries, page, totalPages,
                context.catalog().skippedFileCount()));
    }

    private static void showScript(TrpgCommandContext context, SessionActor actor, String[] params) {
        String scriptId = arg(params, 2);
        if (scriptId.isEmpty()) {
            context.rejectError(actor, "script-show", "参数不对：缺少剧本 ID",
                    "发送 /trpg help 查看用法。");
            return;
        }
        Script script = context.loadScript(scriptId);
        if (script == null) {
            context.rejectError(actor, "script-missing", "剧本不存在：" + scriptId,
                    "发送 /trpg script list 查看可用剧本。");
            return;
        }
        context.presenter().sendScriptPage(actor.groupId(), actor.userId(), script);
    }

    static void blacklist(TrpgCommandContext context, SessionActor actor, String[] params) {
        if (context.blacklist() == null) {
            context.rejectError(actor, "blacklist-unavailable", "黑名单服务不可用，请稍后重试",
                    "可以稍后再发送一次。");
            return;
        }
        // 判定顺序固定为权限先于参数（§5.4）：群内要群管理员，私聊要 BOT_ADMIN。
        if (actor.inGroup() ? !actor.groupAdmin() : !actor.botAdmin()) {
            context.rejectError(actor, "blacklist-permission",
                    actor.inGroup() ? "只有群管理员能管理本群黑名单" : "私聊解封仅限 BOT_ADMIN 或 OWNER",
                    PERMISSION_NEXT);
            return;
        }
        String action = arg(params, 1).toLowerCase(Locale.ROOT);
        if ("add".equals(action)) {
            addToBlacklist(context, actor, params);
            return;
        }
        if ("remove".equals(action)) {
            removeFromBlacklist(context, actor, params);
            return;
        }
        if ("list".equals(action)) {
            listBlacklist(context, actor);
            return;
        }
        context.rejectError(actor, "blacklist-argument", "参数不对：黑名单只支持 add / remove / list",
                "发送 /trpg help 查看用法。");
    }

    private static void addToBlacklist(TrpgCommandContext context, SessionActor actor,
                                       String[] params) {
        if (!actor.inGroup()) {
            context.rejectError(actor, "blacklist-permission", "拉黑只能在群内进行",
                    "私聊用法：/trpg blacklist remove <群号> <QQ>");
            return;
        }
        long target = TrpgCommandService.parseUserId(arg(params, 2));
        if (target <= 0L) {
            context.rejectError(actor, "blacklist-target", "参数不对：无法识别要拉黑的成员",
                    "用法：/trpg blacklist add <@玩家> [原因]");
            return;
        }
        String reason = joinFrom(params, 3);
        boolean added = context.blacklist().add(actor.groupId(), target, actor.userId(), reason,
                context.clock().nowMillis());
        String name = context.nameOf(actor.groupId(), target);
        if (!added) {
            // add 幂等：false 只表示这次没写进去（含 -1），重试即可。
            context.rejectError(actor, "blacklist-write", "拉黑失败，请稍后重试",
                    "可以再发送一次 /trpg blacklist add 重试。");
            return;
        }
        context.reply(actor, TrpgMessageTemplates.notice("已把 " + name + " 加入本群黑名单。"));
    }

    private static void removeFromBlacklist(TrpgCommandContext context, SessionActor actor,
                                            String[] params) {
        long groupId;
        long target;
        if (actor.inGroup()) {
            groupId = actor.groupId();
            target = TrpgCommandService.parseUserId(arg(params, 2));
        } else {
            Long parsedGroup = longValue(arg(params, 2));
            groupId = parsedGroup == null ? 0L : parsedGroup.longValue();
            target = TrpgCommandService.parseUserId(arg(params, 3));
        }
        if (groupId <= 0L || target <= 0L) {
            context.rejectError(actor, "blacklist-target", "参数不对：无法识别群号或成员",
                    "私聊用法：/trpg blacklist remove <群号> <QQ>");
            return;
        }
        boolean removed = context.blacklist().remove(groupId, target);
        String name = context.nameOf(groupId, target);
        if (!removed) {
            // false 有两种来源：本来就不在名单，或 -1 语句未执行。再读一次名单区分。
            if (context.blacklist().contains(groupId, target)) {
                context.rejectError(actor, "blacklist-write", "解封失败，请稍后重试",
                        "可以再发送一次重试。");
            } else {
                context.rejectNotice(actor, "blacklist-absent", "对方不在该群黑名单中：" + name);
            }
            return;
        }
        context.reply(actor, TrpgMessageTemplates.notice(
                "已把 " + name + " 移出群 " + groupId + " 的黑名单。"));
    }

    private static void listBlacklist(TrpgCommandContext context, SessionActor actor) {
        if (!actor.inGroup()) {
            context.rejectError(actor, "blacklist-permission", "黑名单列表只能在群内查看",
                    "私聊用法：/trpg blacklist remove <群号> <QQ>");
            return;
        }
        List<BlacklistRow> rows = context.blacklist().list(actor.groupId());
        // 名单可能超过【提示】的 120 字上限，这里按群内正文上限由拆分器处理，不套 notice 模板。
        StringBuilder message = new StringBuilder(TrpgTitles.of(TrpgTitles.NOTICE))
                .append('\n').append("本群黑名单（").append(rows.size()).append(" 人）");
        if (rows.isEmpty()) {
            message.append("：暂无。");
        } else {
            for (BlacklistRow row : rows) {
                message.append('\n').append(context.nameOf(actor.groupId(), row.userId()));
                if (row.reason() != null && !row.reason().trim().isEmpty()) {
                    message.append("（").append(row.reason().trim()).append("）");
                }
            }
        }
        context.reply(actor, message.toString());
    }

    private static boolean requireBotAdmin(TrpgCommandContext context, SessionActor actor) {
        if (actor.botAdmin()) {
            return true;
        }
        context.rejectError(actor, "script-library-permission",
                "剧本库管理仅限 BOT_ADMIN 或 OWNER", PERMISSION_NEXT);
        return false;
    }

    private static int pageOf(String raw, int totalPages) {
        Long value = longValue(raw);
        int page = value == null ? 1 : value.intValue();
        if (page < 1) {
            return 1;
        }
        return Math.min(page, totalPages);
    }

    private static String arg(String[] params, int index) {
        if (params == null || index < 0 || index >= params.length || params[index] == null) {
            return "";
        }
        return params[index].trim();
    }

    private static String joinFrom(String[] params, int from) {
        StringBuilder text = new StringBuilder();
        if (params == null) {
            return "";
        }
        for (int index = from; index < params.length; index++) {
            String value = arg(params, index);
            if (value.isEmpty()) {
                continue;
            }
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(value);
        }
        return text.toString();
    }

    private static Long longValue(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(token.trim());
        } catch (NumberFormatException error) {
            return null;
        }
    }
}
