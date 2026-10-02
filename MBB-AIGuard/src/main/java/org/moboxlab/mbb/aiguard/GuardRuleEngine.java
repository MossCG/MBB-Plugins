package org.moboxlab.mbb.aiguard;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Plugin;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 可扩展风险规则引擎
 */
public class GuardRuleEngine {
    private final Plugin plugin;
    private final List<GuardRule> rules = new ArrayList<>();

    public GuardRuleEngine(Plugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        rules.clear();
        try {
            String path = plugin.getDataFolder()+"/rules.json";
            String text = new String(Files.readAllBytes(Paths.get(path)),StandardCharsets.UTF_8);
            JSONObject root = JSONObject.parseObject(text);
            JSONArray array = root == null ? null : root.getJSONArray("rules");
            if (array == null) {
                plugin.getLogger().sendWarn("rules.json 缺少 rules 节点！");
                return;
            }
            for (int i = 0; i < array.size(); i++) {
                JSONObject json = array.getJSONObject(i);
                if (json == null) continue;
                GuardRule rule = new GuardRule();
                rule.category = safe(json.getString("category"));
                rule.risk = json.getIntValue("risk");
                rule.safety = json.getBooleanValue("safety");
                JSONArray keywords = json.getJSONArray("keywords");
                if (keywords != null) {
                    for (Object keyword : keywords) {
                        if (keyword != null) rule.keywords.add(String.valueOf(keyword));
                    }
                }
                JSONArray patterns = json.getJSONArray("patterns");
                if (patterns != null) {
                    for (Object pattern : patterns) {
                        if (pattern == null) continue;
                        try {
                            rule.patterns.add(Pattern.compile(String.valueOf(pattern),Pattern.CASE_INSENSITIVE));
                        } catch (Exception e) {
                            plugin.getLogger().sendWarn("风险规则正则无效："+pattern);
                        }
                    }
                }
                if (!rule.category.isEmpty()) rules.add(rule);
            }
            plugin.getLogger().sendInfo("风险规则已加载："+rules.size()+" 条");
        } catch (Exception e) {
            plugin.getLogger().sendWarn("读取 rules.json 失败："+e.getMessage());
        }
    }

    public List<GuardMatch> match(String content) {
        List<GuardMatch> result = new ArrayList<>();
        for (GuardRule rule : rules) {
            GuardMatch match = rule.match(content);
            if (match != null) result.add(match);
        }
        return result;
    }

    public int size() {
        return rules.size();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
