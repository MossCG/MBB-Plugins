package org.moboxlab.mbb.roleplay;

/**
 * 自适应消息防抖档位、活跃度、续发、点名和媒体等待回归测试。
 */
public class RoleplayMessageDebounceTestMain {
    public static void main(String[] args) {
        RoleplayConfig config = new RoleplayConfig();
        if (!"natural".equals(config.messageBatchDebounceMode)
                || config.messageBatchMaxWaitSecond != 8) {
            throw new AssertionError("自适应防抖默认配置不正确");
        }

        long off = RoleplayMessageDebounce.delayMillis(
                "off",1,true,false,false,false,2);
        long fast = RoleplayMessageDebounce.delayMillis(
                "fast",1,true,false,false,false,2);
        long natural = RoleplayMessageDebounce.delayMillis(
                "natural",1,true,false,false,false,2);
        long steady = RoleplayMessageDebounce.delayMillis(
                "steady",1,true,false,false,false,2);
        if (off != 0L || !(fast < natural && natural < steady)) {
            throw new AssertionError("防抖档位必须按 fast < natural < steady 递增，off 必须立即刷新");
        }

        long quiet = RoleplayMessageDebounce.delayMillis(
                "natural",1,false,false,false,false,3);
        if (quiet <= natural) {
            throw new AssertionError("安静群应比活跃群等待更久");
        }

        long sameUser = RoleplayMessageDebounce.delayMillis(
                "natural",2,true,true,false,false,3);
        long speakerChange = RoleplayMessageDebounce.delayMillis(
                "natural",2,true,false,false,false,3);
        if (sameUser <= speakerChange) {
            throw new AssertionError("同一用户续发应比换人时多等一点");
        }

        long direct = RoleplayMessageDebounce.delayMillis(
                "natural",2,false,false,true,false,5);
        if (direct > 800L) {
            throw new AssertionError("直接点名或引用必须使用更短的防抖上限");
        }

        long initialMedia = RoleplayMessageDebounce.delayMillis(
                "natural",1,true,false,false,true,3);
        if (initialMedia <= natural) {
            throw new AssertionError("裸图或纯表情包作为首条消息时应保留识别等待");
        }

        long capped = RoleplayMessageDebounce.delayMillis(
                "steady",1,false,false,false,false,1);
        if (capped > 1000L) {
            throw new AssertionError("等待时间不得超过 messageBatchWindowSecond");
        }
    }
}
