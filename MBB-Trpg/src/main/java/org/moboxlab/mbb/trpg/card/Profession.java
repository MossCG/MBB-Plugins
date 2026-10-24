package org.moboxlab.mbb.trpg.card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 角色职业原型（§7.5 角色卡生成）。
 *
 * <p>职业不落库：{@code cardJSON} 冻结结构只有八属性、技能、SAN/HP/MP、物品。
 * 职业按技能集合反查，仅用于群内摘要、折叠回退与帮助文案；查不到时按「冒险者」处理。</p>
 */
public enum Profession {

    JOURNALIST("记者", base("图书馆使用", 60, "说服", 55, "观察", 50)),
    DOCTOR("医生", base("医学", 65, "急救", 60, "心理分析", 45)),
    DETECTIVE("侦探", base("侦查", 65, "聆听", 55, "心理学", 50)),
    ANTIQUARIAN("古董商", base("估价", 60, "历史", 55, "图书馆使用", 50));

    /** 反查失败时的兜底职业名。 */
    public static final String UNKNOWN_LABEL = "冒险者";

    private final String label;
    private final Map<String, Integer> baseSkills;

    Profession(String label, Map<String, Integer> baseSkills) {
        this.label = label;
        this.baseSkills = baseSkills;
    }

    public String label() {
        return label;
    }

    /** 该职业的初始技能（技能名 → 技能值），顺序固定且不可变。 */
    public Map<String, Integer> skills() {
        return baseSkills;
    }

    /** 全部职业，按声明顺序返回。 */
    public static List<Profession> all() {
        List<Profession> list = new ArrayList<Profession>();
        Collections.addAll(list, values());
        return Collections.unmodifiableList(list);
    }

    /** 按技能集合反查职业；集合不一致或为空时返回 null（由调用方回退「冒险者」）。 */
    public static Profession fromSkills(Map<String, Integer> skills) {
        if (skills == null || skills.isEmpty()) {
            return null;
        }
        for (Profession profession : values()) {
            if (profession.baseSkills.keySet().equals(skills.keySet())) {
                return profession;
            }
        }
        return null;
    }

    private static Map<String, Integer> base(Object... pairs) {
        Map<String, Integer> map = new LinkedHashMap<String, Integer>();
        for (int index = 0; index + 1 < pairs.length; index += 2) {
            map.put((String) pairs[index], (Integer) pairs[index + 1]);
        }
        return Collections.unmodifiableMap(map);
    }
}
