package org.moboxlab.mbb.roleplay;

/**
 * 一份可注入执行层的资料
 *
 * 每份资料有自己的优先级和字符预算，由 RoleplayMaterialBudget 统一分配，
 * 避免出现某一份资料把整条提示词撑爆的情况。
 */
public class RoleplayMaterial {

    public interface Loader {
        String load();
    }

    private final String id;
    private final boolean alwaysOn;
    private final int priority;
    private final int charBudget;
    private final Loader loader;

    public RoleplayMaterial(String id,boolean alwaysOn,int priority,int charBudget,Loader loader) {
        this.id = id;
        this.alwaysOn = alwaysOn;
        this.priority = priority;
        this.charBudget = charBudget;
        this.loader = loader;
    }

    public String id() {
        return id;
    }

    public boolean alwaysOn() {
        return alwaysOn;
    }

    public int priority() {
        return priority;
    }

    public int charBudget() {
        return charBudget;
    }

    public String load() {
        String text = loader == null ? "" : loader.load();
        return text == null ? "" : text;
    }
}
