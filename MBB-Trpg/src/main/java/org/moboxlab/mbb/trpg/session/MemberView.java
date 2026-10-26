package org.moboxlab.mbb.trpg.session;

import org.moboxlab.mbb.trpg.store.CardData;
import org.moboxlab.mbb.trpg.store.MemberRow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 局内成员视图（不可变）。
 *
 * <p>成员级状态与局状态正交：局内成员（未离场）、离场成员（保留卡与局籍）、
 * 旁观者（未加入，不构成本对象）。</p>
 */
public final class MemberView {

    private final long userId;
    private final long joinedAt;
    private final long leftAt;
    private final boolean takenOver;
    private final long madnessUntil;
    private final CardData card;
    private final List<String> successSkills;

    public MemberView(long userId, long joinedAt, long leftAt, boolean takenOver,
                      long madnessUntil, CardData card, List<String> successSkills) {
        this.userId = userId;
        this.joinedAt = joinedAt;
        this.leftAt = leftAt;
        this.takenOver = takenOver;
        this.madnessUntil = madnessUntil;
        this.card = card == null ? CardData.empty() : card;
        this.successSkills = Collections.unmodifiableList(successSkills == null
                ? new ArrayList<String>() : new ArrayList<String>(successSkills));
    }

    /** 由存储行构造视图。 */
    public static MemberView of(MemberRow row) {
        if (row == null) {
            throw new IllegalArgumentException("成员行不能为空");
        }
        return new MemberView(row.userId(), row.joinedAt(), row.leftAt(), row.takenOver(),
                row.madnessUntil(), row.card(), row.successSkills());
    }

    public long userId() {
        return userId;
    }

    public long joinedAt() {
        return joinedAt;
    }

    public long leftAt() {
        return leftAt;
    }

    /** 是否局内成员（未离场）。 */
    public boolean inSession() {
        return leftAt == 0L;
    }

    public boolean takenOver() {
        return takenOver;
    }

    public long madnessUntil() {
        return madnessUntil;
    }

    public CardData card() {
        return card;
    }

    /** 本局成功用过的技能，用于结束成长结算。 */
    public List<String> successSkills() {
        return successSkills;
    }
}
