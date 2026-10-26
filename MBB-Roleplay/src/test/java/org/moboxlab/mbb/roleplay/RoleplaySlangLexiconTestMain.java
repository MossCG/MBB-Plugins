package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 网络用语按消息命中与语境约束测试
 */
public class RoleplaySlangLexiconTestMain {
    public static void main(String[] args) throws Exception {
        String text = new String(Files.readAllBytes(Paths.get(
                "MBB-Roleplay/src/main/resources/network-slangs.json")),StandardCharsets.UTF_8);
        RoleplaySlangLexicon lexicon = RoleplaySlangLexicon.forTesting(JSONObject.parseObject(text));
        if (lexicon.entryCount() < 30) {
            throw new AssertionError("默认网络用语词集条目过少："+lexicon.entryCount());
        }
        String wc = lexicon.promptFor("wc这题也太怪了");
        if (!wc.contains("卧槽") || !wc.contains("极低频")) {
            throw new AssertionError("wc 应注入含义和低频约束");
        }
        String hyw = lexicon.promptFor("hyw");
        if (!hyw.contains("何意味") || !hyw.contains("不要硬把梗翻成字面")) {
            throw new AssertionError("hyw 应作为没看懂梗时的短问来理解");
        }
        String box = lexicon.promptFor("别开盒");
        if (!box.contains("隐私风险") || !box.contains("不参与起哄")) {
            throw new AssertionError("盒/开盒应作为隐私风险词处理");
        }
        if (!lexicon.promptFor("普通聊天内容").isEmpty()) {
            throw new AssertionError("没有命中网络用语时不应注入词表");
        }
        if (!lexicon.promptFor("我想买个盲盒").isEmpty()) {
            throw new AssertionError("普通盲盒不应误判为开盒");
        }
        if (!lexicon.promptFor("swc 是内部编号").isEmpty()) {
            throw new AssertionError("英文缩写应做边界匹配，不能从 swc 中命中 wc");
        }
        if (lexicon.search("笑",10).isEmpty()) {
            throw new AssertionError("slang_search 应能按含义检索词条");
        }
    }
}
