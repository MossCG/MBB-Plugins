package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.PluginService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 生图 prompt 编译层
 *
 * 角色层只提供画面 brief；本服务解析 brief 里的目标角色，注入对应角色的 canonical tag、
 * 外貌资料和禁止标签，再输出最终给 ComfyUI 的正向/负向 prompt。
 */
public class RoleplayDrawPromptService {
    private final RoleplayService service;

    public RoleplayDrawPromptService(RoleplayService service) {
        this.service = service;
    }

    public RoleplayDrawPromptResult compile(long groupID,long userID,
                                             RoleplayDrawBrief brief,
                                             String requestedSize,
                                             String requestedCamera) {
        if (brief == null || brief.isEmpty()) {
            return RoleplayDrawPromptResult.failed("生图 brief 为空，已取消任务");
        }
        RoleplayConfig config = service.config();
        List<RoleplayDrawTarget> targets = resolveTargets(brief);
        if (targets.isEmpty()) {
            return RoleplayDrawPromptResult.failed("生图没有解析到目标角色");
        }
        String size = safe(brief.size).isEmpty() ? safe(requestedSize) : brief.size;
        String camera = safe(brief.camera).isEmpty() ? safe(requestedCamera) : brief.camera;
        if (targets.size() > 1 && "photo".equalsIgnoreCase(camera)) {
            camera = "";
            service.plugin().getLogger().sendInfo("[生图] 多目标场景取消 camera=photo，避免被编译成第一视角");
        }
        if ("photo".equalsIgnoreCase(camera) && isThirdPersonBrief(brief)) {
            camera = "";
            service.plugin().getLogger().sendInfo("[生图] 第三人称/偷拍构图取消 camera=photo，避免被编译成第一视角");
        }
        if (!config.drawPromptCompilerEnable) {
            return fallback(targets,brief,size,camera,"compiler-disabled");
        }
        PluginService ai = service.plugin().getServer().getPluginManager().getService("MBB-AI");
        if (ai == null) return fallback(targets,brief,size,camera,"ai-missing");
        try {
            JSONArray messages = new JSONArray();
            messages.add(service.message("system",compilerPrompt(targets,brief)));
            messages.add(service.message("user","请把下面的画面 brief 编译成最终生图 JSON：\n"
                    +brief.toJson().toJSONString()));
            JSONObject params = new JSONObject(true);
            String profile = safe(config.drawPromptProfile).isEmpty()
                    ? config.aiProfile : config.drawPromptProfile.trim();
            params.put("profile",profile);
            params.put("maxTokens",config.drawPromptMaxTokens);
            params.put("temperature",0.0);
            params.put("reasoningEffort",config.drawPromptReasoningEffort);
            params.put("timeoutSeconds",config.drawPromptTimeoutSecond);
            params.put("sessionId","roleplay-draw-prompt-"+groupID+"-"+userID);
            params.put("messages",messages);
            RoleplayAiMetadata.tagTemplate(params,"roleplay.draw.prompt",
                    RoleplayDrawPromptService.class);
            long startTime = System.currentTimeMillis();
            JSONObject response = ai.call("chat",params);
            RoleplayAiLog.log(service.plugin().getLogger(),"生图prompt",groupID,response,
                    System.currentTimeMillis() - startTime);
            JSONObject parsed = service.parseJson(response == null ? "" : response.getString("content"));
            if (parsed == null) {
                parsed = service.parseJson(response == null ? "" : response.getString("reasoningContent"));
            }
            if (parsed == null) {
                service.plugin().getLogger().sendWarn("[生图] prompt 编译没有返回合法 JSON，使用兜底编译。");
                return fallback(targets,brief,size,camera,"invalid-json");
            }
            String prompt = safe(parsed.getString("prompt"));
            String negative = safe(parsed.getString("negativePrompt"));
            String compiledSize = safe(parsed.getString("size"));
            String compiledCamera = safe(parsed.getString("camera"));
            RoleplayDrawPromptResult result = RoleplayDrawPromptValidator.validate(targets,brief,
                    prompt,negative,config.drawPromptMaxChars,config.drawPromptNegativeMaxChars);
            populateTargets(result,targets);
            if (!result.status) return result;
            if (!compiledSize.isEmpty()) result.size = compiledSize;
            else result.size = size;
            if (!compiledCamera.isEmpty()) result.camera = compiledCamera;
            else result.camera = camera;
            result.expectedTags = joinTargetTags(targets,false);
            result.forbiddenTags = joinTargetTags(targets,true);
            service.plugin().getLogger().sendInfo("[生图] brief="+service.shortText(brief.promptText(),240));
            service.plugin().getLogger().sendInfo("[生图] compiler="+service.shortText(result.prompt,240)
                    +" validation="+result.validation);
            return result;
        } catch (Exception e) {
            service.plugin().getLogger().sendWarn("[生图] prompt 编译异常，使用兜底编译："
                    +service.safe(e.getMessage()));
            return fallback(targets,brief,size,camera,"exception");
        }
    }

    private List<RoleplayDrawTarget> resolveTargets(RoleplayDrawBrief brief) {
        List<RoleplayDrawTarget> result = new ArrayList<>();
        if (brief.characters != null) {
            for (String name : brief.characters) {
                RoleplayDrawTarget target = resolveTarget(name);
                if (target != null) result.add(target);
            }
        }
        if (result.isEmpty() && !safe(brief.subject).isEmpty()
                && !"当前角色".equals(brief.subject.trim())
                && !matchesCurrentPersona(brief.subject)) {
            RoleplayDrawTarget target = resolveTarget(brief.subject);
            if (target != null) result.add(target);
        }
        if (result.isEmpty()) result.add(currentTarget());
        return result;
    }

    private RoleplayDrawTarget resolveTarget(String name) {
        String value = safe(name);
        if (value.isEmpty()) return null;
        if (matchesCurrentPersona(value)) return currentTarget();
        RoleplayDrawTarget target = service.getKnowledgeService().findCharacterTarget(value);
        if (target != null) return target;
        target = new RoleplayDrawTarget();
        target.name = value;
        target.canonicalTag = value;
        target.imageTags.add(value);
        return target;
    }

    private RoleplayDrawTarget currentTarget() {
        RoleplayPersona persona = service.persona();
        RoleplayDrawTarget target = new RoleplayDrawTarget();
        target.id = persona.name;
        target.name = persona.name;
        target.currentRole = true;
        target.appearance = persona.appearance;
        target.imageTags.addAll(persona.imageTags);
        target.negativeTags.addAll(persona.imageNegativeTags);
        target.aliases.addAll(persona.aliases);
        target.canonicalTag = target.imageTags.isEmpty() ? persona.name : target.imageTags.get(0);
        if (target.imageTags.isEmpty()) target.imageTags.add(target.canonicalTag);
        return target;
    }

    private boolean matchesCurrentPersona(String name) {
        String value = normalizedName(name);
        if (value.isEmpty()) return false;
        RoleplayPersona persona = service.persona();
        if (value.equals(normalizedName(persona.name))) return true;
        for (String alias : persona.aliases) {
            if (value.equals(normalizedName(alias))) return true;
        }
        return false;
    }

    private String compilerPrompt(List<RoleplayDrawTarget> targets,RoleplayDrawBrief brief) {
        StringBuilder builder = new StringBuilder();
        builder.append("你是生图 prompt 编译器，只输出 JSON，不要解释，不要聊天。\n")
                .append("本次画面的目标角色如下；只能画这些角色，不要把当前角色自动加入画面：\n");
        for (RoleplayDrawTarget target : targets) {
            builder.append("- ").append(target.name).append(" ").append(target.cardText()).append("\n");
        }
        builder.append("画面 brief：").append(brief.toJson().toJSONString()).append("\n");
        builder.append("编译规则：\n")
                .append("- 画面主体必须与 brief.characters/subject 一致；brief 没写当前角色时，不要把当前角色画进去。\n")
                .append("- 每个目标角色只允许使用自己的 canonical tag 和外貌；禁止把其他角色外貌套到目标角色身上。\n")
                .append("- 严格按 brief 的 action/scene/outfit 画，不要因为角色职业、习惯或梗自动添加画笔、游戏手柄、书本等未要求元素。\n")
                .append("- 必须明确每个角色的发色、瞳色、光环颜色、服装、配饰和武器；不能只写角色名。\n")
                .append("- 背景必须写具体环境、背景物件、光线、景深和氛围；禁止只写 simple background、solid background、flat color、纯色背景或简单色块。\n")
                .append("- 必须补充服装材质、光影和细节 tag，例如 fabric texture、detailed clothing、depth of field、volumetric lighting。\n")
                .append("- 必须使用目标角色 canonical tags；如果 brief 和 canonical tag 冲突，以 canonical tag 为准。\n")
                .append("- 不要写 masterpiece、best quality、highres、highly detailed、anime illustration 等质量词，插件会自动补。\n")
                .append("- 不要写 twins、clone、duplicate character、multiple views、split screen、extra person。\n")
                .append("- 单人使用 1girl，双人使用 2girls，人数标签只出现一次。\n")
                .append("- 多人普通同框不要使用 first person view；只有用户明确要求当前角色第一视角拍照时才使用 photo。\n")
                .append("- negativePrompt 只放角色专属负向词和身份冲突词；不要重复插件全局负向词。\n")
                .append("- prompt 使用逗号分隔的英文 booru 风格 tag，控制在 ")
                .append(service.config().drawPromptMaxChars).append(" 字符以内。\n")
                .append("- 最终 prompt 只能保留英文 tag；brief 里的中文元素必须翻译成英文 booru tag，不能原样输出中文。\n")
                .append("输出格式：{\"prompt\":\"...\",\"negativePrompt\":\"...\",")
                .append("\"size\":\"square|landscape|portrait|avatar\",\"camera\":\"selfie|photo|\"}");
        return builder.toString();
    }

    private RoleplayDrawPromptResult fallback(List<RoleplayDrawTarget> targets,
                                              RoleplayDrawBrief brief,
                                              String size,String camera,String reason) {
        StringBuilder positive = new StringBuilder();
        StringBuilder negative = new StringBuilder();
        for (RoleplayDrawTarget target : targets) {
            for (String tag : target.imageTags) {
                append(positive,tag);
            }
            for (String tag : target.negativeTags) {
                append(negative,tag);
            }
        }
        String briefText = brief.promptText();
        if (!briefText.isEmpty()) append(positive,briefText);
        RoleplayDrawPromptResult result = RoleplayDrawPromptValidator.validate(targets,brief,
                positive.toString(),negative.toString(),
                service.config().drawPromptMaxChars,service.config().drawPromptNegativeMaxChars);
        populateTargets(result,targets);
        if (result.status) {
            result.size = size;
            result.camera = camera;
            result.expectedTags = joinTargetTags(targets,false);
            result.forbiddenTags = joinTargetTags(targets,true);
            service.plugin().getLogger().sendWarn("[生图] 使用兜底编译 reason="+reason
                    +" prompt="+service.shortText(result.prompt,180));
        }
        return result;
    }

    private void populateTargets(RoleplayDrawPromptResult result,List<RoleplayDrawTarget> targets) {
        if (result == null || targets == null) return;
        for (RoleplayDrawTarget target : targets) {
            if (target == null) continue;
            addDistinct(result.targetNames,target.name);
            for (String alias : target.aliases) addDistinct(result.targetAliases,alias);
        }
    }

    private void addDistinct(List<String> values,String value) {
        String text = safe(value);
        if (!text.isEmpty() && !values.contains(text)) values.add(text);
    }

    private void append(StringBuilder builder,String value) {
        String text = safe(value);
        if (text.isEmpty()) return;
        if (builder.length() > 0) builder.append(", ");
        builder.append(text);
    }

    private String joinTargetTags(List<RoleplayDrawTarget> targets,boolean negative) {
        StringBuilder builder = new StringBuilder();
        for (RoleplayDrawTarget target : targets) {
            if (target == null) continue;
            List<String> values = negative ? target.negativeTags : target.imageTags;
            for (String value : values) append(builder,value);
        }
        return builder.toString();
    }

    private String normalizedName(String value) {
        String text = safe(value).toLowerCase(Locale.ROOT);
        int index = text.indexOf('(');
        if (index > 0) text = text.substring(0,index);
        return text.replace('_',' ').replaceAll("\\s+"," ").trim();
    }

    private boolean isThirdPersonBrief(RoleplayDrawBrief brief) {
        String text = safe(brief.subject)+" "+safe(brief.action)+" "+safe(brief.scene)
                +" "+safe(brief.composition)+" "+String.join(" ",brief.mustInclude);
        return text.contains("偷拍") || text.contains("远距离") || text.contains("门边")
                || text.contains("隔板") || text.contains("旁观") || text.contains("第三人称")
                || text.contains("监控") || text.contains("偷看") || text.contains("背影")
                || text.contains("远景") || text.contains("中景") || text.contains("全身");
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
