package org.moboxlab.mbb.trpg.store;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 角色卡数据，对应 member.cardJSON。
 *
 * <p>结构固定为 {@code {attrs, skills, san, hp, mp, items}}。解析失败或字段缺失时
 * 使用空值并记录告警，由加载方统一打印；随机生成规则由后续实现片负责，
 * 本类只固定结构与容错口径。</p>
 */
public final class CardData {

    /** 八项属性键，顺序固定。 */
    public static final List<String> ATTRIBUTES;

    static {
        List<String> attributes = new ArrayList<String>();
        attributes.add("STR");
        attributes.add("CON");
        attributes.add("SIZ");
        attributes.add("DEX");
        attributes.add("APP");
        attributes.add("INT");
        attributes.add("POW");
        attributes.add("EDU");
        ATTRIBUTES = Collections.unmodifiableList(attributes);
    }

    private final Map<String, Integer> attrs;
    private final Map<String, Integer> skills;
    private final int san;
    private final int hp;
    private final int mp;
    private final List<String> items;
    private final List<String> warnings;

    public CardData(Map<String, Integer> attrs, Map<String, Integer> skills,
                    int san, int hp, int mp, List<String> items, List<String> warnings) {
        Map<String, Integer> normalizedAttrs = new LinkedHashMap<String, Integer>();
        for (String attribute : ATTRIBUTES) {
            Integer value = attrs == null ? null : attrs.get(attribute);
            normalizedAttrs.put(attribute, value == null ? 0 : value);
        }
        this.attrs = Collections.unmodifiableMap(normalizedAttrs);
        this.skills = Collections.unmodifiableMap(skills == null
                ? new LinkedHashMap<String, Integer>() : new LinkedHashMap<String, Integer>(skills));
        this.san = san;
        this.hp = hp;
        this.mp = mp;
        this.items = Collections.unmodifiableList(items == null
                ? new ArrayList<String>() : new ArrayList<String>(items));
        this.warnings = Collections.unmodifiableList(warnings == null
                ? new ArrayList<String>() : new ArrayList<String>(warnings));
    }

    /** 空卡：八属性全 0，其他为空。 */
    public static CardData empty() {
        return new CardData(null, null, 0, 0, 0, null, null);
    }

    /** 解析 cardJSON，失败返回空卡并记录告警。 */
    public static CardData fromJson(String json) {
        List<String> warnings = new ArrayList<String>();
        if (json == null || json.trim().isEmpty()) {
            return new CardData(null, null, 0, 0, 0, null, warnings);
        }
        JSONObject root;
        try {
            root = JSONObject.parseObject(json);
        } catch (RuntimeException e) {
            warnings.add("角色卡 JSON 解析失败，按空卡处理：" + e.getMessage());
            return new CardData(null, null, 0, 0, 0, null, warnings);
        }
        if (root == null) {
            warnings.add("角色卡 JSON 为空，按空卡处理");
            return new CardData(null, null, 0, 0, 0, null, warnings);
        }

        Map<String, Integer> attrs = new LinkedHashMap<String, Integer>();
        JSONObject attrJson = root.getJSONObject("attrs");
        for (String attribute : ATTRIBUTES) {
            if (attrJson != null && attrJson.containsKey(attribute)) {
                try {
                    attrs.put(attribute, attrJson.getIntValue(attribute));
                } catch (RuntimeException e) {
                    warnings.add("角色卡属性 " + attribute + " 解析失败，按 0 处理");
                }
            } else {
                warnings.add("角色卡缺少属性 " + attribute + "，按 0 处理");
            }
        }

        Map<String, Integer> skills = new LinkedHashMap<String, Integer>();
        JSONObject skillJson = root.getJSONObject("skills");
        if (skillJson != null) {
            for (String key : skillJson.keySet()) {
                try {
                    skills.put(key, skillJson.getIntValue(key));
                } catch (RuntimeException e) {
                    warnings.add("角色卡技能 " + key + " 解析失败，已忽略");
                }
            }
        }

        List<String> items = new ArrayList<String>();
        JSONArray itemArray = root.getJSONArray("items");
        if (itemArray != null) {
            for (int i = 0; i < itemArray.size(); i++) {
                Object item = itemArray.get(i);
                if (item != null) {
                    items.add(String.valueOf(item));
                }
            }
        }
        return new CardData(attrs, skills, root.getIntValue("san"), root.getIntValue("hp"),
                root.getIntValue("mp"), items, warnings);
    }

    /** 序列化为 cardJSON。 */
    public String toJson() {
        JSONObject root = new JSONObject(true);
        JSONObject attrJson = new JSONObject(true);
        for (Map.Entry<String, Integer> entry : attrs.entrySet()) {
            attrJson.put(entry.getKey(), entry.getValue());
        }
        root.put("attrs", attrJson);
        JSONObject skillJson = new JSONObject(true);
        for (Map.Entry<String, Integer> entry : skills.entrySet()) {
            skillJson.put(entry.getKey(), entry.getValue());
        }
        root.put("skills", skillJson);
        root.put("san", san);
        root.put("hp", hp);
        root.put("mp", mp);
        root.put("items", new JSONArray(new ArrayList<Object>(items)));
        return root.toJSONString();
    }

    public int attribute(String key) {
        Integer value = attrs.get(key);
        return value == null ? 0 : value;
    }

    public Map<String, Integer> attributes() {
        return attrs;
    }

    public Map<String, Integer> skills() {
        return skills;
    }

    public int skill(String skillId) {
        Integer value = skills.get(skillId);
        return value == null ? 0 : value;
    }

    public int san() {
        return san;
    }

    public int hp() {
        return hp;
    }

    public int mp() {
        return mp;
    }

    public List<String> items() {
        return items;
    }

    /** 解析期告警。 */
    public List<String> warnings() {
        return warnings;
    }

    public CardData withAttribute(String key, int value) {
        Map<String, Integer> next = new LinkedHashMap<String, Integer>(attrs);
        next.put(key, value);
        return new CardData(next, skills, san, hp, mp, items, warnings);
    }

    public CardData withSkills(Map<String, Integer> nextSkills) {
        return new CardData(attrs, nextSkills, san, hp, mp, items, warnings);
    }

    public CardData withSan(int value) {
        return new CardData(attrs, skills, value, hp, mp, items, warnings);
    }

    public CardData withHp(int value) {
        return new CardData(attrs, skills, san, value, mp, items, warnings);
    }

    public CardData withMp(int value) {
        return new CardData(attrs, skills, san, hp, value, items, warnings);
    }

    public CardData withItems(List<String> nextItems) {
        return new CardData(attrs, skills, san, hp, mp, nextItems, warnings);
    }
}
