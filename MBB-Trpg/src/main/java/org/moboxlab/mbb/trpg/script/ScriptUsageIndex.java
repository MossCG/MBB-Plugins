package org.moboxlab.mbb.trpg.script;

/**
 * 未归档局引用索引：删除与替换剧本前用来判断该剧本是否还被某一局引用（§8.6）。
 *
 * <p>由 {@code SessionService} 实现（它持有局面状态），剧本库只依赖这个窄端口，
 * 不反向依赖 session 包。装配顺序是「先建剧本库、后建局面服务」，因此引用索引允许
 * 建库之后再绑定：绑定之前删除与替换按「无引用」处理，绑定之后才做检查。</p>
 */
public interface ScriptUsageIndex {

    /**
     * 该剧本是否被未归档局（含重启恢复的局）引用。
     *
     * @param scriptId 剧本 id
     * @return 被引用返回 true
     */
    boolean isInUse(String scriptId);
}
