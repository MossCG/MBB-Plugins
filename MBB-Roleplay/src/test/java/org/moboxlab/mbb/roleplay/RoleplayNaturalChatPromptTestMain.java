package org.moboxlab.mbb.roleplay;

/**
 * 自然聊天边界、收尾识别和复核降分规则测试。
 */
public class RoleplayNaturalChatPromptTestMain {
    public static void main(String[] args) {
        String rules = RoleplayPromptAssembler.naturalChatRulesText();
        if (!rules.contains("一个重点") || !rules.contains("不要自行补成具体名词")
                || !rules.contains("不新增任务") || !rules.contains("每句都反问")
                || !rules.contains("跨群记忆只作背景")) {
            throw new AssertionError("自然聊天边界规则不完整");
        }
        if (!RoleplayPromptAssembler.isClosingMessage("晚安昔涟，明天别忘把那箱补上")
                || !RoleplayPromptAssembler.isClosingMessage("拜拜，明天见")
                || RoleplayPromptAssembler.isClosingMessage("今天晚饭吃什么")) {
            throw new AssertionError("聊天收尾识别不正确");
        }
        String review = RoleplayReplyReviewService.reviewNaturalnessRulesText();
        if (!review.contains("多个旧名词") || !review.contains("任务确认")
                || !review.contains("closingMode=true") || !review.contains("不要补新的俏皮话")) {
            throw new AssertionError("复核层 AI 味降分规则不完整");
        }
    }
}
