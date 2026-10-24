package org.moboxlab.mbb.trpg.present;

import org.moboxlab.moboxbot.API.Util.ImageUtil;

import java.util.List;

/** 生产渲染器：只调主程序 {@code ImageUtil}，不引入 AWT 自绘。 */
public final class ImageUtilRenderer implements TrpgImageRenderer {

    @Override
    public byte[] render(String title, List<String> lines) {
        return ImageUtil.renderText(title, lines);
    }
}
