package org.moboxlab.mbb.trpg.session;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.moboxlab.mbb.trpg.config.TrpgConfig;
import org.moboxlab.mbb.trpg.script.Scene;
import org.moboxlab.mbb.trpg.script.Script;
import org.moboxlab.mbb.trpg.script.ScriptCatalog;
import org.moboxlab.mbb.trpg.script.ScriptTrigger;
import org.moboxlab.mbb.trpg.store.CardData;
import org.moboxlab.mbb.trpg.store.MemberRow;
import org.moboxlab.mbb.trpg.store.SessionRow;
import org.moboxlab.mbb.trpg.store.SessionStore;

/**
 * 局面流程动作（规格 §6.1 的 KP 子集）：场景推进、技能调整、邀请、接管、跳过与换剧本。
 *
 * <p>所有动作都走 {@link SessionService#withGroupLockValue}：判定顺序固定为
 * 群内 → 权限 → 状态 → 参数 → 冷却 → 执行，写库在群锁内完成，事件在解锁后发布。
 * 参数解析与文案拼装属于指令层，这里只接收已解析好的标量并返回结构化明细。</p>
 *
 * <p>检定、理智与对抗由 {@link SessionCheckService} 承担，两边共用
 * {@link SessionPlaySupport} 的静态工具。</p>
 */
final class SessionPlayService {

    private static final String ACTION_SCENE = "scene";
    private static final String ACTION_ADJUST = "adjust";
    private static final String ACTION_INVITE = "invite";
    private static final String ACTION_TAKE = "take";
    private static final String ACTION_SKIP = "skip";
    private static final String ACTION_SCRIPT = "script";

    private static final String EVENT_SCENE = "scene";
    private static final String EVENT_ADJUST = "adjust";
    private static final String EVENT_INVITE = "invite";
    private static final String EVENT_TAKE = "take";
    private static final String EVENT_SKIP = "skip";
    private static final String EVENT_CLUE = "clue";

    /** 技能值合法的闭区间端点。 */
    private static final int SKILL_MIN = 0;
    private static final int SKILL_MAX = 99;

    private final SessionService sessions;
    private final SessionStore store;
    private final ScriptCatalog catalog;
    private final TrpgConfig config;

    SessionPlayService(SessionService sessions, SessionStore store, ScriptCatalog catalog,
                       TrpgConfig config) {
        if (sessions == null) {
            throw new IllegalArgumentException("局面服务不能为空");
        }
        if (store == null) {
            throw new IllegalArgumentException("存储不能为空");
        }
        if (catalog == null) {
            throw new IllegalArgumentException("剧本库不能为空");
        }
        if (config == null) {
            throw new IllegalArgumentException("配置不能为空");
        }
        this.sessions = sessions;
        this.store = store;
        this.catalog = catalog;
        this.config = config;
    }

    /** 推进到指定场景并解锁本场景尚未解锁的线索（§6.1 scene）。 */
    PlayResult<SessionOutcomes.Scene> scene(SessionActor actor, final String rawTitle) {
        final SessionActor request = SessionService.requireActor(actor);
        if (!request.inGroup()) {
            return PlayResult.rejected(SessionResult.rejected(SessionReject.NOT_IN_GROUP, "请在群内使用"));
        }
        return sessions.withGroupLockValue(request, ACTION_SCENE,
                new SessionService.LockedOutcome<SessionOutcomes.Scene>() {

                    @Override
                    public PlayResult<SessionOutcomes.Scene> run(SessionService.Turn turn) {
                        return sceneInLock(turn, rawTitle);
                    }
                });
    }

    private PlayResult<SessionOutcomes.Scene> sceneInLock(SessionService.Turn turn, String rawTitle) {
        SessionRow row = turn.active();
        if (row == null) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.STATE_UNAVAILABLE,
                    "本群暂无局"));
        }
        if (row.kpUserId() != turn.actor.userId()) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.NO_PERMISSION,
                    "只有 KP 能推进场景"));
        }
        if (row.state() != SessionState.RUNNING) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.STATE_UNAVAILABLE,
                    "本局尚未开局"));
        }
        String title = rawTitle == null ? "" : rawTitle.trim();
        if (title.isEmpty()) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.BAD_ARGUMENT,
                    "参数不对：缺少场景标题"));
        }
        Script script = sessions.loadScript(row.scriptId());
        if (script == null) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.STATE_UNAVAILABLE,
                    "本局剧本不可用：" + row.scriptId()));
        }
        Scene scene = script.sceneByTitle(title);
        if (scene == null) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.BAD_ARGUMENT,
                    "剧本里没有该场景：" + title));
        }
        SessionResult cooling = sessions.cooling(ACTION_SCENE, turn,
                config.defaultCooldownSeconds());
        if (cooling != null) {
            return PlayResult.rejected(cooling);
        }
        int sceneIndex = SessionPlaySupport.indexOf(script, scene);
        int affected = store.casScene(row.id(), SessionState.RUNNING, sceneIndex, scene.title(),
                turn.now);
        if (affected < 0) {
            return PlayResult.rejected(SessionResult.storageFailure("场景更新未生效，请稍后重试"));
        }
        if (affected == 0) {
            SessionRow current = store.findSession(row.id());
            if (current == null || current.state() != SessionState.RUNNING) {
                return PlayResult.rejected(sessions.reject(turn, SessionReject.STATE_UNAVAILABLE,
                        "本局状态已变化，请重新查看局面"));
            }
            return PlayResult.rejected(SessionResult.storageFailure("场景更新未生效，请稍后重试"));
        }
        Set<String> unlocked = new LinkedHashSet<String>();
        List<String> existing = store.listClueKeys(row.id());
        if (existing != null) {
            unlocked.addAll(existing);
        }
        List<String> newClueKeys = new ArrayList<String>();
        for (String clueId : scene.clueIds()) {
            if (unlocked.contains(clueId)) {
                continue;
            }
            sessions.appendLog(row.id(), EVENT_CLUE, turn.actor.userId(), clueId, turn.now);
            unlocked.add(clueId);
            newClueKeys.add(clueId);
        }
        sessions.appendLog(row.id(), EVENT_SCENE, turn.actor.userId(), scene.title(), turn.now);
        sessions.markCooldown(ACTION_SCENE, turn);
        String hints = sceneHint(script, scene.id(), newClueKeys);
        SessionResult ok = SessionResult.ok(turn.reload(row), "已进入场景：" + scene.title());
        return PlayResult.of(ok, new SessionOutcomes.Scene(ok, sceneIndex, scene.title(),
                newClueKeys, hints));
    }

    /** 技能调整（§6.1 adjust）：KP 改成员技能，越界拒绝并记日志。 */
    PlayResult<SessionOutcomes.Adjust> adjust(SessionActor actor, final long targetUserId,
                                              final String rawSkill, final String rawDelta) {
        final SessionActor request = SessionService.requireActor(actor);
        if (!request.inGroup()) {
            return PlayResult.rejected(SessionResult.rejected(SessionReject.NOT_IN_GROUP, "请在群内使用"));
        }
        return sessions.withGroupLockValue(request, ACTION_ADJUST,
                new SessionService.LockedOutcome<SessionOutcomes.Adjust>() {

                    @Override
                    public PlayResult<SessionOutcomes.Adjust> run(SessionService.Turn turn) {
                        return adjustInLock(turn, targetUserId, rawSkill, rawDelta);
                    }
                });
    }

    private PlayResult<SessionOutcomes.Adjust> adjustInLock(SessionService.Turn turn,
                                                            long targetUserId, String rawSkill,
                                                            String rawDelta) {
        SessionRow row = turn.active();
        if (row == null) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.STATE_UNAVAILABLE,
                    "本群暂无局"));
        }
        if (row.kpUserId() != turn.actor.userId()) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.NO_PERMISSION,
                    "只有 KP 能调整技能"));
        }
        if (row.state() != SessionState.RECRUITING && row.state() != SessionState.RUNNING) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.STATE_UNAVAILABLE,
                    "本局状态不可调整"));
        }
        if (targetUserId <= 0L) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.BAD_ARGUMENT,
                    "参数不对：缺少目标玩家"));
        }
        if (targetUserId == row.kpUserId()) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.MEMBER_STATE,
                    "KP 没有角色卡"));
        }
        MemberRow member = turn.member(row, targetUserId);
        if (member == null || member.leftAt() > 0L) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.MEMBER_STATE,
                    "目标不在本局内"));
        }
        String skill = rawSkill == null ? "" : rawSkill.trim();
        if (skill.isEmpty()) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.BAD_ARGUMENT,
                    "参数不对：缺少技能名"));
        }
        String deltaText = rawDelta == null ? "" : rawDelta.trim();
        if (deltaText.isEmpty()) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.BAD_ARGUMENT,
                    "参数不对：缺少调整值"));
        }
        Integer parsedDelta = SessionPlaySupport.parseInt(deltaText);
        if (parsedDelta == null) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.BAD_ARGUMENT,
                    "参数不对：调整值必须是整数：" + deltaText));
        }
        CardData card = member.card();
        int from = card.skill(skill);
        int to = from + parsedDelta.intValue();
        if (to < SKILL_MIN || to > SKILL_MAX) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.BAD_ARGUMENT,
                    "技能值需在 " + SKILL_MIN + "～" + SKILL_MAX + " 之间：" + to));
        }
        SessionResult cooling = sessions.cooling(ACTION_ADJUST, turn,
                config.defaultCooldownSeconds());
        if (cooling != null) {
            return PlayResult.rejected(cooling);
        }
        Map<String, Integer> skills = new LinkedHashMap<String, Integer>(card.skills());
        skills.put(skill, Integer.valueOf(to));
        MemberRow updated = member.toBuilder()
                .card(card.withSkills(skills))
                .updateTime(turn.now)
                .build();
        int affected = store.updateMember(updated);
        if (affected < 0) {
            return PlayResult.rejected(SessionResult.storageFailure("技能调整未生效，请稍后重试"));
        }
        if (affected == 0) {
            return PlayResult.rejected(SessionResult.storageFailure("技能调整未生效，请稍后重试"));
        }
        sessions.appendLog(row.id(), EVENT_ADJUST, turn.actor.userId(),
                skill + " " + from + "→" + to, turn.now);
        sessions.markCooldown(ACTION_ADJUST, turn);
        SessionResult ok = SessionResult.ok(turn.reload(row), "已将 " + skill + " 调整为 " + to);
        return PlayResult.of(ok, new SessionOutcomes.Adjust(ok, targetUserId, skill, from, to));
    }

    /** 进行中邀请入局（§6.1 invite）：离场成员重进时清离场与代管标记，角色卡保留。 */
    SessionResult invite(SessionActor actor, final long targetUserId, final CardData card) {
        final SessionActor request = SessionService.requireActor(actor);
        if (!request.inGroup()) {
            return SessionResult.rejected(SessionReject.NOT_IN_GROUP, "请在群内使用");
        }
        return sessions.withGroupLock(actor, ACTION_INVITE, new SessionService.LockedAction() {

            @Override
            public SessionResult run(SessionService.Turn turn) {
                return inviteInLock(turn, targetUserId, card);
            }
        });
    }

    private SessionResult inviteInLock(SessionService.Turn turn, long targetUserId, CardData card) {
        SessionRow row = turn.active();
        if (row == null) {
            return sessions.reject(turn, SessionReject.STATE_UNAVAILABLE, "本群暂无局");
        }
        if (row.kpUserId() != turn.actor.userId()) {
            return sessions.reject(turn, SessionReject.NO_PERMISSION, "只有 KP 能邀请入局");
        }
        if (row.state() != SessionState.RUNNING) {
            return sessions.reject(turn, SessionReject.STATE_UNAVAILABLE, "只有进行中的局需要邀请");
        }
        if (targetUserId <= 0L) {
            return sessions.reject(turn, SessionReject.BAD_ARGUMENT, "参数不对：缺少目标玩家");
        }
        if (targetUserId == row.kpUserId()) {
            return sessions.reject(turn, SessionReject.MEMBER_STATE, "对方已是本局 KP");
        }
        MemberRow existing = turn.member(row, targetUserId);
        if (existing != null && existing.leftAt() <= 0L) {
            return sessions.reject(turn, SessionReject.MEMBER_STATE, "对方已在局内");
        }
        Script script = sessions.loadScript(row.scriptId());
        if (script != null && sessions.insideCount(row, targetUserId) >= script.maxPlayers()) {
            return sessions.reject(turn, SessionReject.MEMBER_STATE,
                    "本局人数已满（" + script.maxPlayers() + " 人）");
        }
        SessionResult cooling = sessions.cooling(ACTION_INVITE, turn,
                config.defaultCooldownSeconds());
        if (cooling != null) {
            return cooling;
        }
        if (existing != null) {
            MemberRow updated = existing.toBuilder()
                    .leftAt(0L)
                    .takenOver(false)
                    .updateTime(turn.now)
                    .build();
            int affected = store.updateMember(updated);
            if (affected <= 0) {
                return SessionResult.storageFailure("邀请未生效，请稍后重试");
            }
        } else {
            MemberRow member = MemberRow.builder()
                    .sessionId(row.id())
                    .userId(targetUserId)
                    .card(card == null ? CardData.empty() : card)
                    .successSkills(new ArrayList<String>())
                    .joinedAt(turn.now)
                    .createTime(turn.now)
                    .updateTime(turn.now)
                    .build();
            if (store.insertMember(member) == 0L) {
                return SessionResult.storageFailure("邀请未生效，请稍后重试");
            }
        }
        sessions.appendLog(row.id(), EVENT_INVITE, turn.actor.userId(),
                String.valueOf(targetUserId), turn.now);
        sessions.markCooldown(ACTION_INVITE, turn);
        return SessionResult.ok(turn.reload(row), "已邀请对方入局");
    }

    /** KP 代管（§6.1 take）：标记由 KP 代掷，目标重新加入时自动清除。 */
    SessionResult takeover(SessionActor actor, final long targetUserId) {
        final SessionActor request = SessionService.requireActor(actor);
        if (!request.inGroup()) {
            return SessionResult.rejected(SessionReject.NOT_IN_GROUP, "请在群内使用");
        }
        return sessions.withGroupLock(actor, ACTION_TAKE, new SessionService.LockedAction() {

            @Override
            public SessionResult run(SessionService.Turn turn) {
                return takeoverInLock(turn, targetUserId);
            }
        });
    }

    private SessionResult takeoverInLock(SessionService.Turn turn, long targetUserId) {
        SessionRow row = turn.active();
        if (row == null) {
            return sessions.reject(turn, SessionReject.STATE_UNAVAILABLE, "本群暂无局");
        }
        if (row.kpUserId() != turn.actor.userId()) {
            return sessions.reject(turn, SessionReject.NO_PERMISSION, "只有 KP 能接管");
        }
        if (row.state() != SessionState.RUNNING) {
            return sessions.reject(turn, SessionReject.STATE_UNAVAILABLE, "本局尚未开局");
        }
        if (targetUserId <= 0L) {
            return sessions.reject(turn, SessionReject.BAD_ARGUMENT, "参数不对：缺少目标玩家");
        }
        if (targetUserId == row.kpUserId()) {
            return sessions.reject(turn, SessionReject.MEMBER_STATE, "KP 无需接管自己");
        }
        MemberRow member = turn.member(row, targetUserId);
        if (member == null || member.leftAt() > 0L) {
            return sessions.reject(turn, SessionReject.MEMBER_STATE, "目标不在本局内");
        }
        if (member.takenOver()) {
            return SessionResult.ok(turn.snapshot(row), "对方已在代管中");
        }
        SessionResult cooling = sessions.cooling(ACTION_TAKE, turn, config.defaultCooldownSeconds());
        if (cooling != null) {
            return cooling;
        }
        MemberRow updated = member.toBuilder().takenOver(true).updateTime(turn.now).build();
        int affected = store.updateMember(updated);
        if (affected <= 0) {
            return SessionResult.storageFailure("接管未生效，请稍后重试");
        }
        sessions.appendLog(row.id(), EVENT_TAKE, turn.actor.userId(),
                String.valueOf(targetUserId), turn.now);
        sessions.markCooldown(ACTION_TAKE, turn);
        return SessionResult.ok(turn.reload(row), "已接管该成员的回合");
    }

    /** 跳过（§6.1 skip）：只写日志与状态标记，不改角色卡。 */
    SessionResult skip(SessionActor actor, final long targetUserId, final String rawReason) {
        final SessionActor request = SessionService.requireActor(actor);
        if (!request.inGroup()) {
            return SessionResult.rejected(SessionReject.NOT_IN_GROUP, "请在群内使用");
        }
        return sessions.withGroupLock(actor, ACTION_SKIP, new SessionService.LockedAction() {

            @Override
            public SessionResult run(SessionService.Turn turn) {
                return skipInLock(turn, targetUserId, rawReason);
            }
        });
    }

    private SessionResult skipInLock(SessionService.Turn turn, long targetUserId, String rawReason) {
        SessionRow row = turn.active();
        if (row == null) {
            return sessions.reject(turn, SessionReject.STATE_UNAVAILABLE, "本群暂无局");
        }
        if (row.kpUserId() != turn.actor.userId()) {
            return sessions.reject(turn, SessionReject.NO_PERMISSION, "只有 KP 能跳过");
        }
        if (row.state() != SessionState.RUNNING) {
            return sessions.reject(turn, SessionReject.STATE_UNAVAILABLE, "本局尚未开局");
        }
        if (targetUserId <= 0L) {
            return sessions.reject(turn, SessionReject.BAD_ARGUMENT, "参数不对：缺少目标玩家");
        }
        MemberRow member = turn.member(row, targetUserId);
        if (member == null || member.leftAt() > 0L) {
            return sessions.reject(turn, SessionReject.MEMBER_STATE, "目标不在本局内");
        }
        SessionResult cooling = sessions.cooling(ACTION_SKIP, turn, config.defaultCooldownSeconds());
        if (cooling != null) {
            return cooling;
        }
        String reason = rawReason == null ? "" : rawReason.trim();
        // 日志带上被跳过的人，/trpg log 才能定位到具体成员。
        String content = reason.isEmpty() ? String.valueOf(targetUserId)
                : targetUserId + " " + reason;
        sessions.appendLog(row.id(), EVENT_SKIP, turn.actor.userId(), content, turn.now);
        sessions.markCooldown(ACTION_SKIP, turn);
        return SessionResult.ok(turn.snapshot(row), "已记录跳过");
    }

    /** 换本局剧本（§6.1 script）：仅 KP、仅招募期。 */
    SessionResult changeScript(SessionActor actor, final String rawScriptId) {
        final SessionActor request = SessionService.requireActor(actor);
        if (!request.inGroup()) {
            return SessionResult.rejected(SessionReject.NOT_IN_GROUP, "请在群内使用");
        }
        return sessions.withGroupLock(actor, ACTION_SCRIPT, new SessionService.LockedAction() {

            @Override
            public SessionResult run(SessionService.Turn turn) {
                return changeScriptInLock(turn, rawScriptId);
            }
        });
    }

    private SessionResult changeScriptInLock(SessionService.Turn turn, String rawScriptId) {
        SessionRow row = turn.active();
        if (row == null) {
            return sessions.reject(turn, SessionReject.STATE_UNAVAILABLE, "本群暂无局");
        }
        if (row.kpUserId() != turn.actor.userId()) {
            return sessions.reject(turn, SessionReject.NO_PERMISSION, "只有 KP 能更换剧本");
        }
        if (row.state() != SessionState.RECRUITING) {
            return sessions.reject(turn, SessionReject.STATE_UNAVAILABLE, "开局后不能更换剧本");
        }
        String scriptId = rawScriptId == null ? "" : rawScriptId.trim();
        if (scriptId.isEmpty()) {
            return sessions.reject(turn, SessionReject.BAD_ARGUMENT, "参数不对：缺少剧本 ID");
        }
        Script script = sessions.loadScript(scriptId);
        if (script == null) {
            return sessions.reject(turn, SessionReject.BAD_ARGUMENT, "剧本不存在：" + scriptId);
        }
        SessionResult cooling = sessions.cooling(ACTION_SCRIPT, turn,
                config.defaultCooldownSeconds());
        if (cooling != null) {
            return cooling;
        }
        int affected = store.casScript(row.id(), SessionState.RECRUITING, scriptId, turn.now);
        if (affected < 0) {
            return SessionResult.storageFailure("剧本切换未生效，请稍后重试");
        }
        if (affected == 0) {
            return sessions.reject(turn, SessionReject.STATE_UNAVAILABLE, "本局状态已变化，请重新查看局面");
        }
        sessions.markCooldown(ACTION_SCRIPT, turn);
        return SessionResult.ok(turn.reload(row), "本局剧本已切换为《" + script.name() + "》");
    }

    /** 按进入的场景与本次解锁的线索聚合触发器提示（§4.4 trigger）。 */
    private static String sceneHint(Script script, String sceneId, List<String> newClueKeys) {
        if (script == null) {
            return "";
        }
        LinkedHashSet<String> hints = new LinkedHashSet<String>();
        for (ScriptTrigger trigger : script.triggers()) {
            if (trigger == null) {
                continue;
            }
            String when = trigger.when();
            if (ScriptTrigger.WHEN_SCENE_ENTER.equals(when)
                    && sceneId != null && sceneId.equals(trigger.sceneId())) {
                SessionPlaySupport.addHint(hints, trigger.hint());
            } else if (ScriptTrigger.WHEN_CLUE_UNLOCKED.equals(when)
                    && newClueKeys != null && newClueKeys.contains(trigger.clueId())) {
                SessionPlaySupport.addHint(hints, trigger.hint());
            }
        }
        return SessionPlaySupport.joinHints(hints);
    }

}
