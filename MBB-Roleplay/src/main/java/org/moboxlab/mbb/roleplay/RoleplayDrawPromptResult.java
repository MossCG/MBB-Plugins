package org.moboxlab.mbb.roleplay;

import java.util.ArrayList;
import java.util.List;

/**
 * 生图 prompt 编译结果
 */
public class RoleplayDrawPromptResult {
    public boolean status;
    public String message = "";
    public String prompt = "";
    public String negativePrompt = "";
    public String size = "";
    public String camera = "";
    public String validation = "";
    public String expectedTags = "";
    public String forbiddenTags = "";
    public List<String> targetNames = new ArrayList<>();
    public List<String> targetAliases = new ArrayList<>();

    public static RoleplayDrawPromptResult ok(String prompt,String negativePrompt,
                                              String size,String camera,String validation) {
        RoleplayDrawPromptResult result = new RoleplayDrawPromptResult();
        result.status = true;
        result.prompt = prompt == null ? "" : prompt;
        result.negativePrompt = negativePrompt == null ? "" : negativePrompt;
        result.size = size == null ? "" : size;
        result.camera = camera == null ? "" : camera;
        result.validation = validation == null ? "" : validation;
        return result;
    }

    public static RoleplayDrawPromptResult failed(String message) {
        RoleplayDrawPromptResult result = new RoleplayDrawPromptResult();
        result.status = false;
        result.message = message == null ? "" : message;
        return result;
    }
}
