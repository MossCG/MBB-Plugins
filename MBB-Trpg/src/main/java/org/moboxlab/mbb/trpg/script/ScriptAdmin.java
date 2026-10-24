package org.moboxlab.mbb.trpg.script;

import java.util.concurrent.Executor;

/**
 * S10 剧本管理三件套的装配包：分段导入、AI 生成与承载生成任务的执行器。
 *
 * <p>只用于装配层把三个端口一起交给指令层与消息入口，本身不持有状态。
 * 插件未启用或装配失败时允许整包为 null，此时 script 的库管理动作回「暂不可用」。</p>
 */
public final class ScriptAdmin {

    private final ScriptImportService imports;
    private final ScriptGenerationService generation;
    private final Executor executor;

    public ScriptAdmin(ScriptImportService imports, ScriptGenerationService generation,
                       Executor executor) {
        if (imports == null) {
            throw new IllegalArgumentException("导入服务不能为空");
        }
        if (generation == null) {
            throw new IllegalArgumentException("生成服务不能为空");
        }
        if (executor == null) {
            throw new IllegalArgumentException("生成执行器不能为空");
        }
        this.imports = imports;
        this.generation = generation;
        this.executor = executor;
    }

    /** 分段导入服务。 */
    public ScriptImportService imports() {
        return imports;
    }

    /** 剧本生成服务。 */
    public ScriptGenerationService generation() {
        return generation;
    }

    /** 生成任务执行器（单线程 daemon，由装配层负责关停）。 */
    public Executor executor() {
        return executor;
    }
}
