package org.moboxlab.mbb.roleplay;

/**
 * 笑声语料过滤回归测试
 */
public class RoleplaySpeechCorpusServiceTestMain {
    public static void main(String[] args) {
        if (!RoleplaySpeechCorpusService.isLaughterHeavy("啊哈哈，反应不错！")
                || !RoleplaySpeechCorpusService.isLaughterHeavy("啊哈哈哈哈！")) {
            throw new AssertionError("以笑声为主的语料不应作为普通语气示例注入");
        }
        if (RoleplaySpeechCorpusService.isLaughterHeavy("这样就行了")
                || RoleplaySpeechCorpusService.isLaughterHeavy("刚才真好笑")) {
            throw new AssertionError("普通聊天和正常表达不应被当成纯笑声语料");
        }
    }
}
