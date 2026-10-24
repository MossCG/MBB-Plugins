package org.moboxlab.mbb.trpg.present;

import java.util.List;

/** 图片渲染端口：生产实现调主程序 {@code ImageUtil.renderText}，测试可打桩返回 null 或抛异常。 */
public interface TrpgImageRenderer {

    /** 渲染一张文字卡；返回 null 表示渲染不可用（调用方回退纯文本）。 */
    byte[] render(String title, List<String> lines);
}
