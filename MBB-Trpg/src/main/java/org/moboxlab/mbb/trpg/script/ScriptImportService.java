package org.moboxlab.mbb.trpg.script;

/**
 * 分段导入端口：开始 → 分段粘贴 → 结束 / 取消。
 *
 * <p>命令参数会被主程序按空白切分，无法把 JSON 当单条参数粘贴，因此导入拆成多段暂存。
 * 暂存期间，发起人在同一会话（群号 + 用户）发送的任何消息都被当作 JSON 片段；
 * 群号 0 表示私聊。暂存与会话状态由实现方持有，本接口只约定状态机边界。</p>
 *
 * <p>实现归 S10；本切片只冻结接口，避免命令层与导入实现互相牵制。</p>
 */
public interface ScriptImportService {

    /**
     * 开始一次导入，建立暂存。
     *
     * @param groupId    群号，0 表示私聊
     * @param operatorId 发起人 QQ 号
     * @param mode       新建或替换
     * @throws ScriptException 已有待导入会话、暂存已超限或超时时抛出 E_IMPORT_STATE
     */
    void begin(long groupId, long operatorId, ScriptImportMode mode) throws ScriptException;

    /**
     * 追加一段 JSON 片段。
     *
     * @param groupId    群号，0 表示私聊
     * @param operatorId 发起人 QQ 号
     * @param fragment   消息原文
     * @throws ScriptException 没有待导入会话或超出字节上限时抛出
     */
    void append(long groupId, long operatorId, String fragment) throws ScriptException;

    /**
     * 结束分段粘贴：解析、校验、落盘。
     *
     * <p>解析失败（多半是还没传完）时保留暂存并抛出 E_PARSE；语义校验失败时结束暂存、
     * 保存原文到 imports/rejected/，并抛出 {@link ScriptValidationException}：
     * 它携带完整的校验结果，回执展示 {@link ScriptValidationResult#reportedErrors()}，
     * 再用 {@link ScriptValidationResult#remainingErrorCount()} 追加「另有 N 条」。</p>
     *
     * @param groupId    群号，0 表示私聊
     * @param operatorId 发起人 QQ 号
     * @return 落盘结果
     * @throws ScriptValidationException 语义校验未通过时抛出
     * @throws ScriptException           解析、状态或写入被拒绝时抛出
     */
    ScriptImportResult end(long groupId, long operatorId) throws ScriptException;

    /**
     * 丢弃暂存并回复已取消。
     *
     * @param groupId    群号，0 表示私聊
     * @param operatorId 发起人 QQ 号
     * @throws ScriptException 没有待导入会话时抛出 E_IMPORT_STATE
     */
    void cancel(long groupId, long operatorId) throws ScriptException;

    /**
     * 指定会话里是否有待导入暂存。
     *
     * @param groupId    群号，0 表示私聊
     * @param operatorId 发起人 QQ 号
     * @return 有暂存返回 true
     */
    boolean hasPending(long groupId, long operatorId);

    /**
     * 暂存已累计的字节数，用于回执进度。
     *
     * @param groupId    群号，0 表示私聊
     * @param operatorId 发起人 QQ 号
     * @return 已累计字节数；没有暂存时返回 0
     */
    long pendingBytes(long groupId, long operatorId);

    /**
     * 单份剧本内容的字节上限，用于回执提示。
     *
     * @return 上限字节数
     */
    long maxBytes();
}
