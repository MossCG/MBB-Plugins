package org.moboxlab.mbb.trpg.testutil;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.moboxlab.mbb.trpg.rule.RandomSource;

/**
 * 固定骰面桩：按入队顺序返回骰面。
 *
 * <p>骰面越界或提前耗尽都直接抛错，避免测试脚本与真实掷骰顺序不一致时悄悄通过。</p>
 */
public final class FakeRandom implements RandomSource {

    private final Deque<Integer> faces = new ArrayDeque<Integer>();
    private final List<Integer> requestedSides = new ArrayList<Integer>();

    public FakeRandom(int... faces) {
        enqueue(faces);
    }

    public FakeRandom enqueue(int... values) {
        for (int value : values) {
            faces.add(value);
        }
        return this;
    }

    @Override
    public int roll(int sides) {
        requestedSides.add(sides);
        Integer face = faces.poll();
        if (face == null) {
            throw new IllegalStateException("固定骰面已用尽，请求 " + sides + " 面骰");
        }
        if (face < 1 || face > sides) {
            throw new IllegalStateException("骰面 " + face + " 超出 " + sides + " 面骰范围");
        }
        return face;
    }

    /** 按调用顺序记录的骰子面数。 */
    public List<Integer> requestedSides() {
        return requestedSides;
    }

    public int remaining() {
        return faces.size();
    }
}
