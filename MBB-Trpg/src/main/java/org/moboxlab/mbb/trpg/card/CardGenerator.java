package org.moboxlab.mbb.trpg.card;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.moboxlab.mbb.trpg.rule.RandomSource;
import org.moboxlab.mbb.trpg.script.Script;
import org.moboxlab.mbb.trpg.store.CardData;

/**
 * 角色卡生成（§7.5）：八属性 3D6×5，派生 SAN = POW、HP = (CON+SIZ)/10、MP = POW/5。
 *
 * <p>技能来自随机抽中的职业原型（职业本身不落库）；物品取剧本 {@code startingItems}。
 * 重掷（{@code pc reroll}）语义即「重新生成」，因此 SAN 天然等于新 POW。</p>
 */
public final class CardGenerator {

    private static final int ATTRIBUTE_DICE = 3;
    private static final int ATTRIBUTE_FACES = 6;
    private static final int ATTRIBUTE_MULTIPLIER = 5;
    private static final int HP_DIVISOR = 10;
    private static final int MP_DIVISOR = 5;

    private final RandomSource random;

    public CardGenerator(RandomSource random) {
        if (random == null) {
            throw new IllegalArgumentException("随机源不能为空");
        }
        this.random = random;
    }

    /** 生成一张新角色卡；剧本为空时物品为空。 */
    public CardData generate(Script script) {
        Map<String, Integer> attrs = new LinkedHashMap<String, Integer>();
        for (String attribute : CardData.ATTRIBUTES) {
            int value = 0;
            for (int dice = 0; dice < ATTRIBUTE_DICE; dice++) {
                value += rollFace(random, ATTRIBUTE_FACES);
            }
            attrs.put(attribute, Integer.valueOf(value * ATTRIBUTE_MULTIPLIER));
        }
        List<Profession> professions = Profession.all();
        Profession profession = professions.get(rollFace(random, professions.size()) - 1);
        Map<String, Integer> skills = new LinkedHashMap<String, Integer>(profession.skills());
        int pow = attr(attrs, "POW");
        int con = attr(attrs, "CON");
        int siz = attr(attrs, "SIZ");
        int san = pow;
        int hp = Math.max(1, (con + siz) / HP_DIVISOR);
        int mp = Math.max(1, pow / MP_DIVISOR);
        List<String> items = new ArrayList<String>();
        if (script != null) {
            items.addAll(script.startingItems());
        }
        return new CardData(attrs, skills, san, hp, mp, items, new ArrayList<String>());
    }

    /** 按技能集合反查职业名，查不到返回「冒险者」。 */
    public static String professionLabel(CardData card) {
        if (card == null) {
            return Profession.UNKNOWN_LABEL;
        }
        Profession profession = Profession.fromSkills(card.skills());
        return profession == null ? Profession.UNKNOWN_LABEL : profession.label();
    }

    /** 掷一颗 {@code sides} 面骰，并校验随机源返回值在面数区间内。 */
    private static int rollFace(RandomSource random, int sides) {
        int face = random.roll(sides);
        if (face < 1 || face > sides) {
            throw new IllegalStateException("随机源返回越界骰面：" + face + "，面数=" + sides);
        }
        return face;
    }

    private static int attr(Map<String, Integer> attrs, String key) {
        Integer value = attrs.get(key);
        return value == null ? 0 : value.intValue();
    }
}
