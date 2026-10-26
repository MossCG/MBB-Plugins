package org.moboxlab.mbb.roleplay;

/**
 * 通用谐音梗提示词回归测试。
 */
public class RoleplayHumorPromptTestMain {
    public static void main(String[] args) {
        String prompt = RoleplayPromptAssembler.humorRulesText();
        if (!prompt.contains("略优先尝试") || !prompt.contains("谐音")
                || !prompt.contains("低频") || !prompt.contains("不解释谐音")
                || !prompt.contains("严肃话题") || !prompt.contains("先判断")
                || !prompt.contains("不要照着字面") || !prompt.contains("短问")) {
            throw new AssertionError("谐音梗提示词缺少权重、频率、解释或场景边界");
        }
        if (prompt.contains("哈基米") || prompt.contains("米哈游")) {
            throw new AssertionError("通用提示词不应写死具体网络梗");
        }
    }
}
