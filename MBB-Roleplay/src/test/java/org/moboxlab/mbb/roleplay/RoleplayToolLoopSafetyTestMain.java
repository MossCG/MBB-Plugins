package org.moboxlab.mbb.roleplay;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 工具 schema 装载与失败回合消息等待时间回归测试
 */
public class RoleplayToolLoopSafetyTestMain {
    public static void main(String[] args) {
        Set<String> exposed = new LinkedHashSet<>(Arrays.asList(
                "tool_search","end_turn","message_send","sticker"));
        List<String> schemas = new ArrayList<>(exposed);
        if (!RoleplayToolLoopService.addLoadedTool(exposed,schemas,"draw",8)) {
            throw new AssertionError("tool_search 命中的工具应加入下一步 schema");
        }
        if (!schemas.contains("draw")) {
            throw new AssertionError("draw 没有进入模型下一步可见的 schema");
        }
        if (RoleplayToolLoopService.addLoadedTool(exposed,schemas,"draw",8)) {
            throw new AssertionError("重复装载同一工具不应改变 schema 集合");
        }

        Set<String> full = new LinkedHashSet<>(Arrays.asList("a","b"));
        List<String> fullSchemas = new ArrayList<>(full);
        if (RoleplayToolLoopService.addLoadedTool(full,fullSchemas,"c",2)) {
            throw new AssertionError("超过 toolLoadedMaxTools 时不应继续装载");
        }

        if (RoleplayBatchScheduler.waitStartedAt(5000L,1000L) != 5000L) {
            throw new AssertionError("消息过期时间应从真正进入合批队列时开始计算");
        }
        if (RoleplayBatchScheduler.waitStartedAt(null,1000L) != 1000L) {
            throw new AssertionError("没有入队时间时应回退到消息时间");
        }

        String protocolText = "<｜DSML｜ calls>\n<｜DSML｜ invoke name=\"draw\">\n"
                +"<｜DSML｜ parameter name=\"brief\" string=\"false\">{\"subject\":\"当前角色\"}"
                +"</｜DSML｜ parameter>\n</｜DSML｜ invoke>\n</｜DSML｜ calls>";
        RoleplayReplyDraft polluted = RoleplayReplyDraft.parse(protocolText);
        if (!polluted.malformed) {
            throw new AssertionError("工具调用协议正文必须按结构化结果污染处理");
        }
        String cleaned = RoleplayReplySanitizer.clean(null,"角色","先说一句\n"+protocolText);
        if (!"先说一句".equals(cleaned)) {
            throw new AssertionError("发送清洗应保留协议前的正常文字并丢弃协议块");
        }
        String reject = RoleplayReplyTextValidator.rejectReason("先说一句",
                "先说一句\n"+protocolText);
        if (!reject.contains("工具调用协议")) {
            throw new AssertionError("复核改写包含工具协议时必须拒绝");
        }
    }
}
