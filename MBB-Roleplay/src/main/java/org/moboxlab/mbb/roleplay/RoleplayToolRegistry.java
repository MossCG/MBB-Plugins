package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.PluginService;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Base64;

/**
 * 角色原生工具注册表。
 */
public class RoleplayToolRegistry {
    private final RoleplayService service;
    private final RoleplayWorkspaceService workspace;
    private final RoleplayWebService web;
    private final RoleplayHistorySearchService historySearch;
    private final Map<String,RoleplayTool> tools = new LinkedHashMap<>();

    public RoleplayToolRegistry(RoleplayService service,RoleplayConfig config) {
        this.service = service;
        this.workspace = new RoleplayWorkspaceService(service.plugin(),config);
        this.web = new RoleplayWebService(service);
        this.historySearch = new RoleplayHistorySearchService(service);
        register(new ToolSearchTool());
        register(new SelfStateTool());
        register(new TimeCalcTool());
        register(new TodoAddTool());
        register(new TodoListTool());
        register(new TodoDoneTool());
        register(new TodoSnoozeTool());
        register(new HistorySearchTool());
        register(new MemorySearchTool());
        register(new KnowledgeSearchTool());
        register(new SlangSearchTool());
        register(new MemeSearchTool());
        register(new WebFetchTool());
        register(new WebSearchTool());
        register(new WebImageSendTool());
        register(new ProfileGetTool());
        register(new ProfileUpdateTool());
        register(new WorkspaceListTool());
        register(new WorkspaceReadTool());
        register(new WorkspaceWriteTool());
        register(new WorkspaceImportImageTool());
        register(new WorkspaceMediaListTool());
        register(new WorkspaceNoteAppendTool());
        register(new WorkspaceDraftSaveTool());
        register(new WorkspaceDeleteTool());
        register(new WorkspaceSearchTool());
        register(new WorkspaceRecentTool());
        register(new WorkspaceRenameTool());
        register(new WorkspaceCopyTool());
        register(new WorkspaceRestoreTool());
        register(new MessageSendTool());
        register(new EndTurnTool());
        registerSkill("reminder");
        registerSkill("memory");
        registerSkill("global-memory");
        registerSkill("sticker");
        register(new StickerSaveTool());
        registerSkill("poke-back");
        registerSkill("draw");
        RoleplayQQTools.register(this,service);
    }

    public void reload(RoleplayConfig config) {
        workspace.reload(config);
    }

    public RoleplayWorkspaceService workspace() {
        return workspace;
    }

    public JSONArray schemas(RoleplayConfig config) {
        return schemas(config,null);
    }

    public JSONArray schemas(RoleplayConfig config,List<String> names) {
        JSONArray result = new JSONArray();
        if (!config.toolEnable) return result;
        for (RoleplayTool tool : tools.values()) {
            if (!enabled(tool,config)) continue;
            if (names != null && !names.contains(tool.name())) continue;
            result.add(tool.schema());
        }
        return result;
    }

    public List<String> coreToolNames(RoleplayConfig config) {
        List<String> result = new java.util.ArrayList<>();
        if (config == null) return result;
        for (String item : service.safe(config.toolCoreTools).split(",")) {
            String name = item == null ? "" : item.trim();
            RoleplayTool tool = get(name);
            if (!name.isEmpty() && tool != null && enabled(tool,config) && !result.contains(name)) {
                result.add(name);
            }
        }
        RoleplayTool search = get("tool_search");
        if (search != null && enabled(search,config) && !result.contains("tool_search")) {
            result.add(0,"tool_search");
        }
        return result;
    }

    public List<RoleplayTool> search(RoleplayConfig config,String query,int limit) {
        String text = service.safe(query).toLowerCase(Locale.ROOT).trim();
        String queryBigrams = text;
        List<ScoredTool> candidates = new java.util.ArrayList<>();
        for (RoleplayTool tool : tools.values()) {
            if (!enabled(tool,config) || "tool_search".equals(tool.name())) continue;
            String haystack = (tool.name()+" "+tool.category()+" "
                    +tool.description()).toLowerCase(Locale.ROOT);
            double score = 0;
            if (!text.isEmpty() && (haystack.contains(text) || text.contains(tool.name()))) {
                score += 8;
            }
            String normalized = SpeechCorpusEntry.normalize(haystack);
            java.util.Set<String> querySet = SpeechCorpusEntry.bigrams(
                    SpeechCorpusEntry.normalize(queryBigrams));
            if (!querySet.isEmpty()) {
                java.util.Set<String> toolSet = SpeechCorpusEntry.bigrams(normalized);
                int overlap = 0;
                for (String bigram : querySet) if (toolSet.contains(bigram)) overlap++;
                score += overlap * 2.0 / Math.max(1,querySet.size());
            }
            if (score > 0) candidates.add(new ScoredTool(tool,score));
        }
        candidates.sort((left,right) -> Double.compare(right.score,left.score));
        if (candidates.isEmpty()) {
            for (RoleplayTool tool : tools.values()) {
                if (enabled(tool,config) && !"tool_search".equals(tool.name())) {
                    candidates.add(new ScoredTool(tool,0));
                }
            }
        }
        List<RoleplayTool> result = new java.util.ArrayList<>();
        for (ScoredTool item : candidates) {
            if (result.size() >= Math.max(1,limit)) break;
            result.add(item.tool);
        }
        return result;
    }

    public String catalogueText(RoleplayConfig config) {
        StringBuilder builder = new StringBuilder();
        for (RoleplayTool tool : tools.values()) {
            if (!enabled(tool,config)) continue;
            builder.append("- ").append(tool.name()).append("：")
                    .append(tool.description()).append("\n");
        }
        return builder.toString();
    }

    public String categoryCatalogue(RoleplayConfig config) {
        Map<String,Integer> counts = new LinkedHashMap<>();
        for (RoleplayTool tool : tools.values()) {
            if (!enabled(tool,config)) continue;
            String category = tool.category() == null ? "general" : tool.category();
            Integer count = counts.get(category);
            counts.put(category,count == null ? 1 : count + 1);
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String,Integer> entry : counts.entrySet()) {
            builder.append("- ").append(entry.getKey())
                    .append("：").append(entry.getValue()).append(" 个\n");
        }
        return builder.toString();
    }

    /**
     * 给系统提示词用的紧凑能力索引。每个工具保留一句用途，不展开 schema，
     * 让角色知道能力边界，同时保留 tool_search 的按需参数查询。
     */
    public String capabilityIndexText(RoleplayConfig config) {
        Map<String,List<String>> items = new LinkedHashMap<>();
        for (RoleplayTool tool : tools.values()) {
            if (!enabled(tool,config)) continue;
            String category = tool.category() == null ? "general" : tool.category();
            List<String> lines = items.get(category);
            if (lines == null) {
                lines = new java.util.ArrayList<>();
                items.put(category,lines);
            }
            String description = service.shortText(service.safe(tool.description()).trim(),48);
            lines.add(description.isEmpty() ? tool.name() : tool.name()+"（"+description+"）");
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String,List<String>> entry : items.entrySet()) {
            builder.append("- ").append(entry.getKey()).append("：")
                    .append(String.join("、",entry.getValue())).append("\n");
        }
        return builder.toString();
    }

    public JSONObject selfTest(RoleplayConfig config) {
        JSONArray issues = new JSONArray();
        int checked = 0;
        for (RoleplayTool tool : tools.values()) {
            if (!enabled(tool,config)) continue;
            checked++;
            JSONObject schema = tool.schema();
            JSONObject function = schema == null ? null : schema.getJSONObject("function");
            JSONObject parameters = function == null ? null : function.getJSONObject("parameters");
            String name = function == null ? "" : function.getString("name");
            if (!"function".equals(schema == null ? "" : schema.getString("type"))) {
                issues.add(tool.name()+" schema type 错误");
            }
            if (!tool.name().equals(name)) {
                issues.add(tool.name()+" schema 名称不一致");
            }
            if (parameters == null || !"object".equals(parameters.getString("type"))) {
                issues.add(tool.name()+" parameters 不是 object");
                continue;
            }
            JSONObject properties = parameters.getJSONObject("properties");
            JSONArray required = parameters.getJSONArray("required");
            if (required != null && !required.isEmpty()) {
                String missing = validateArguments(tool.name(),new JSONObject(true));
                if (!missing.startsWith("缺少参数：")) {
                    issues.add(tool.name()+" 未拒绝缺失必填参数");
                }
            }
            JSONObject extra = new JSONObject(true);
            if (required != null && properties != null) {
                for (Object item : required) {
                    String key = item == null ? "" : String.valueOf(item);
                    JSONObject property = key.isEmpty() ? null : properties.getJSONObject(key);
                    extra.put(key,sampleValue(property == null ? "" : property.getString("type")));
                }
            }
            extra.put("__unexpected_field__",true);
            String extraResult = validateArguments(tool.name(),extra);
            if (!extraResult.startsWith("不允许的参数：")) {
                issues.add(tool.name()+" 未拒绝未知参数");
            }
        }
        JSONObject workspaceTest = config.workspaceEnable ? workspace.selfTest() : new JSONObject(true);
        JSONObject webTest = config.webEnable ? web.selfTest() : new JSONObject(true);
        if (!workspaceTest.isEmpty() && !workspaceTest.getBooleanValue("status")) {
            issues.add("工作区路径隔离自检失败");
        }
        if (!webTest.isEmpty() && !webTest.getBooleanValue("status")) {
            issues.add("网页白名单自检失败");
        }
        JSONObject result = new JSONObject(true);
        result.put("status",issues.isEmpty());
        result.put("checked",checked);
        result.put("issues",issues);
        result.put("workspace",workspaceTest);
        result.put("web",webTest);
        return result;
    }

    private Object sampleValue(String type) {
        if ("integer".equalsIgnoreCase(type) || "number".equalsIgnoreCase(type)) return Long.valueOf(1L);
        if ("boolean".equalsIgnoreCase(type)) return Boolean.TRUE;
        if ("array".equalsIgnoreCase(type)) return new JSONArray();
        if ("object".equalsIgnoreCase(type)) return new JSONObject(true);
        return "";
    }

    public RoleplayTool get(String name) {
        return name == null ? null : tools.get(name.trim());
    }

    /**
     * 工具是否对当前配置开放。未进入本轮暴露集合的工具由工具循环按需自动装载。
     */
    public boolean isEnabled(String name,RoleplayConfig config) {
        return enabled(get(name),config);
    }

    public RoleplayToolResult execute(RoleplayToolContext context,String name,JSONObject args) {
        RoleplayTool tool = get(name);
        if (tool == null) return RoleplayToolResult.failed("未知工具："+name);
        try {
            return tool.execute(context,args == null ? new JSONObject(true) : args);
        } catch (Exception e) {
            return RoleplayToolResult.failed(tool.name()+" 执行失败："+service.safe(e.getMessage()));
        }
    }

    public String validateArguments(String name,JSONObject args) {
        RoleplayTool tool = get(name);
        if (tool == null) return "未知工具："+name;
        JSONObject schema = tool.schema();
        JSONObject function = schema == null ? null : schema.getJSONObject("function");
        JSONObject parameters = function == null ? null : function.getJSONObject("parameters");
        if (parameters == null) return "";
        JSONObject value = args == null ? new JSONObject(true) : args;
        JSONArray required = parameters.getJSONArray("required");
        if (required != null) {
            for (Object item : required) {
                String key = item == null ? "" : String.valueOf(item);
                if (key.isEmpty() || !value.containsKey(key)) return "缺少参数："+key;
            }
        }
        JSONObject properties = parameters.getJSONObject("properties");
        if (properties != null) {
            for (String key : value.keySet()) {
                JSONObject property = properties.getJSONObject(key);
                if (property == null) {
                    if (Boolean.FALSE.equals(parameters.getBoolean("additionalProperties"))) {
                        return "不允许的参数："+key;
                    }
                    continue;
                }
                String expected = property.getString("type");
                Object actual = value.get(key);
                if (actual == null || acceptType(expected,actual)) continue;
                return "参数 "+key+" 类型应为 "+expected;
            }
        }
        return "";
    }

    private boolean acceptType(String expected,Object value) {
        if (expected == null || expected.isEmpty()) return true;
        if ("string".equalsIgnoreCase(expected)) return value instanceof String;
        if ("integer".equalsIgnoreCase(expected)) {
            return value instanceof Integer || value instanceof Long
                    || value instanceof Short || value instanceof Byte;
        }
        if ("number".equalsIgnoreCase(expected)) {
            return value instanceof Number;
        }
        if ("boolean".equalsIgnoreCase(expected)) return value instanceof Boolean;
        if ("array".equalsIgnoreCase(expected)) return value instanceof JSONArray;
        if ("object".equalsIgnoreCase(expected)) return value instanceof JSONObject;
        return true;
    }

    void register(RoleplayTool tool) {
        if (tool != null) tools.put(tool.name(),tool);
    }

    private void registerSkill(String id) {
        RoleplaySkillTool tool = RoleplaySkillTool.create(service,id);
        if (tool != null) register(tool);
    }

    private boolean enabled(RoleplayTool tool,RoleplayConfig config) {
        if (tool == null || config == null || !config.toolEnable) return false;
        if (tool.name().startsWith("workspace_") && !config.workspaceEnable) return false;
        if (("web_fetch".equals(tool.name()) || "web_search".equals(tool.name())
                || "web_image_send".equals(tool.name()))
                && !config.webEnable) return false;
        if ("web_image_send".equals(tool.name()) && !hasService("MBB-QQ")) return false;
        if ("kb_search".equals(tool.name()) && !config.knowledgeEnable) return false;
        if ("meme_search".equals(tool.name()) && !config.memeSearchEnable) return false;
        if ("reminder".equals(tool.name()) && !config.reminderEnable) return false;
        if ("memory".equals(tool.name()) && !config.activeMemory) return false;
        if ("global-memory".equals(tool.name()) && !config.globalMemoryEnable) return false;
        if ("poke-back".equals(tool.name()) && !config.pokeBackEnable) return false;
        if ("draw".equals(tool.name()) && !hasService("MBB-ComfyUI")) {
            return false;
        }
        if ("sticker".equals(tool.name()) && !hasService("MBB-Sticker")) {
            return false;
        }
        if ("sticker_save".equals(tool.name()) && !hasService("MBB-Sticker")) {
            return false;
        }
        return true;
    }

    private boolean hasService(String name) {
        return service.plugin().getServer() != null
                && service.plugin().getServer().getPluginManager() != null
                && service.plugin().getServer().getPluginManager().getService(name) != null;
    }

    private abstract class BaseTool implements RoleplayTool {
        @Override
        public String category() {
            return "general";
        }

        @Override
        public int maxCallsPerTurn() {
            return 4;
        }

        @Override
        public boolean sideEffect() {
            return false;
        }

        protected JSONObject functionSchema(String name,String description,JSONObject properties,
                                            String[] required) {
            JSONObject function = new JSONObject(true);
            function.put("name",name);
            function.put("description",description);
            JSONObject parameters = new JSONObject(true);
            parameters.put("type","object");
            parameters.put("properties",properties == null ? new JSONObject(true) : properties);
            JSONArray requiredArray = new JSONArray();
            if (required != null) {
                for (String item : required) {
                    if (item != null && !item.isEmpty()) requiredArray.add(item);
                }
            }
            parameters.put("required",requiredArray);
            parameters.put("additionalProperties",false);
            function.put("parameters",parameters);
            JSONObject schema = new JSONObject(true);
            schema.put("type","function");
            schema.put("function",function);
            return schema;
        }

        protected JSONObject property(String type,String description) {
            JSONObject property = new JSONObject(true);
            property.put("type",type);
            property.put("description",description);
            return property;
        }
    }

    private class ToolSearchTool extends BaseTool {
        @Override
        public String name() { return "tool_search"; }

        @Override
        public String category() { return "core"; }

        @Override
        public String description() {
            return "查询工具参数或发现不确定的能力；已知工具名可直接调用，无需先搜索";
        }

        @Override
        public int maxCallsPerTurn() { return 3; }

        @Override
        public JSONObject schema() {
            JSONObject properties = new JSONObject(true);
            properties.put("query",property("string","要查找的能力、对象或动作"));
            properties.put("limit",property("integer","最多返回几个工具，1 到 10"));
            return functionSchema(name(),description(),properties,new String[]{"query"});
        }

        @Override
        public RoleplayToolResult execute(RoleplayToolContext context,JSONObject args) {
            String query = service.safe(args.getString("query")).trim();
            if (query.isEmpty()) return RoleplayToolResult.failed("query 不能为空");
            int limit = args.getIntValue("limit");
            if (limit <= 0) limit = service.config().toolSearchMaxResults;
            limit = Math.max(1,Math.min(10,limit));
            List<RoleplayTool> selected = search(service.config(),query,limit);
            JSONArray tools = new JSONArray();
            for (RoleplayTool tool : selected) {
                JSONObject item = new JSONObject(true);
                item.put("name",tool.name());
                item.put("category",tool.category());
                item.put("description",tool.description());
                RoleplaySkill skill = service.skillRegistry().get(tool.name());
                if (skill != null) {
                    RoleplaySkillContext skillContext = new RoleplaySkillContext();
                    skillContext.groupID = context.groupID;
                    skillContext.userID = context.userID;
                    skillContext.messageID = context.messageID;
                    skillContext.selfID = context.selfID;
                    skillContext.userName = context.userName;
                    skillContext.relationship = context.relationship;
                    skillContext.message = context.message;
                    skillContext.state = service.state(context.groupID);
                    String instruction = service.safe(skill.promptFragment(skillContext));
                    if (!instruction.isEmpty()) {
                        item.put("instruction",service.shortText(instruction,2400));
                    }
                }
                tools.add(item);
            }
            RoleplayToolResult result = RoleplayToolResult.ok(
                    "已加载 "+tools.size()+" 个工具，下一步可直接调用");
            result.data.put("query",query);
            result.data.put("tools",tools);
            for (RoleplayTool tool : selected) result.activateTools.add(tool.name());
            return result;
        }
    }

    private class SelfStateTool extends BaseTool {
        @Override
        public String name() { return "self_state"; }

        @Override
        public String category() { return "core"; }

        @Override
        public String description() { return "查看当前发送冷却、工作区、记忆和回复约束等真实运行状态"; }

        @Override
        public JSONObject schema() {
            return functionSchema(name(),description(),new JSONObject(true),new String[0]);
        }

        @Override
        public RoleplayToolResult execute(RoleplayToolContext context,JSONObject args) {
            JSONObject data = new JSONObject(true);
            data.put("groupID",context.groupID);
            data.put("canReply",!service.rateLimited(context.groupID));
            data.put("workspaceEnabled",service.config().workspaceEnable);
            data.put("workspacePath","plugins/MBB-Roleplay/data");
            data.put("memoryCount",service.status(context.groupID).getIntValue("memoryCount"));
            data.put("shortSummary",service.shortText(service.shortSummary(context.groupID),600));
            data.put("replySegmentMaxChars",service.config().replySegmentMaxChars);
            data.put("replyMaxSegments",service.config().replyMaxSegments);
            RoleplayToolResult result = RoleplayToolResult.ok("已读取当前运行状态");
            result.data = data;
            return result;
        }
    }

    private class TimeCalcTool extends BaseTool {
        @Override
        public String name() { return "time_calc"; }

        @Override
        public String description() {
            return "按角色时区计算当前时间、时间加减、两个时间差和距离目标时间";
        }

        @Override
        public String category() { return "utility"; }

        @Override
        public JSONObject schema() {
            JSONObject properties = new JSONObject(true);
            properties.put("operation",property("string","now / add / diff / until"));
            properties.put("baseTime",property("string","基准时间，省略为当前时间"));
            properties.put("targetTime",property("string","目标时间，支持 yyyy-MM-dd HH:mm:ss、明天 08:00 等"));
            properties.put("amount",property("integer","add 操作的数量"));
            properties.put("unit",property("string","second / minute / hour / day / week，或中文单位"));
            return functionSchema(name(),description(),properties,new String[]{"operation"});
        }

        @Override
        public RoleplayToolResult execute(RoleplayToolContext context,JSONObject args) {
            try {
                JSONObject data = service.timeService().calculate(args.getString("operation"),
                        args.getString("baseTime"),args.getString("targetTime"),
                        args.getIntValue("amount"),args.getString("unit"));
                RoleplayToolResult result = RoleplayToolResult.ok("时间计算完成");
                result.data = data;
                return result;
            } catch (Exception e) {
                return RoleplayToolResult.failed(e.getMessage());
            }
        }
    }

    private class TodoAddTool extends BaseTool {
        @Override
        public String name() { return "todo_add"; }

        @Override
        public String description() {
            return "新增用户待办或角色自身待办；有时限时可同时创建提醒";
        }

        @Override
        public String category() { return "productivity"; }

        @Override
        public boolean sideEffect() { return true; }

        @Override
        public int maxCallsPerTurn() { return 3; }

        @Override
        public JSONObject schema() {
            JSONObject properties = new JSONObject(true);
            properties.put("title",property("string","待办内容"));
            properties.put("dueTime",property("string","可选截止时间，如 明天 08:00"));
            properties.put("scope",property("string","user 当前用户待办，self 角色自身待办；默认 user"));
            properties.put("remind",property("boolean","到点是否创建提醒"));
            return functionSchema(name(),description(),properties,new String[]{"title"});
        }

        @Override
        public RoleplayToolResult execute(RoleplayToolContext context,JSONObject args) {
            try {
                JSONObject data = service.todoService().add(context.groupID,context.userID,
                        context.userName,args.getString("title"),args.getString("dueTime"),
                        args.getString("scope"),args.getBooleanValue("remind"));
                RoleplayToolResult result = RoleplayToolResult.ok("待办已创建");
                result.data = data;
                return result;
            } catch (Exception e) {
                return RoleplayToolResult.failed(e.getMessage());
            }
        }
    }

    private class TodoListTool extends BaseTool {
        @Override
        public String name() { return "todo_list"; }

        @Override
        public String description() {
            return "查看当前用户待办、角色自身待办或两者合并列表";
        }

        @Override
        public String category() { return "productivity"; }

        @Override
        public JSONObject schema() {
            JSONObject properties = new JSONObject(true);
            properties.put("scope",property("string","all / user / self，默认 all"));
            properties.put("includeDone",property("boolean","是否包含已完成，默认 false"));
            properties.put("limit",property("integer","最多返回多少条，1 到 100"));
            return functionSchema(name(),description(),properties,new String[0]);
        }

        @Override
        public RoleplayToolResult execute(RoleplayToolContext context,JSONObject args) {
            try {
                JSONObject data = service.todoService().list(context.groupID,context.userID,
                        service.isOwner(context.userID),args.getString("scope"),
                        args.getBooleanValue("includeDone"),args.getIntValue("limit"));
                JSONArray items = data.getJSONArray("items");
                RoleplayToolResult result = RoleplayToolResult.ok("待办 "
                        +(items == null ? 0 : items.size())+" 条");
                result.data = data;
                return result;
            } catch (Exception e) {
                return RoleplayToolResult.failed(e.getMessage());
            }
        }
    }

    private class TodoDoneTool extends BaseTool {
        @Override
        public String name() { return "todo_done"; }

        @Override
        public String description() { return "把一条待办标记为完成，并取消关联提醒"; }

        @Override
        public String category() { return "productivity"; }

        @Override
        public boolean sideEffect() { return true; }

        @Override
        public int maxCallsPerTurn() { return 3; }

        @Override
        public JSONObject schema() {
            JSONObject properties = new JSONObject(true);
            properties.put("id",property("integer","todo_list 返回的待办 ID"));
            return functionSchema(name(),description(),properties,new String[]{"id"});
        }

        @Override
        public RoleplayToolResult execute(RoleplayToolContext context,JSONObject args) {
            try {
                JSONObject data = service.todoService().done(args.getLongValue("id"),
                        context.groupID,context.userID,service.isOwner(context.userID));
                RoleplayToolResult result = RoleplayToolResult.ok("待办已完成");
                result.data = data;
                return result;
            } catch (Exception e) {
                return RoleplayToolResult.failed(e.getMessage());
            }
        }
    }

    private class TodoSnoozeTool extends BaseTool {
        @Override
        public String name() { return "todo_snooze"; }

        @Override
        public String description() { return "延后一条待办的时间，并同步调整关联提醒"; }

        @Override
        public String category() { return "productivity"; }

        @Override
        public boolean sideEffect() { return true; }

        @Override
        public int maxCallsPerTurn() { return 3; }

        @Override
        public JSONObject schema() {
            JSONObject properties = new JSONObject(true);
            properties.put("id",property("integer","待办 ID"));
            properties.put("dueTime",property("string","新的截止时间"));
            properties.put("minutes",property("integer","也可以只延后多少分钟，默认 30"));
            return functionSchema(name(),description(),properties,new String[]{"id"});
        }

        @Override
        public RoleplayToolResult execute(RoleplayToolContext context,JSONObject args) {
            try {
                int minutes = args.containsKey("minutes")
                        ? args.getIntValue("minutes") : 30;
                JSONObject data = service.todoService().snooze(args.getLongValue("id"),
                        context.groupID,context.userID,service.isOwner(context.userID),
                        args.getString("dueTime"),minutes);
                RoleplayToolResult result = RoleplayToolResult.ok("待办已延后");
                result.data = data;
                return result;
            } catch (Exception e) {
                return RoleplayToolResult.failed(e.getMessage());
            }
        }
    }

    private class HistorySearchTool extends BaseTool {
        @Override
        public String name() { return "history_search"; }

        @Override
        public String description() { return "搜索当前群最近的聊天流水，返回匹配的消息、发言人和时间"; }

        @Override
        public String category() { return "retrieval"; }

        @Override
        public JSONObject schema() {
            JSONObject properties = new JSONObject(true);
            properties.put("query",property("string","要搜索的原文片段"));
            properties.put("limit",property("integer","最多返回条数，1 到 20"));
            properties.put("days",property("integer","向前搜索多少天，默认 30，最大 3650"));
            return functionSchema(name(),description(),properties,new String[]{"query"});
        }

        @Override
        public RoleplayToolResult execute(RoleplayToolContext context,JSONObject args) {
            String query = service.safe(args.getString("query")).trim();
            if (query.isEmpty()) return RoleplayToolResult.failed("query 不能为空");
            int limit = args.getIntValue("limit");
            JSONObject found = historySearch.search(context.groupID,query,limit,
                    args.getIntValue("days"));
            JSONArray messages = found.getJSONArray("messages");
            RoleplayToolResult result = RoleplayToolResult.ok("找到 "+(messages == null ? 0 : messages.size())
                    +" 条历史消息");
            result.data.putAll(found);
            return result;
        }
    }

    private class MemorySearchTool extends BaseTool {
        @Override
        public String name() { return "memory_search"; }

        @Override
        public String description() { return "按需检索角色自我、跨群、群内、好友和个人记忆；个人记忆默认查当前发言人"; }

        @Override
        public String category() { return "retrieval"; }

        @Override
        public JSONObject schema() {
            JSONObject properties = new JSONObject(true);
            properties.put("query",property("string","要查询的主题或关键词"));
            properties.put("userID",property("integer","可选，要查询个人记忆的 QQ；默认当前发言人"));
            return functionSchema(name(),description(),properties,new String[]{"query"});
        }

        @Override
        public RoleplayToolResult execute(RoleplayToolContext context,JSONObject args) {
            String query = service.safe(args.getString("query")).trim();
            if (query.isEmpty()) return RoleplayToolResult.failed("query 不能为空");
            long targetUserID = context.userID;
            if (args.containsKey("userID")) {
                try {
                    targetUserID = Long.parseLong(String.valueOf(args.get("userID")).trim());
                } catch (Exception e) {
                    return RoleplayToolResult.failed("userID 必须是有效 QQ 号");
                }
                if (targetUserID <= 0) {
                    return RoleplayToolResult.failed("userID 必须是有效 QQ 号");
                }
            }
            int budget = Math.max(1000,service.config().toolResultMaxChars);
            String profile;
            if (!service.config().memberMemoryEnable) {
                profile = "个人记忆未启用。";
            } else {
                profile = service.memberService().promptText(context.groupID,targetUserID);
                if (profile.isEmpty()) profile = "暂无该群员的个人记忆。";
            }
            String friendMemory = targetUserID == context.userID || service.isOwner(context.userID)
                    ? service.friendMemoryService().promptText(targetUserID) : "";
            RoleplayToolResult result = RoleplayToolResult.ok("已按需检索角色自我、跨群、群内、好友和个人记忆");
            result.data.put("query",query);
            result.data.put("userID",targetUserID);
            result.data.put("shortTerm",service.shortText(service.shortSummary(0L),budget * 8 / 100));
            result.data.put("profile",service.shortText(profile,budget * 10 / 100));
            String selfMemory = service.brainMemoryService().selfPromptText(
                    context.groupID,targetUserID,query,budget * 18 / 100);
            if (!selfMemory.isEmpty()) {
                result.data.put("selfMemory",selfMemory);
            }
            result.data.put("brainMatches",service.brainMemoryService().search(
                    context.groupID,targetUserID,"group",query,12));
            if (!friendMemory.isEmpty()) {
                result.data.put("friendMemory",service.shortText(friendMemory,budget * 15 / 100));
            }
            result.data.put("longTerm",service.shortText(
                    service.longMemoryText(context.groupID,targetUserID,query),
                    budget * 18 / 100));
            result.data.put("global",service.shortText(
                    service.globalMemoryService().promptText(query,targetUserID,
                            Math.max(1000,budget * 18 / 100)),
                    budget * 18 / 100));
            return result;
        }
    }

    private class KnowledgeSearchTool extends BaseTool {
        @Override
        public String name() { return "kb_search"; }

        @Override
        public String description() { return "检索已安装的知识库，返回条目、小节和可引用的正文"; }

        @Override
        public String category() { return "retrieval"; }

        @Override
        public JSONObject schema() {
            JSONObject properties = new JSONObject(true);
            properties.put("query",property("string","要查询的角色、社团、学院、剧情或设定问题"));
            properties.put("library",property("string","可选，只查指定知识库 ID"));
            properties.put("limit",property("integer","最多返回几个条目，1 到 20"));
            return functionSchema(name(),description(),properties,new String[]{"query"});
        }

        @Override
        public RoleplayToolResult execute(RoleplayToolContext context,JSONObject args) {
            String query = service.safe(args.getString("query")).trim();
            if (query.isEmpty()) return RoleplayToolResult.failed("query 不能为空");
            String requested = service.safe(args.getString("library")).trim();
            List<RoleplayKnowledgeLibrary> libraries = service.knowledgeService().enabledLibraries();
            if (libraries.isEmpty()) return RoleplayToolResult.failed("当前没有启用的知识库");
            if (!requested.isEmpty() && !hasLibrary(libraries,requested)) {
                return RoleplayToolResult.failed("知识库不存在或未启用："+requested);
            }
            int limit = args.getIntValue("limit");
            if (limit <= 0) limit = 6;
            JSONArray results = service.knowledgeService().toolResults(query,requested,limit,
                    Math.max(2000,service.config().toolResultMaxChars));
            RoleplayToolResult result = RoleplayToolResult.ok(results.isEmpty()
                    ? "知识库未命中相关条目" : "知识库命中 "+results.size()+" 个条目");
            result.trust = "external-untrusted";
            result.data.put("query",query);
            result.data.put("results",results);
            return result;
        }

        private boolean hasLibrary(List<RoleplayKnowledgeLibrary> libraries,String id) {
            for (RoleplayKnowledgeLibrary library : libraries) {
                if (library != null && id.equals(library.id)) return true;
            }
            return false;
        }
    }

    private class SlangSearchTool extends BaseTool {
        @Override
        public String name() { return "slang_search"; }

        @Override
        public String description() { return "查询本地网络用语词集，返回含义、使用语境、禁用语境和风险等级"; }

        @Override
        public String category() { return "retrieval"; }

        @Override
        public JSONObject schema() {
            JSONObject properties = new JSONObject(true);
            properties.put("query",property("string","要查询的网络用语、缩写、类别或含义"));
            properties.put("limit",property("integer","最多返回几条，1 到 20，默认 8"));
            return functionSchema(name(),description(),properties,new String[]{"query"});
        }

        @Override
        public RoleplayToolResult execute(RoleplayToolContext context,JSONObject args) {
            String query = service.safe(args.getString("query")).trim();
            if (query.isEmpty()) return RoleplayToolResult.failed("query 不能为空");
            int limit = args.getIntValue("limit");
            if (limit <= 0) limit = 8;
            JSONArray results = service.slangLexicon().search(query,limit);
            RoleplayToolResult result = RoleplayToolResult.ok(results.isEmpty()
                    ? "本地网络用语词集未命中" : "网络用语命中 "+results.size()+" 条");
            result.data.put("query",query);
            result.data.put("results",results);
            result.data.put("status",service.slangLexicon().status());
            return result;
        }
    }

    private class MemeSearchTool extends BaseTool {
        @Override
        public String name() { return "meme_search"; }

        @Override
        public String description() { return "怀疑对方在使用已有网络梗时，检索梗Wiki公开词条的标题、摘要、标签和来源链接"; }

        @Override
        public String category() { return "retrieval"; }

        @Override
        public JSONObject schema() {
            JSONObject properties = new JSONObject(true);
            properties.put("query",property("string","疑似梗名、完整短语或关键描述；不要只传一两个字母"));
            properties.put("tag",property("string","可选，单个标签，例如谐音梗、游戏梗、弹幕文化"));
            properties.put("limit",property("integer","最多返回几条，1 到 5，默认使用配置值"));
            return functionSchema(name(),description(),properties,new String[]{"query"});
        }

        @Override
        public RoleplayToolResult execute(RoleplayToolContext context,JSONObject args) {
            String query = service.safe(args.getString("query")).trim();
            if (query.isEmpty()) return RoleplayToolResult.failed("query 不能为空");
            try {
                JSONObject found = service.memeService().search(query,args.getString("tag"),
                        args.getIntValue("limit"));
                JSONArray items = found.getJSONArray("items");
                RoleplayToolResult result = RoleplayToolResult.untrusted(items == null || items.isEmpty()
                        ? "梗Wiki未命中公开词条" : "梗Wiki命中 "+items.size()+" 条公开词条");
                result.data.putAll(found);
                return result;
            } catch (Exception e) {
                return RoleplayToolResult.failed("梗检索失败："+service.safe(e.getMessage()));
            }
        }
    }

    private class StickerSaveTool extends BaseTool {
        @Override
        public String name() { return "sticker_save"; }

        @Override
        public String description() {
            return "把当前消息里确实值得复用的图片或表情保存到 MBB-Sticker 表情库";
        }

        @Override
        public String category() { return "action"; }

        @Override
        public boolean sideEffect() { return true; }

        @Override
        public int maxCallsPerTurn() { return 1; }

        @Override
        public JSONObject schema() {
            JSONObject properties = new JSONObject(true);
            properties.put("tags",property("string","可选，英文逗号分隔的情绪或场景标签；留空会自动识图"));
            properties.put("description",property("string","可选，这个表情包的简短画面描述"));
            return functionSchema(name(),description(),properties,new String[0]);
        }

        @Override
        public RoleplayToolResult execute(RoleplayToolContext context,JSONObject args) {
            JSONObject image = currentImage(context);
            if (image == null) return RoleplayToolResult.failed("当前消息没有可保存的图片或表情");
            PluginService sticker = service.plugin().getServer().getPluginManager()
                    .getService("MBB-Sticker");
            if (sticker == null) return RoleplayToolResult.failed("MBB-Sticker 未启用");
            JSONObject params = new JSONObject(true);
            params.put("file",service.safe(image.getString("file")));
            String unique = service.safe(image.getString("file_unique"));
            if (unique.isEmpty()) unique = service.safe(image.getString("file_id"));
            params.put("fileUnique",unique);
            params.put("url",service.safe(image.getString("url")));
            params.put("source","roleplay");
            params.put("description",service.shortText(service.safe(args.getString("description")),200));
            JSONArray tags = new JSONArray();
            for (String item : service.safe(args.getString("tags")).split("[,，]")) {
                if (!item.trim().isEmpty()) tags.add(item.trim());
            }
            params.put("tags",tags);
            JSONObject response = sticker.call("add",params);
            if (response == null || !response.getBooleanValue("status")) {
                return RoleplayToolResult.failed(response == null
                        ? "MBB-Sticker 没有返回结果" : response.getString("message"));
            }
            RoleplayToolResult result = RoleplayToolResult.ok(response.getBooleanValue("duplicate")
                    ? "这个表情包里已经有，已刷新使用记录"
                    : "已把这个表情保存到表情包库");
            result.data.put("id",service.safe(response.getString("id")));
            result.data.put("duplicate",response.getBooleanValue("duplicate"));
            result.data.put("tags",response.getJSONArray("tags"));
            result.data.put("description",service.safe(response.getString("description")));
            return result;
        }

        private JSONObject currentImage(RoleplayToolContext context) {
            return service.currentOrRecentImage(context.groupID,context.userID,
                    context.eventRaw,context.message);
        }
    }

    private class WebFetchTool extends BaseTool {
        @Override
        public String name() { return "web_fetch"; }

        @Override
        public String description() { return "读取白名单网站中的公开网页并返回净化后的正文"; }

        @Override
        public String category() { return "web"; }

        @Override
        public JSONObject schema() {
            JSONObject properties = new JSONObject(true);
            properties.put("url",property("string","要读取的 http/https 网页地址"));
            return functionSchema(name(),description(),properties,new String[]{"url"});
        }

        @Override
        public RoleplayToolResult execute(RoleplayToolContext context,JSONObject args) {
            String url = service.safe(args.getString("url")).trim();
            if (url.isEmpty()) return RoleplayToolResult.failed("url 不能为空");
            try {
                boolean internal = web.isInternal(url);
                boolean ownerAllowed = !internal || context.ownerAuthorizedInternalNetwork
                        || service.ownerAuthorizedInternalWeb(
                        context.groupID,context.userID,url);
                JSONObject page = web.fetch(url,ownerAllowed);
                String text = service.safe(page.getString("text")).trim();
                if (text.isEmpty()) return RoleplayToolResult.failed("网页没有可读取的正文");
                RoleplayToolResult result = RoleplayToolResult.untrusted("已读取网页："+url);
                result.data = page;
                result.data.put("internal",internal);
                result.data.put("ownerAuthorized",ownerAllowed);
                return result;
            } catch (Exception e) {
                return RoleplayToolResult.failed("网页读取失败："+service.safe(e.getMessage()));
            }
        }
    }

    private class WebSearchTool extends BaseTool {
        @Override
        public String name() { return "web_search"; }

        @Override
        public String category() { return "web"; }

        @Override
        public String description() {
            return "联网搜索公开信息，返回最多 10 条标题和链接；调用前先自行把聊天原句改写为检索关键词";
        }

        @Override
        public JSONObject schema() {
            JSONObject properties = new JSONObject(true);
            properties.put("query",property("string",
                    "由你改写后的搜索关键词；不要直接传整句聊天，必要时自行加入时间、版本或 CVE"));
            return functionSchema(name(),description(),properties,new String[]{"query"});
        }

        @Override
        public RoleplayToolResult execute(RoleplayToolContext context,JSONObject args) {
            String query = service.safe(args.getString("query")).trim();
            if (query.isEmpty()) return RoleplayToolResult.failed("query 不能为空");
            try {
                JSONObject payload = web.searchPayload(query,false);
                JSONArray results = payload.getJSONArray("results");
                boolean anyProviderResponded = providerResponded(payload.getJSONArray("attempts"));
                String displayQuery = service.shortText(
                        query.replace("\r"," ").replace("\n"," "),80);
                String provider = service.safe(payload.getString("provider"));
                if (provider.isEmpty()) provider = "unknown";
                String cacheState = payload.getBooleanValue("cached") ? "是" : "否";
                service.plugin().getLogger().sendInfo("[搜索] 群"+context.groupID
                        +" 关键词="+displayQuery+" 来源="+provider
                        +" 命中="+results.size()+" 缓存="+cacheState);
                RoleplayToolResult result;
                if (results.isEmpty() && !anyProviderResponded) {
                    result = RoleplayToolResult.failed("联网搜索「"+displayQuery
                            +"」失败：搜索源连接失败或被风控页拦截");
                    result.trust = "external-untrusted";
                } else {
                    result = RoleplayToolResult.untrusted(
                            results.isEmpty() ? "联网搜索「"+displayQuery+"」没有返回可解析结果"
                                    : "联网搜索「"+displayQuery+"」返回 "+results.size()
                                    +" 条结果，来源="+provider);
                }
                result.data.putAll(payload);
                result.data.put("query",query);
                if (results.isEmpty()) {
                    result.data.put("retryHint",
                            "换用作品、人物或站点的官方英文名/英文别名后重试一次，不要重复原查询");
                }
                return result;
            } catch (Exception e) {
                return RoleplayToolResult.failed("联网搜索「"+service.shortText(query,80)
                        +"」失败："+service.safe(e.getMessage()));
            }
        }

        private boolean providerResponded(JSONArray attempts) {
            if (attempts == null) return false;
            for (Object object : attempts) {
                if (object instanceof JSONObject
                        && ((JSONObject)object).getBooleanValue("status")) return true;
            }
            return false;
        }
    }

    private class WebImageSendTool extends BaseTool {
        @Override
        public String name() { return "web_image_send"; }

        @Override
        public String category() { return "web"; }

        @Override
        public String description() {
            return "从白名单网页获取一张公开图片并发送到当前群；支持直接图片 URL 和作品页预览图";
        }

        @Override
        public boolean sideEffect() { return true; }

        @Override
        public int maxCallsPerTurn() { return 1; }

        @Override
        public JSONObject schema() {
            JSONObject properties = new JSONObject(true);
            properties.put("url",property("string","图片直链，或包含 og:image 的白名单公开网页"));
            return functionSchema(name(),description(),properties,new String[]{"url"});
        }

        @Override
        public RoleplayToolResult execute(RoleplayToolContext context,JSONObject args) {
            if (context == null || context.groupID <= 0) {
                return RoleplayToolResult.failed("当前会话不能主动发送群图片");
            }
            String url = service.safe(args.getString("url")).trim();
            if (url.isEmpty()) return RoleplayToolResult.failed("url 不能为空");
            PluginService qq = service.plugin().getServer().getPluginManager().getService("MBB-QQ");
            if (qq == null) return RoleplayToolResult.failed("MBB-QQ 未启用");
            try {
                boolean internal = web.isInternal(url);
                boolean ownerAllowed = !internal || context.ownerAuthorizedInternalNetwork
                        || service.ownerAuthorizedInternalWeb(
                        context.groupID,context.userID,url);
                RoleplayWebService.ImagePayload image = web.fetchImage(url,ownerAllowed);
                JSONObject params = new JSONObject(true);
                params.put("groupID",context.groupID);
                params.put("operatorID",context.userID);
                params.put("file","base64://"+Base64.getEncoder().encodeToString(image.bytes));
                JSONObject sent = qq.call("send_image",params);
                if (sent == null || !sent.getBooleanValue("status")) {
                    return RoleplayToolResult.failed(sent == null
                            ? "图片发送没有返回结果" : sent.getString("message"));
                }
                RoleplayToolResult result = RoleplayToolResult.untrusted(
                        "已从网络获取并发送图片");
                result.data.put("sourceUrl",image.url);
                result.data.put("contentType",image.contentType);
                result.data.put("format",image.format);
                result.data.put("bytes",image.bytes.length);
                return result;
            } catch (Exception e) {
                return RoleplayToolResult.failed("网络图片发送失败："+service.safe(e.getMessage()));
            }
        }
    }

    private class ProfileGetTool extends BaseTool {
        @Override
        public String name() { return "profile_get"; }

        @Override
        public String description() { return "查看角色对当前群员或指定 QQ 的个人印象"; }

        @Override
        public String category() { return "retrieval"; }

        @Override
        public JSONObject schema() {
            JSONObject properties = new JSONObject(true);
            properties.put("userID",property("integer","目标 QQ；省略时查看当前发言者"));
            return functionSchema(name(),description(),properties,new String[0]);
        }

        @Override
        public RoleplayToolResult execute(RoleplayToolContext context,JSONObject args) {
            long userID = args.getLongValue("userID");
            if (userID <= 0) userID = context.userID;
            JSONObject profile = service.getMemberService().profile(context.groupID,userID);
            RoleplayToolResult result = RoleplayToolResult.ok(profile == null ? "暂无该群员档案" : "已读取群员档案");
            result.data.put("profile",profile == null ? new JSONObject(true) : profile);
            return result;
        }
    }

    private class ProfileUpdateTool extends BaseTool {
        @Override
        public String name() { return "profile_update"; }

        @Override
        public String description() {
            return "用户明确纠正称呼、喜好或相处边界时，更新群员个人档案；改他人档案仅 owner 可用";
        }

        @Override
        public String category() { return "memory"; }

        @Override
        public boolean sideEffect() { return true; }

        @Override
        public int maxCallsPerTurn() { return 2; }

        @Override
        public JSONObject schema() {
            JSONObject properties = new JSONObject(true);
            properties.put("userID",property("integer","可选目标 QQ，默认当前发言者；改他人仅 owner"));
            properties.put("alias",property("string","应该怎么称呼这个群员"));
            properties.put("likes",property("string","明确表达过的喜好"));
            properties.put("dislikes",property("string","明确表达过的不喜欢"));
            properties.put("notes",property("string","相处方式或边界"));
            return functionSchema(name(),description(),properties,new String[0]);
        }

        @Override
        public RoleplayToolResult execute(RoleplayToolContext context,JSONObject args) {
            try {
                long targetID = args.getLongValue("userID");
                if (targetID <= 0) targetID = context.userID;
                if (targetID != context.userID && !service.isOwner(context.userID)) {
                    return RoleplayToolResult.failed("只有 owner 可以修改其他群员的档案");
                }
                JSONObject member = new JSONObject(true);
                copyField(args,member,"alias");
                copyField(args,member,"likes");
                copyField(args,member,"dislikes");
                copyField(args,member,"notes");
                if (member.isEmpty()) return RoleplayToolResult.failed("至少提供一个要更新的档案字段");
                String userName = targetID == context.userID ? context.userName : "";
                boolean changed = service.getMemberService().applyAi(
                        context.groupID,targetID,userName,member);
                JSONObject profile = service.getMemberService().profile(context.groupID,targetID);
                RoleplayToolResult result = RoleplayToolResult.ok(changed
                        ? "群员档案已更新" : "群员档案没有变化");
                result.data.put("userID",targetID);
                result.data.put("profile",profile == null ? new JSONObject(true) : profile);
                return result;
            } catch (Exception e) {
                return RoleplayToolResult.failed(e.getMessage());
            }
        }

        private void copyField(JSONObject source,JSONObject target,String key) {
            String value = service.safe(source.getString(key)).trim();
            if (!value.isEmpty()) target.put(key,value);
        }
    }

    private class WorkspaceListTool extends BaseTool {
        @Override
        public String name() { return "workspace_list"; }

        @Override
        public String description() { return "列出角色私有工作区中的文件和文件夹"; }

        @Override
        public String category() { return "workspace"; }

        @Override
        public JSONObject schema() {
            JSONObject properties = new JSONObject(true);
            properties.put("path",property("string","相对 data/ 的目录，省略为根目录"));
            return functionSchema(name(),description(),properties,new String[0]);
        }

        @Override
        public RoleplayToolResult execute(RoleplayToolContext context,JSONObject args) {
            try {
                RoleplayToolResult result = RoleplayToolResult.ok("已列出工作区");
                result.data = workspace.list(args.getString("path"));
                return result;
            } catch (Exception e) {
                return RoleplayToolResult.failed(e.getMessage());
            }
        }
    }

    private class WorkspaceReadTool extends BaseTool {
        @Override
        public String name() { return "workspace_read"; }

        @Override
        public String description() { return "读取角色私有工作区中的一个 UTF-8 文本文件"; }

        @Override
        public String category() { return "workspace"; }

        @Override
        public JSONObject schema() {
            JSONObject properties = new JSONObject(true);
            properties.put("path",property("string","相对 data/ 的文件路径"));
            return functionSchema(name(),description(),properties,new String[]{"path"});
        }

        @Override
        public RoleplayToolResult execute(RoleplayToolContext context,JSONObject args) {
            try {
                RoleplayToolResult result = RoleplayToolResult.ok("已读取文件");
                result.data = workspace.read(args.getString("path"));
                return result;
            } catch (Exception e) {
                return RoleplayToolResult.failed(e.getMessage());
            }
        }
    }

    private class WorkspaceWriteTool extends BaseTool {
        @Override
        public String name() { return "workspace_write"; }

        @Override
        public String description() { return "创建或修改角色私有工作区中的 UTF-8 文本文件"; }

        @Override
        public String category() { return "workspace"; }

        @Override
        public boolean sideEffect() { return true; }

        @Override
        public int maxCallsPerTurn() { return 4; }

        @Override
        public JSONObject schema() {
            JSONObject properties = new JSONObject(true);
            properties.put("path",property("string","相对 data/ 的文件路径"));
            properties.put("content",property("string","UTF-8 文本内容"));
            properties.put("append",property("boolean","是否追加到文件末尾"));
            return functionSchema(name(),description(),properties,new String[]{"path","content"});
        }

        @Override
        public RoleplayToolResult execute(RoleplayToolContext context,JSONObject args) {
            try {
                RoleplayToolResult result = RoleplayToolResult.ok("文件已写入");
                result.data = workspace.write(args.getString("path"),args.getString("content"),
                        args.getBooleanValue("append"));
                return result;
            } catch (Exception e) {
                return RoleplayToolResult.failed(e.getMessage());
            }
        }
    }

    private class WorkspaceImportImageTool extends BaseTool {
        @Override
        public String name() { return "workspace_import_image"; }

        @Override
        public String description() {
            return "把当前消息图片、最近图片或白名单网页图片保存到角色工作区";
        }

        @Override
        public String category() { return "workspace"; }

        @Override
        public boolean sideEffect() { return true; }

        @Override
        public int maxCallsPerTurn() { return 2; }

        @Override
        public JSONObject schema() {
            JSONObject properties = new JSONObject(true);
            properties.put("path",property("string","可选，目标相对路径；省略时自动写入 media/images/"));
            properties.put("url",property("string","可选，白名单网页图片地址；省略时使用当前或最近图片"));
            properties.put("overwrite",property("boolean","目标存在时是否覆盖，默认 false"));
            return functionSchema(name(),description(),properties,new String[0]);
        }

        @Override
        public RoleplayToolResult execute(RoleplayToolContext context,JSONObject args) {
            try {
                String url = service.safe(args.getString("url")).trim();
                if (!url.isEmpty()) {
                    boolean internal = web.isInternal(url);
                    boolean ownerAllowed = !internal || context.ownerAuthorizedInternalNetwork
                            || service.ownerAuthorizedInternalWeb(
                            context.groupID,context.userID,url);
                    RoleplayWebService.ImagePayload image = web.fetchImage(url,ownerAllowed);
                    JSONObject saved = workspace.importImage(args.getString("path"),
                            image.bytes,image.contentType,"web:"+image.url,
                            args.getBooleanValue("overwrite"));
                    RoleplayToolResult result = RoleplayToolResult.ok("网页图片已保存到工作区");
                    result.data = saved;
                    return result;
                }
                JSONObject image = service.currentOrRecentImage(context.groupID,context.userID,
                        context.eventRaw,context.message);
                if (image == null) return RoleplayToolResult.failed("当前没有可导入的图片");
                PluginService vision = service.plugin().getServer().getPluginManager()
                        .getService("MBB-Vision");
                if (vision == null) return RoleplayToolResult.failed("MBB-Vision 未启用，无法读取当前图片");
                JSONObject params = new JSONObject(true);
                String unique = service.safe(image.getString("file_unique"));
                if (unique.isEmpty()) unique = service.safe(image.getString("file_id"));
                params.put("fileUnique",unique);
                params.put("url",service.safe(image.getString("url")));
                params.put("file",service.safe(image.getString("file")));
                JSONObject response = vision.call("dataUri",params);
                if (response == null || !response.getBooleanValue("status")) {
                    return RoleplayToolResult.failed(response == null
                            ? "识图服务没有返回图片" : response.getString("message"));
                }
                String dataUri = service.safe(response.getString("dataUri"));
                int comma = dataUri.indexOf(',');
                if (comma <= 0) return RoleplayToolResult.failed("识图服务返回了无效图片数据");
                String mime = dataUri.substring(0,comma);
                int semicolon = mime.indexOf(';');
                if (semicolon >= 0) mime = mime.substring(0,semicolon);
                if (mime.startsWith("data:")) mime = mime.substring(5);
                byte[] bytes = Base64.getDecoder().decode(dataUri.substring(comma + 1));
                JSONObject saved = workspace.importImage(args.getString("path"),bytes,mime,
                        "message:"+context.messageID,args.getBooleanValue("overwrite"));
                RoleplayToolResult result = RoleplayToolResult.ok("当前图片已保存到工作区");
                result.data = saved;
                return result;
            } catch (Exception e) {
                return RoleplayToolResult.failed("图片导入失败："+service.safe(e.getMessage()));
            }
        }
    }

    private class WorkspaceMediaListTool extends BaseTool {
        @Override
        public String name() { return "workspace_media_list"; }

        @Override
        public String description() { return "递归列出工作区中的图片、语音和视频文件"; }

        @Override
        public String category() { return "workspace"; }

        @Override
        public JSONObject schema() {
            JSONObject properties = new JSONObject(true);
            properties.put("path",property("string","相对 data/ 的目录，省略为根目录"));
            properties.put("kind",property("string","all / image / audio / video，默认 all"));
            properties.put("limit",property("integer","最多返回多少条，1 到 200"));
            return functionSchema(name(),description(),properties,new String[0]);
        }

        @Override
        public RoleplayToolResult execute(RoleplayToolContext context,JSONObject args) {
            try {
                JSONObject data = workspace.mediaList(args.getString("path"),
                        args.getString("kind"),args.getIntValue("limit"));
                JSONArray items = data.getJSONArray("items");
                RoleplayToolResult result = RoleplayToolResult.ok("已列出 "
                        +(items == null ? 0 : items.size())+" 个媒体文件");
                result.data = data;
                return result;
            } catch (Exception e) {
                return RoleplayToolResult.failed(e.getMessage());
            }
        }
    }

    private class WorkspaceNoteAppendTool extends BaseTool {
        @Override
        public String name() { return "workspace_note_append"; }

        @Override
        public String description() {
            return "向结构化笔记追加一条日记、人物观察、灵感、资料或普通笔记";
        }

        @Override
        public String category() { return "workspace"; }

        @Override
        public boolean sideEffect() { return true; }

        @Override
        public int maxCallsPerTurn() { return 3; }

        @Override
        public JSONObject schema() {
            JSONObject properties = new JSONObject(true);
            properties.put("kind",property("string","journal / person / idea / reference / general，默认 journal"));
            properties.put("title",property("string","标题；person 和 reference 必填"));
            properties.put("content",property("string","要追加的笔记正文"));
            properties.put("tags",property("array","可选标签数组"));
            return functionSchema(name(),description(),properties,new String[]{"content"});
        }

        @Override
        public RoleplayToolResult execute(RoleplayToolContext context,JSONObject args) {
            try {
                JSONObject data = workspace.appendNote(args.getString("kind"),
                        args.getString("title"),args.getString("content"),args.getJSONArray("tags"));
                RoleplayToolResult result = RoleplayToolResult.ok("笔记已追加");
                result.data = data;
                return result;
            } catch (Exception e) {
                return RoleplayToolResult.failed(e.getMessage());
            }
        }
    }

    private class WorkspaceDraftSaveTool extends BaseTool {
        @Override
        public String name() { return "workspace_draft_save"; }

        @Override
        public String description() {
            return "把回复、Qzone、生图或剧情草稿归档到工作区，保留标题、标签和创建时间";
        }

        @Override
        public String category() { return "workspace"; }

        @Override
        public boolean sideEffect() { return true; }

        @Override
        public int maxCallsPerTurn() { return 3; }

        @Override
        public JSONObject schema() {
            JSONObject properties = new JSONObject(true);
            properties.put("kind",property("string","reply / qzone / draw / story / other"));
            properties.put("title",property("string","草稿标题"));
            properties.put("content",property("string","草稿正文"));
            properties.put("tags",property("array","可选标签数组"));
            return functionSchema(name(),description(),properties,new String[]{"content"});
        }

        @Override
        public RoleplayToolResult execute(RoleplayToolContext context,JSONObject args) {
            try {
                JSONObject data = workspace.saveDraft(args.getString("kind"),
                        args.getString("title"),args.getString("content"),args.getJSONArray("tags"));
                RoleplayToolResult result = RoleplayToolResult.ok("草稿已归档");
                result.data = data;
                return result;
            } catch (Exception e) {
                return RoleplayToolResult.failed(e.getMessage());
            }
        }
    }

    private class WorkspaceDeleteTool extends BaseTool {
        @Override
        public String name() { return "workspace_delete"; }

        @Override
        public String description() { return "把文件或目录移入工作区回收站；默认不永久删除"; }

        @Override
        public String category() { return "workspace"; }

        @Override
        public boolean sideEffect() { return true; }

        @Override
        public int maxCallsPerTurn() { return 2; }

        @Override
        public JSONObject schema() {
            JSONObject properties = new JSONObject(true);
            properties.put("path",property("string","相对 data/ 的文件或目录路径"));
            properties.put("recursive",property("boolean","是否递归删除非空目录"));
            properties.put("permanent",property("boolean","是否永久删除，默认 false 进入回收站"));
            return functionSchema(name(),description(),properties,new String[]{"path"});
        }

        @Override
        public RoleplayToolResult execute(RoleplayToolContext context,JSONObject args) {
            try {
                JSONObject deleted = workspace.delete(args.getString("path"),
                        args.getBooleanValue("recursive"),args.getBooleanValue("permanent"));
                RoleplayToolResult result = RoleplayToolResult.ok(
                        deleted.getBooleanValue("permanent") ? "已永久删除" : "已移入回收站");
                result.data = deleted;
                return result;
            } catch (Exception e) {
                return RoleplayToolResult.failed(e.getMessage());
            }
        }
    }

    private class WorkspaceSearchTool extends BaseTool {
        @Override
        public String name() { return "workspace_search"; }

        @Override
        public String description() {
            return "按文本、标题、文件类型、标签和时间范围检索角色工作区";
        }

        @Override
        public String category() { return "workspace"; }

        @Override
        public JSONObject schema() {
            JSONObject properties = new JSONObject(true);
            properties.put("query",property("string","可选，搜索文件名、标题或正文"));
            properties.put("path",property("string","相对 data/ 的搜索目录，省略为根目录"));
            properties.put("kind",property("string","all / text / note / journal / person / idea / reference / draft / image / audio / video"));
            properties.put("tags",property("array","可选标签数组，匹配任意一个"));
            properties.put("days",property("integer","可选，只搜索最近多少天"));
            properties.put("maxResults",property("integer","最多返回结果数，1 到 100"));
            return functionSchema(name(),description(),properties,new String[0]);
        }

        @Override
        public RoleplayToolResult execute(RoleplayToolContext context,JSONObject args) {
            try {
                JSONObject data = workspace.structuredSearch(args.getString("path"),
                        args.getString("query"),args.getString("kind"),args.getJSONArray("tags"),
                        args.getIntValue("maxResults"),args.getIntValue("days"));
                JSONArray items = data.getJSONArray("items");
                RoleplayToolResult result = RoleplayToolResult.ok("工作区检索命中 "
                        +(items == null ? 0 : items.size())+" 个文件");
                result.data = data;
                return result;
            } catch (Exception e) {
                return RoleplayToolResult.failed(e.getMessage());
            }
        }
    }

    private class WorkspaceRecentTool extends BaseTool {
        @Override
        public String name() { return "workspace_recent"; }

        @Override
        public String description() { return "列出角色工作区最近修改的文件，可按类型和天数筛选"; }

        @Override
        public String category() { return "workspace"; }

        @Override
        public JSONObject schema() {
            JSONObject properties = new JSONObject(true);
            properties.put("path",property("string","相对 data/ 的目录，省略为根目录"));
            properties.put("kind",property("string","all / text / note / journal / person / idea / reference / draft / image / audio / video"));
            properties.put("days",property("integer","只看最近多少天"));
            properties.put("limit",property("integer","最多返回多少条，1 到 100"));
            return functionSchema(name(),description(),properties,new String[0]);
        }

        @Override
        public RoleplayToolResult execute(RoleplayToolContext context,JSONObject args) {
            try {
                JSONObject data = workspace.recent(args.getString("path"),
                        args.getString("kind"),args.getIntValue("limit"),args.getIntValue("days"));
                JSONArray items = data.getJSONArray("items");
                RoleplayToolResult result = RoleplayToolResult.ok("最近文件 "
                        +(items == null ? 0 : items.size())+" 个");
                result.data = data;
                return result;
            } catch (Exception e) {
                return RoleplayToolResult.failed(e.getMessage());
            }
        }
    }

    private class WorkspaceRenameTool extends BaseTool {
        @Override
        public String name() { return "workspace_rename"; }

        @Override
        public String category() { return "workspace"; }

        @Override
        public String description() { return "重命名或移动角色私有工作区中的文件或目录"; }

        @Override
        public boolean sideEffect() { return true; }

        @Override
        public int maxCallsPerTurn() { return 3; }

        @Override
        public JSONObject schema() {
            JSONObject properties = new JSONObject(true);
            properties.put("source",property("string","源路径"));
            properties.put("target",property("string","目标路径"));
            properties.put("overwrite",property("boolean","目标存在时是否覆盖"));
            return functionSchema(name(),description(),properties,new String[]{"source","target"});
        }

        @Override
        public RoleplayToolResult execute(RoleplayToolContext context,JSONObject args) {
            try {
                RoleplayToolResult result = RoleplayToolResult.ok("已重命名工作区路径");
                result.data = workspace.rename(args.getString("source"),args.getString("target"),
                        args.getBooleanValue("overwrite"));
                return result;
            } catch (Exception e) {
                return RoleplayToolResult.failed(e.getMessage());
            }
        }
    }

    private class WorkspaceCopyTool extends BaseTool {
        @Override
        public String name() { return "workspace_copy"; }

        @Override
        public String category() { return "workspace"; }

        @Override
        public String description() { return "复制角色私有工作区中的文件或目录"; }

        @Override
        public boolean sideEffect() { return true; }

        @Override
        public int maxCallsPerTurn() { return 2; }

        @Override
        public JSONObject schema() {
            JSONObject properties = new JSONObject(true);
            properties.put("source",property("string","源路径"));
            properties.put("target",property("string","目标路径"));
            properties.put("overwrite",property("boolean","目标存在时是否覆盖"));
            return functionSchema(name(),description(),properties,new String[]{"source","target"});
        }

        @Override
        public RoleplayToolResult execute(RoleplayToolContext context,JSONObject args) {
            try {
                RoleplayToolResult result = RoleplayToolResult.ok("已复制工作区路径");
                result.data = workspace.copy(args.getString("source"),args.getString("target"),
                        args.getBooleanValue("overwrite"));
                return result;
            } catch (Exception e) {
                return RoleplayToolResult.failed(e.getMessage());
            }
        }
    }

    private class WorkspaceRestoreTool extends BaseTool {
        @Override
        public String name() { return "workspace_restore"; }

        @Override
        public String category() { return "workspace"; }

        @Override
        public String description() { return "从工作区回收站恢复文件或目录"; }

        @Override
        public boolean sideEffect() { return true; }

        @Override
        public int maxCallsPerTurn() { return 2; }

        @Override
        public JSONObject schema() {
            JSONObject properties = new JSONObject(true);
            properties.put("trashPath",property("string","workspace_delete 返回的 trashPath"));
            properties.put("target",property("string","恢复后的相对路径，省略时使用原文件名"));
            properties.put("overwrite",property("boolean","目标存在时是否覆盖"));
            return functionSchema(name(),description(),properties,new String[]{"trashPath"});
        }

        @Override
        public RoleplayToolResult execute(RoleplayToolContext context,JSONObject args) {
            try {
                RoleplayToolResult result = RoleplayToolResult.ok("已从回收站恢复");
                result.data = workspace.restore(args.getString("trashPath"),
                        args.getString("target"),args.getBooleanValue("overwrite"));
                return result;
            } catch (Exception e) {
                return RoleplayToolResult.failed(e.getMessage());
            }
        }
    }

    private class MessageSendTool extends BaseTool {
        @Override
        public String name() { return "message_send"; }

        @Override
        public String description() {
            return "在执行检索前追加一句简短回应；拿到工具结果后不要再用它说结论，最终结论必须走普通回复";
        }

        @Override
        public String category() { return "core"; }

        @Override
        public boolean sideEffect() { return true; }

        @Override
        public int maxCallsPerTurn() { return 1; }

        @Override
        public JSONObject schema() {
            JSONObject properties = new JSONObject(true);
            properties.put("text",property("string","要发送的聊天正文，只发一条短消息"));
            properties.put("quote",property("boolean","是否引用当前触发消息"));
            return functionSchema(name(),description(),properties,new String[]{"text"});
        }

        @Override
        public RoleplayToolResult execute(RoleplayToolContext context,JSONObject args) {
            if (context == null || context.groupID <= 0) {
                return RoleplayToolResult.failed("当前会话不能主动发送群消息");
            }
            String text = service.safe(args.getString("text"))
                    .replace("\r"," ").replace("\n"," ").replaceAll("\\s+"," ").trim();
            if (text.isEmpty()) return RoleplayToolResult.failed("text 不能为空");
            if (text.length() > Math.max(60,service.config().replySegmentMaxChars)) {
                return RoleplayToolResult.failed("主动消息过长，最多 "
                        +Math.max(60,service.config().replySegmentMaxChars)+" 个字符");
            }
            boolean sent = service.messageSender().sendSingleMessage(
                    service.plugin().getServer().getOneBotClient(),context.groupID,context.selfID,
                    context.userID,text,args.getBooleanValue("quote"),context.messageID);
            if (!sent) return RoleplayToolResult.failed("消息发送失败");
            RoleplayToolResult result = RoleplayToolResult.ok("已追加发送一条消息");
            result.data.put("text",text);
            return result;
        }
    }

    private class EndTurnTool extends BaseTool {
        @Override
        public String name() { return "end_turn"; }

        @Override
        public String category() { return "core"; }

        @Override
        public String description() { return "本轮不发送任何文字并立即结束；只适合确实无需回复或完成动作后保持沉默"; }

        @Override
        public int maxCallsPerTurn() { return 1; }

        @Override
        public JSONObject schema() {
            JSONObject properties = new JSONObject(true);
            properties.put("reason",property("string","结束本轮的简短原因"));
            return functionSchema(name(),description(),properties,new String[]{"reason"});
        }

        @Override
        public RoleplayToolResult execute(RoleplayToolContext context,JSONObject args) {
            RoleplayToolResult result = RoleplayToolResult.ok("本轮已结束");
            result.endTurn = true;
            result.data.put("reason",service.shortText(service.safe(args.getString("reason")),200));
            return result;
        }
    }

    private String formatTime(long time) {
        if (time <= 0) return "";
        return new SimpleDateFormat("MM-dd HH:mm:ss",Locale.CHINA).format(new Date(time));
    }

    private static class ScoredTool {
        private final RoleplayTool tool;
        private final double score;

        private ScoredTool(RoleplayTool tool,double score) {
            this.tool = tool;
            this.score = score;
        }
    }
}
