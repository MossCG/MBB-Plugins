package org.moboxlab.mbb.trpg.command;

import org.moboxlab.mbb.trpg.session.SessionActor;

/**
 * 单个 /trpg 子指令的处理器。
 *
 * <p>新切片只需新增实现类并在 {@link TrpgCommandService} 注册，不改分发结构；
 * 处理器只解析参数、调用服务与拼回执。</p>
 */
public interface TrpgSubcommandHandler {

    /**
     * 处理一条已通过「群内」与「已启用」前置判断的指令。
     *
     * @param context 指令上下文（服务与工具）
     * @param actor   请求者
     * @param params  归一化后的参数（含子指令本身；pc 类已补成 {@code pc reroll ...} 形态）
     */
    void handle(TrpgCommandContext context, SessionActor actor, String[] params);
}
