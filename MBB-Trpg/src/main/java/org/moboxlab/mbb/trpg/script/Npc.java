package org.moboxlab.mbb.trpg.script;

/** 剧本 NPC（不可变）；secret 只给 KP 与 AI 上下文，不得进入玩家可见回复。 */
public final class Npc {

    private final String id;
    private final String name;
    private final String role;
    private final String publicText;
    private final String secret;

    public Npc(String id, String name, String role, String publicText, String secret) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
        this.role = role == null ? "" : role;
        this.publicText = publicText == null ? "" : publicText;
        this.secret = secret == null ? "" : secret;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String role() {
        return role;
    }

    /** 可对玩家公开的信息。 */
    public String publicText() {
        return publicText;
    }

    /** 只给 KP 与 AI 的秘密；没有时为空串。 */
    public String secret() {
        return secret;
    }
}
