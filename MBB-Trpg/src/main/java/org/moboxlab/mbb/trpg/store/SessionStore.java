package org.moboxlab.mbb.trpg.store;

import org.moboxlab.mbb.trpg.session.SessionState;

import java.util.List;

/**
 * 局面持久化端口，适配主程序 StorageService。
 *
 * <p>约定：每个方法只发单语句，不做业务校验、不发消息、不加锁。返回值沿用主程序
 * 落库语义：{@code -1} 表示语句未执行（状态未知），{@code 0} 表示无匹配行，
 * 正数表示影响行数；查询类方法返回已读到的部分行，不抛异常。</p>
 *
 * <p>业务编排（状态迁移、成长结算、归档顺序）一律在 SessionService 中完成，
 * 建表 / 补列 / 清理直连 SqlSessionStore，不经过本端口。</p>
 */
public interface SessionStore {

    /** 查本群未归档局（RECRUITING / RUNNING），无则 null。 */
    SessionRow findActiveByGroup(long groupId);

    /** 按主键查局。 */
    SessionRow findSession(long sessionId);

    /**
     * 查本群最近一局已归档局（state = ENDED 且 endedAt >= endedAfter），无则 null。
     *
     * <p>供只读日志回看定位「最近一局」：活跃局由调用方先查，这里只做归档兜底；
     * 窗口由调用方按保留天数换算后传入，本方法不解释时间语义。</p>
     */
    SessionRow findLatestArchivedByGroup(long groupId, long endedAfter);

    /** 新建局，返回自增主键；0 表示未插入或取键失败，调用方回读确认。 */
    long insertSession(SessionRow row);

    /**
     * 单语句 CAS 迁移：仅当库中 state 等于期望值时更新状态与结束字段。
     *
     * @return 1 成功；0 前置不匹配；-1 语句未执行
     */
    int casTransition(long sessionId, SessionState expected, SessionState target,
                      long startedAt, long endedAt, String endReason, boolean growthSettled,
                      String summary, long updateTime);

    /** CAS 更新当前场景（期望状态为进行中）。 */
    int casScene(long sessionId, SessionState expected, int sceneIndex, String sceneTitle,
                 long updateTime);

    /** CAS 更换本局剧本（期望状态由调用方给出，招募期换本局用）。 */
    int casScript(long sessionId, SessionState expected, String scriptId, long updateTime);

    /** CAS 更新 AI 子状态。 */
    int casAiState(long sessionId, SessionState expected, boolean aiEnabled,
                   long aiSuspendedUntil, int aiFailStreak, long updateTime);

    /** 刷新最后活动时间（调用方负责节流）。 */
    int touchActivity(long sessionId, long lastActivityAt, long updateTime);

    /** 加载全部未归档局。 */
    List<SessionRow> loadActiveSessions();

    /** 加载一局全部成员行。 */
    List<MemberRow> loadMembers(long sessionId);

    /** 新建成员行，返回自增主键；0 表示失败。 */
    long insertMember(MemberRow row);

    /** 更新成员行（按主键）。 */
    int updateMember(MemberRow row);

    /** 追加本局成功技能（读改写由调用方在群锁内完成）。 */
    int appendSuccessSkills(long sessionId, long userId, List<String> skills, long updateTime);

    /** 列出已结束但未完成成长结算的局。 */
    List<SessionRow> listUnsettledSessions();

    /** 写结束摘要（不改变状态）。 */
    int settleSummary(long sessionId, String summary, long updateTime);

    /** 追加一条文本日志；seq 由调用方在本局锁内取号。 */
    int appendLog(LogRow row);

    /** 本局当前最大日志序号，无日志返回 0。 */
    long maxLogSeq(long sessionId);

    /** 分页读取日志（seq 倒序）。 */
    List<LogRow> listLogs(long sessionId, int limit, int offset);

    /** 本局已解锁线索键（去重）。 */
    List<String> listClueKeys(long sessionId);

    /** 加黑名单。 */
    int insertBlacklist(BlacklistRow row);

    /** 移出黑名单。 */
    int deleteBlacklist(long groupId, long userId);

    /** 本群黑名单。 */
    List<BlacklistRow> listBlacklist(long groupId);

    /** 是否在黑名单。 */
    boolean isBlacklisted(long groupId, long userId);

    /**
     * 列出结束时间早于给定时点、可清理的局主键。
     *
     * <p>只选 {@code state='ENDED' AND growthSettled=1 AND endedAt < endedBefore}，按 id 升序；
     * 不做 LIMIT（规格 §7.7 冻结 SQL）。</p>
     */
    List<Long> listExpiredSessionIds(long endedBefore);

    /** 删除一局日志。 */
    int deleteLogs(long sessionId);

    /** 删除一局成员。 */
    int deleteMembers(long sessionId);

    /** 删除一局（仅 ENDED 行生效）。 */
    int deleteSession(long sessionId);

    /** 追加 AI 审计行，返回自增主键；0 表示失败。 */
    long appendAiTrace(AiTraceRow row);

    /**
     * 读最近审计行（按 id 倒序）。
     *
     * <p>{@code sessionId <= 0} 时只按群过滤；{@code limit <= 0} 时不查库、返回空表。</p>
     */
    List<AiTraceRow> listAiTrace(long groupId, long sessionId, int limit);

    /** 删除创建时间早于给定时点的审计行。 */
    int deleteAiTraceBefore(long createTime);

    /** 只保留最新 keepRows 行审计。 */
    int deleteAiTraceOverflow(int keepRows);
}
