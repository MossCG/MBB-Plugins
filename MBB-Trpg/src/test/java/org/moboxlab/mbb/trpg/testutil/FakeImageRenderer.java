package org.moboxlab.mbb.trpg.testutil;

import org.moboxlab.mbb.trpg.present.TrpgImageRenderer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 图片渲染端口假件：记录每次渲染的标题与行，可配置返回 null 或抛异常。 */
public final class FakeImageRenderer implements TrpgImageRenderer {

    /** 一次渲染记录。 */
    public static final class Rendered {
        private final String title;
        private final List<String> lines;

        Rendered(String title, List<String> lines) {
            this.title = title;
            this.lines = Collections.unmodifiableList(new ArrayList<String>(lines));
        }

        public String title() {
            return title;
        }

        public List<String> lines() {
            return lines;
        }

        public String line(int index) {
            return lines.get(index);
        }

        public int lineCount() {
            return lines.size();
        }
    }

    private final List<Rendered> rendered = new ArrayList<Rendered>();
    private byte[] image = new byte[]{1, 2, 3};
    private boolean returnsNull;
    private RuntimeException failure;

    @Override
    public byte[] render(String title, List<String> lines) {
        if (failure != null) {
            throw failure;
        }
        if (returnsNull) {
            return null;
        }
        rendered.add(new Rendered(title, lines == null ? Collections.<String>emptyList() : lines));
        return image;
    }

    /** 设置渲染返回的图片字节。 */
    public void image(byte[] bytes) {
        this.image = bytes;
    }

    /** 设置渲染返回 null（模拟渲染不可用）。 */
    public void returnsNull(boolean value) {
        this.returnsNull = value;
    }

    /** 设置渲染抛出的异常。 */
    public void failWith(RuntimeException error) {
        this.failure = error;
    }

    /** 全部渲染记录，按调用顺序。 */
    public List<Rendered> rendered() {
        return Collections.unmodifiableList(rendered);
    }

    /** 渲染次数。 */
    public int renderCount() {
        return rendered.size();
    }

    /** 最近一次渲染记录，未渲染过返回 null。 */
    public Rendered last() {
        return rendered.isEmpty() ? null : rendered.get(rendered.size() - 1);
    }

    /** 清空渲染记录，保留开关。 */
    public void clear() {
        rendered.clear();
    }
}
