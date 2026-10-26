package org.moboxlab.mbb.trpg.script;

/**
 * 剧本相关的错误码与告警码常量。
 *
 * <p>这里集中放剧本链路的码，避免剧本校验器成为导入、删除、生成等服务的常量宿主：
 * 校验器只产出语法与语义码，其余码（来源冲突、引用占用、生成失败）由对应服务使用。
 * 码与中文文案的对应关系见 docs/scripts/README.md 的「错误码」一节。</p>
 */
public final class ScriptErrorCodes {

    /** 内容超过上限（字节）。 */
    public static final String E_SIZE = "E_SIZE";
    /** JSON 语法错误。 */
    public static final String E_PARSE = "E_PARSE";
    /** 顶层不是 JSON 对象。 */
    public static final String E_ROOT = "E_ROOT";
    /** schemaVersion 不是 1。 */
    public static final String E_VERSION = "E_VERSION";
    /** ruleSet 不是 d100。 */
    public static final String E_RULESET = "E_RULESET";
    /** id 不符合正则，或脚本 id 以 gen- 开头。 */
    public static final String E_ID = "E_ID";
    /** 脚本 id 命中保留字。 */
    public static final String E_RESERVED = "E_RESERVED";
    /** 节点 id 或场景标题重复。 */
    public static final String E_DUP = "E_DUP";
    /** 字段缺失、类型错误、出现 null、互斥字段同时出现。 */
    public static final String E_FIELD = "E_FIELD";
    /** 数值越界、数组数量越界、文本长度越界或去空白后为空。 */
    public static final String E_RANGE = "E_RANGE";
    /** 引用的节点 id 不存在，或引用类型不匹配。 */
    public static final String E_REF = "E_REF";
    /** 读写数据目录失败。 */
    public static final String E_IO = "E_IO";
    /** 与内置剧本 id 冲突，或试图删除/替换内置剧本。 */
    public static final String E_BUILTIN = "E_BUILTIN";
    /** 新建导入时目标 id 已存在。 */
    public static final String E_EXISTS = "E_EXISTS";
    /** 剧本被未归档局引用，禁止删除或替换。 */
    public static final String E_IN_USE = "E_IN_USE";
    /** 找不到剧本。 */
    public static final String E_NOT_FOUND = "E_NOT_FOUND";
    /** 没有待导入会话、已有待导入会话、已超时或已超限。 */
    public static final String E_IMPORT_STATE = "E_IMPORT_STATE";

    /** AI 生成：MBB-AI 未安装或被禁用。 */
    public static final String E_AI_DISABLED = "E_AI_DISABLED";
    /** AI 生成：已有任务在跑，或 MBB-AI 并发已满。 */
    public static final String E_AI_BUSY = "E_AI_BUSY";
    /** AI 生成：超时或被中断。 */
    public static final String E_AI_TIMEOUT = "E_AI_TIMEOUT";
    /** AI 生成：返回内容不是可解析的 JSON 对象。 */
    public static final String E_AI_OUTPUT = "E_AI_OUTPUT";
    /** AI 生成：JSON 可解析但没通过剧本校验。 */
    public static final String E_AI_VALIDATION = "E_AI_VALIDATION";
    /** AI 生成：其他失败。 */
    public static final String E_AI_FAILED = "E_AI_FAILED";

    /** 未知字段告警：字段被忽略，不影响校验通过。 */
    public static final String W_UNKNOWN_FIELD = "W_UNKNOWN_FIELD";

    private ScriptErrorCodes() {
    }
}
