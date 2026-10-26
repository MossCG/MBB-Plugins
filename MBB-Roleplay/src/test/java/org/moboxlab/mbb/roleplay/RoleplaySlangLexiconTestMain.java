package org.moboxlab.mbb.roleplay;

/**
 * 网络用语按消息命中与语境约束测试
 */
public class RoleplaySlangLexiconTestMain {
    public static void main(String[] args) {
        String wc = RoleplaySlangLexicon.promptFor("wc这题也太怪了");
        if (!wc.contains("卧槽") || !wc.contains("极低频")) {
            throw new AssertionError("wc 应注入含义和低频约束");
        }
        String hyw = RoleplaySlangLexicon.promptFor("hyw");
        if (!hyw.contains("何意味") || !hyw.contains("不要硬把梗翻成字面")) {
            throw new AssertionError("hyw 应作为没看懂梗时的短问来理解");
        }
        String box = RoleplaySlangLexicon.promptFor("别开盒");
        if (!box.contains("隐私风险") || !box.contains("不参与起哄")) {
            throw new AssertionError("盒/开盒应作为隐私风险词处理");
        }
        if (!RoleplaySlangLexicon.promptFor("普通聊天内容").isEmpty()) {
            throw new AssertionError("没有命中网络用语时不应注入词表");
        }
        if (!RoleplaySlangLexicon.promptFor("我想买个盲盒").isEmpty()) {
            throw new AssertionError("普通盲盒不应误判为开盒");
        }
        if (!RoleplaySlangLexicon.promptFor("swc 是内部编号").isEmpty()) {
            throw new AssertionError("英文缩写应做边界匹配，不能从 swc 中命中 wc");
        }
    }
}
