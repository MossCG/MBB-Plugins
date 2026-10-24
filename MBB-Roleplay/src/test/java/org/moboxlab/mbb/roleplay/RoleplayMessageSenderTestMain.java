package org.moboxlab.mbb.roleplay;

import java.util.List;

/**
 * 回复断句回归测试入口
 *
 * 不引入测试框架，构建后由 javac 直接编译并按退出码判断结果。
 */
public class RoleplayMessageSenderTestMain {
    public static void main(String[] args) {
        String punctuation = "。！？!?；;，、：,:～~…";
        assertFirstSegmentEndsWith(
                "这次就这样吧。......下次别这么做了，这次真的下次别这么做了，记住了吗",
                "。",punctuation);
        assertFirstSegmentEndsWith(
                "前面先这样说？后面还有很多很多内容需要继续讲下去，不能一直挤在同一段里",
                "？",punctuation);
        assertFirstSegmentEndsWith(
                "前面没有句末标点但是这里有一个逗号，后面还有很多很多内容需要继续说下去",
                "，",punctuation,true);
    }

    private static void assertFirstSegmentEndsWith(String text,String expected,
                                                   String punctuation) {
        assertFirstSegmentEndsWith(text,expected,punctuation,false);
    }

    private static void assertFirstSegmentEndsWith(String text,String expected,
                                                   String punctuation,boolean weakTrimmed) {
        List<String> segments = RoleplayMessageSender.splitReply(text,30,punctuation);
        if (segments.size() < 2) throw new AssertionError("应当拆成至少两段："+segments);
        String first = segments.get(0);
        if (weakTrimmed) {
            if (first.endsWith("，") || first.endsWith(",")) {
                throw new AssertionError("弱标点分段不应保留句尾逗号："+first);
            }
            return;
        }
        if (!first.endsWith(expected)) {
            throw new AssertionError("首段应以 "+expected+" 结束，实际="+first);
        }
    }
}
