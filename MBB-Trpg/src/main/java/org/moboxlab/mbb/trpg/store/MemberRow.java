package org.moboxlab.mbb.trpg.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * plugin_mbb_trpg_member 行对象（不可变）。
 *
 * <p>cardJSON 与 successSkills 在行对象层完成解析，解析失败按空值处理并把告警
 * 挂在 {@link #warnings()} 上，由调用方统一打印。</p>
 */
public final class MemberRow {

    private final long id;
    private final long sessionId;
    private final long userId;
    private final String cardJson;
    private final CardData card;
    private final String successSkillsJson;
    private final List<String> successSkills;
    private final long joinedAt;
    private final long leftAt;
    private final boolean takenOver;
    private final long madnessUntil;
    private final long createTime;
    private final long updateTime;
    private final List<String> warnings;

    private MemberRow(Builder builder) {
        this.id = builder.id;
        this.sessionId = builder.sessionId;
        this.userId = builder.userId;
        this.cardJson = builder.cardJson == null ? "{}" : builder.cardJson;
        this.card = CardData.fromJson(this.cardJson);
        List<String> parseWarnings = new ArrayList<String>(builder.warnings);
        parseWarnings.addAll(card.warnings());
        this.successSkillsJson = builder.successSkillsJson == null ? "[]" : builder.successSkillsJson;
        this.successSkills = parseSkills(this.successSkillsJson, parseWarnings);
        this.joinedAt = builder.joinedAt;
        this.leftAt = builder.leftAt;
        this.takenOver = builder.takenOver;
        this.madnessUntil = builder.madnessUntil;
        this.createTime = builder.createTime;
        this.updateTime = builder.updateTime;
        this.warnings = Collections.unmodifiableList(parseWarnings);
    }

    private static List<String> parseSkills(String json, List<String> warnings) {
        List<String> result = new ArrayList<String>();
        try {
            List<String> parsed = com.alibaba.fastjson.JSON.parseArray(json, String.class);
            if (parsed != null) {
                result.addAll(parsed);
            }
        } catch (RuntimeException e) {
            warnings.add("成员成功技能 JSON 解析失败，按空值处理：" + e.getMessage());
        }
        return Collections.unmodifiableList(result);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 以当前行的全部字段为起点复制一个构造器，便于只改其中一两个字段。 */
    public Builder toBuilder() {
        return new Builder()
                .id(id)
                .sessionId(sessionId)
                .userId(userId)
                .cardJson(cardJson)
                .successSkillsJson(successSkillsJson)
                .joinedAt(joinedAt)
                .leftAt(leftAt)
                .takenOver(takenOver)
                .madnessUntil(madnessUntil)
                .createTime(createTime)
                .updateTime(updateTime)
                .warnings(warnings);
    }

    public long id() {
        return id;
    }

    public long sessionId() {
        return sessionId;
    }

    public long userId() {
        return userId;
    }

    public String cardJson() {
        return cardJson;
    }

    public CardData card() {
        return card;
    }

    public String successSkillsJson() {
        return successSkillsJson;
    }

    public List<String> successSkills() {
        return successSkills;
    }

    public long joinedAt() {
        return joinedAt;
    }

    public long leftAt() {
        return leftAt;
    }

    public boolean takenOver() {
        return takenOver;
    }

    public long madnessUntil() {
        return madnessUntil;
    }

    public long createTime() {
        return createTime;
    }

    public long updateTime() {
        return updateTime;
    }

    /** 解析期告警。 */
    public List<String> warnings() {
        return warnings;
    }

    /** 行构造器。 */
    public static final class Builder {

        private long id;
        private long sessionId;
        private long userId;
        private String cardJson = "{}";
        private String successSkillsJson = "[]";
        private long joinedAt;
        private long leftAt;
        private boolean takenOver;
        private long madnessUntil;
        private long createTime;
        private long updateTime;
        private List<String> warnings = new ArrayList<String>();

        public Builder id(long value) {
            this.id = value;
            return this;
        }

        public Builder sessionId(long value) {
            this.sessionId = value;
            return this;
        }

        public Builder userId(long value) {
            this.userId = value;
            return this;
        }

        public Builder cardJson(String value) {
            this.cardJson = value;
            return this;
        }

        public Builder card(CardData value) {
            this.cardJson = value == null ? "{}" : value.toJson();
            return this;
        }

        public Builder successSkillsJson(String value) {
            this.successSkillsJson = value;
            return this;
        }

        public Builder successSkills(List<String> value) {
            this.successSkillsJson = com.alibaba.fastjson.JSON.toJSONString(
                    value == null ? new ArrayList<String>() : value);
            return this;
        }

        public Builder joinedAt(long value) {
            this.joinedAt = value;
            return this;
        }

        public Builder leftAt(long value) {
            this.leftAt = value;
            return this;
        }

        public Builder takenOver(boolean value) {
            this.takenOver = value;
            return this;
        }

        public Builder madnessUntil(long value) {
            this.madnessUntil = value;
            return this;
        }

        public Builder createTime(long value) {
            this.createTime = value;
            return this;
        }

        public Builder updateTime(long value) {
            this.updateTime = value;
            return this;
        }

        public Builder warnings(List<String> value) {
            this.warnings = value == null ? new ArrayList<String>() : value;
            return this;
        }

        public MemberRow build() {
            return new MemberRow(this);
        }
    }
}
