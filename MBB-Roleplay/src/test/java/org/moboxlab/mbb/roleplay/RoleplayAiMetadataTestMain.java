package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONObject;

/**
 * AI 调用点 Prompt 指纹回归测试
 */
public class RoleplayAiMetadataTestMain {
    public static void main(String[] args) {
        String firstVersion = RoleplayAiMetadata.templateVersion(RoleplayAiMetadataTestMain.class);
        String secondVersion = RoleplayAiMetadata.templateVersion(RoleplayAiMetadataTestMain.class);
        if (firstVersion.isEmpty() || firstVersion.length() != 16
                || !firstVersion.equals(secondVersion)) {
            throw new AssertionError("同一提示词构建类必须返回稳定且非空的版本指纹");
        }
        if (firstVersion.equals(RoleplayAiMetadata.templateVersion(SecondPromptOwner.class))) {
            throw new AssertionError("不同提示词构建类应产生不同版本指纹");
        }

        JSONObject params = new JSONObject(true);
        RoleplayAiMetadata.tagTemplate(params,"roleplay.test",RoleplayAiMetadataTestMain.class);
        if (!"roleplay.test".equals(params.getString("taskName"))
                || !firstVersion.equals(params.getString("promptVersion"))) {
            throw new AssertionError("调用点统计标记未正确写入任务名或版本指纹");
        }
    }

    private static class SecondPromptOwner {
    }
}
