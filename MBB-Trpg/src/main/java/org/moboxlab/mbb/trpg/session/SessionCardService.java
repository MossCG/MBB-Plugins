package org.moboxlab.mbb.trpg.session;

import java.util.Locale;

import org.moboxlab.mbb.trpg.config.TrpgConfig;
import org.moboxlab.mbb.trpg.store.CardData;
import org.moboxlab.mbb.trpg.store.MemberRow;
import org.moboxlab.mbb.trpg.store.SessionRow;
import org.moboxlab.mbb.trpg.store.SessionStore;

/**
 * 角色卡指令（§6.1 / §6.2）：pc 查看、pc reroll 重掷、pc set 微调。
 *
 * <p>从 {@link SessionService} 拆出，只负责卡面读写与属性校验；群锁、冷却与拒绝去重
 * 仍走主服务的同一个入口，判定顺序与其余局面指令保持一致。</p>
 */
final class SessionCardService {

    private static final String ACTION_PC = "pc";
    private static final String ACTION_PC_REROLL = "pc-reroll";
    private static final String ACTION_PC_SET = "pc-set";
    private static final int ATTRIBUTE_MIN = 15;
    private static final int ATTRIBUTE_MAX = 90;

    private final SessionService sessions;
    private final SessionStore store;
    private final CooldownRegistry cooldowns;
    private final TrpgConfig config;

    SessionCardService(SessionService sessions, SessionStore store, CooldownRegistry cooldowns,
                       TrpgConfig config) {
        if (sessions == null) {
            throw new IllegalArgumentException("局面服务不能为空");
        }
        if (store == null) {
            throw new IllegalArgumentException("存储端口不能为空");
        }
        if (cooldowns == null) {
            throw new IllegalArgumentException("冷却登记不能为空");
        }
        if (config == null) {
            throw new IllegalArgumentException("配置不能为空");
        }
        this.sessions = sessions;
        this.store = store;
        this.cooldowns = cooldowns;
        this.config = config;
    }

    /**
     * 前置放行判定：局面存在、请求者在局内、不是 KP。
     *
     * <p>指令层在回报「参数不对」之前调用，避免把参数错误排在状态与权限之前（§5.5）；
     * 放行时不动冷却，真正的动作会自己再判。</p>
     */
    SessionResult gate(final SessionActor actor, final boolean rejectKp) {
        return sessions.withGroupLock(actor, ACTION_PC, new SessionService.LockedAction() {

            @Override
            public SessionResult run(SessionService.Turn turn) {
                SessionRow row = turn.active();
                if (row == null) {
                    return sessions.reject(turn, SessionReject.STATE_UNAVAILABLE, "本群暂无局");
                }
                MemberRow member = turn.member(row, actor.userId());
                if (member == null) {
                    return sessions.reject(turn, SessionReject.MEMBER_STATE, "你不在本局");
                }
                if (rejectKp && actor.userId() == row.kpUserId()) {
                    return sessions.reject(turn, SessionReject.MEMBER_STATE, "KP 无需角色卡");
                }
                return SessionResult.ok(turn.snapshot(row), "");
            }
        });
    }

    /** 查看角色卡（§6.2）：本局成员可看自己（含离场），KP 可看任意成员。 */
    SessionResult viewCard(final SessionActor actor, final long targetUserId) {
        if (!SessionService.requireActor(actor).inGroup()) {
            return SessionResult.rejected(SessionReject.NOT_IN_GROUP, "请在群内使用");
        }
        return sessions.withGroupLock(actor, ACTION_PC, new SessionService.LockedAction() {

            @Override
            public SessionResult run(SessionService.Turn turn) {
                SessionRow row = turn.active();
                if (row == null) {
                    return sessions.reject(turn, SessionReject.STATE_UNAVAILABLE, "本群暂无局");
                }
                // 判定顺序按 §5.5：权限先于状态与参数，其次才是成员是否存在。
                if (actor.userId() != targetUserId && actor.userId() != row.kpUserId()) {
                    return sessions.reject(turn, SessionReject.NO_PERMISSION,
                            "只有本人或 KP 能查看该角色卡");
                }
                MemberRow target = turn.member(row, targetUserId);
                if (target == null) {
                    return sessions.reject(turn, SessionReject.MEMBER_STATE, "该成员不在本局");
                }
                SessionResult cooling = sessions.cooling(ACTION_PC, turn,
                        config.defaultCooldownSeconds());
                if (cooling != null) {
                    return cooling;
                }
                cooldowns.mark(ACTION_PC, actor.userId(), turn.groupId, turn.now);
                return SessionResult.ok(turn.snapshot(row), "");
            }
        });
    }

    /**
     * 重掷角色卡（§6.1 pc reroll）：仅招募期、玩家本人；新卡由指令层生成后传入。
     *
     * <p>按 §7.5，重掷把 SAN 重置为 POW 属于生成侧职责，本方法只整卡替换并落库。</p>
     */
    SessionResult rerollCard(final SessionActor actor, final CardData card) {
        if (!SessionService.requireActor(actor).inGroup()) {
            return SessionResult.rejected(SessionReject.NOT_IN_GROUP, "请在群内使用");
        }
        return sessions.withGroupLock(actor, ACTION_PC_REROLL, new SessionService.LockedAction() {

            @Override
            public SessionResult run(SessionService.Turn turn) {
                SessionRow row = turn.active();
                if (row == null) {
                    return sessions.reject(turn, SessionReject.STATE_UNAVAILABLE, "本群暂无局");
                }
                MemberRow member = turn.member(row, actor.userId());
                if (member == null || member.leftAt() > 0L) {
                    return sessions.reject(turn, SessionReject.MEMBER_STATE, "你不在局内");
                }
                if (actor.userId() == row.kpUserId()) {
                    return sessions.reject(turn, SessionReject.MEMBER_STATE, "KP 无需角色卡");
                }
                if (row.state() != SessionState.RECRUITING) {
                    return sessions.reject(turn, SessionReject.STATE_UNAVAILABLE,
                            "本局已开局，角色卡已锁定");
                }
                if (card == null) {
                    return sessions.reject(turn, SessionReject.BAD_ARGUMENT, "角色卡不能为空");
                }
                SessionResult cooling = sessions.cooling(ACTION_PC_REROLL, turn,
                        config.defaultCooldownSeconds());
                if (cooling != null) {
                    return cooling;
                }
                int affected = store.updateMember(member.toBuilder()
                        .card(card).updateTime(turn.now).build());
                if (affected < 0) {
                    return SessionResult.storageFailure("角色卡写入未生效");
                }
                if (affected == 0) {
                    MemberRow again = turn.member(row, actor.userId());
                    if (again == null || !card.toJson().equals(again.cardJson())) {
                        return SessionResult.storageFailure("角色卡写入未生效");
                    }
                }
                cooldowns.mark(ACTION_PC_REROLL, actor.userId(), turn.groupId, turn.now);
                return SessionResult.ok(turn.reload(row), "已重新生成角色卡");
            }
        });
    }

    /**
     * 微调角色卡（§6.1 pc set）：仅招募期、玩家本人；只接受八属性、每项 15–90 的整数。
     *
     * <p>改 POW 不联动 SAN；SAN / HP / MP 与技能、物品都不在这里改（§7.5）。</p>
     */
    SessionResult setAttribute(final SessionActor actor, final String field, final String rawValue) {
        if (!SessionService.requireActor(actor).inGroup()) {
            return SessionResult.rejected(SessionReject.NOT_IN_GROUP, "请在群内使用");
        }
        return sessions.withGroupLock(actor, ACTION_PC_SET, new SessionService.LockedAction() {

            @Override
            public SessionResult run(SessionService.Turn turn) {
                SessionRow row = turn.active();
                if (row == null) {
                    return sessions.reject(turn, SessionReject.STATE_UNAVAILABLE, "本群暂无局");
                }
                MemberRow member = turn.member(row, actor.userId());
                if (member == null || member.leftAt() > 0L) {
                    return sessions.reject(turn, SessionReject.MEMBER_STATE, "你不在局内");
                }
                if (actor.userId() == row.kpUserId()) {
                    return sessions.reject(turn, SessionReject.MEMBER_STATE, "KP 无需角色卡");
                }
                if (row.state() != SessionState.RECRUITING) {
                    return sessions.reject(turn, SessionReject.STATE_UNAVAILABLE,
                            "本局已开局，角色卡已锁定");
                }
                String name = field == null ? "" : field.trim().toUpperCase(Locale.ROOT);
                if (!CardData.ATTRIBUTES.contains(name)) {
                    return sessions.reject(turn, SessionReject.BAD_ARGUMENT,
                            "微调只支持八属性：STR、CON、SIZ、DEX、APP、INT、POW、EDU");
                }
                int value;
                try {
                    value = Integer.parseInt(rawValue == null ? "" : rawValue.trim());
                } catch (NumberFormatException error) {
                    return sessions.reject(turn, SessionReject.BAD_ARGUMENT,
                            "微调值必须是整数：" + rawValue);
                }
                if (value < ATTRIBUTE_MIN || value > ATTRIBUTE_MAX) {
                    return sessions.reject(turn, SessionReject.BAD_ARGUMENT,
                            "微调值需在 " + ATTRIBUTE_MIN + "～" + ATTRIBUTE_MAX + " 之间：" + value);
                }
                SessionResult cooling = sessions.cooling(ACTION_PC_SET, turn,
                        config.defaultCooldownSeconds());
                if (cooling != null) {
                    return cooling;
                }
                CardData updated = member.card().withAttribute(name, value);
                int affected = store.updateMember(member.toBuilder()
                        .card(updated).updateTime(turn.now).build());
                if (affected < 0) {
                    return SessionResult.storageFailure("角色卡写入未生效");
                }
                if (affected == 0) {
                    MemberRow again = turn.member(row, actor.userId());
                    if (again == null || again.card().attribute(name) != value) {
                        return SessionResult.storageFailure("角色卡写入未生效");
                    }
                }
                cooldowns.mark(ACTION_PC_SET, actor.userId(), turn.groupId, turn.now);
                String detail = "已把 " + name + " 调整为 " + value
                        + ("POW".equals(name) ? "（改 POW 不联动 SAN）" : "");
                return SessionResult.ok(turn.reload(row), detail);
            }
        });
    }
}
