package org.moboxlab.mbb.trpg.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 局内动作的结构化结果：给指令层拼回执用的明细载体。
 *
 * <p>每个内嵌类都带着统一的局面结果（{@link SessionResult}），因此被拒绝时明细为空、
 * 成功时明细里是模板需要的数值。数值一律是已落库后的最终值。</p>
 */
public final class SessionOutcomes {

    private SessionOutcomes() {
    }

    /** 技能检定结果。 */
    public static final class Check {

        private final SessionResult result;
        private final long rollerUserId;
        private final String skillLabel;
        private final int rolled;
        private final int target;
        private final int degree;
        private final String note;
        private final String triggerHint;

        public Check(SessionResult result, long rollerUserId, String skillLabel, int rolled,
                     int target, int degree, String note, String triggerHint) {
            this.result = result;
            this.rollerUserId = rollerUserId;
            this.skillLabel = skillLabel == null ? "" : skillLabel;
            this.rolled = rolled;
            this.target = target;
            this.degree = degree;
            this.note = note == null ? "" : note;
            this.triggerHint = triggerHint == null ? "" : triggerHint;
        }

        public SessionResult result() {
            return result;
        }

        public long rollerUserId() {
            return rollerUserId;
        }

        public String skillLabel() {
            return skillLabel;
        }

        public int rolled() {
            return rolled;
        }

        public int target() {
            return target;
        }

        public int degree() {
            return degree;
        }

        public String note() {
            return note;
        }

        public String triggerHint() {
            return triggerHint;
        }

        /** 是否成功（常规成功及以上）。 */
        public boolean success() {
            return degree >= org.moboxlab.mbb.trpg.rule.CheckResult.DEGREE_REGULAR;
        }
    }

    /** 理智检定结果。 */
    public static final class San {

        private final SessionResult result;
        private final long rollerUserId;
        private final int rolled;
        private final int target;
        private final int degree;
        private final int sanBefore;
        private final int sanAfter;
        private final int loss;
        private final boolean maddened;
        private final boolean permanent;
        private final long madnessUntil;
        private final String triggerHint;

        public San(SessionResult result, long rollerUserId, int rolled, int target, int degree,
                   int sanBefore, int sanAfter, int loss, boolean maddened, boolean permanent,
                   long madnessUntil, String triggerHint) {
            this.result = result;
            this.rollerUserId = rollerUserId;
            this.rolled = rolled;
            this.target = target;
            this.degree = degree;
            this.sanBefore = sanBefore;
            this.sanAfter = sanAfter;
            this.loss = loss;
            this.maddened = maddened;
            this.permanent = permanent;
            this.madnessUntil = madnessUntil;
            this.triggerHint = triggerHint == null ? "" : triggerHint;
        }

        public SessionResult result() {
            return result;
        }

        public long rollerUserId() {
            return rollerUserId;
        }

        public int rolled() {
            return rolled;
        }

        public int target() {
            return target;
        }

        public int degree() {
            return degree;
        }

        public int sanBefore() {
            return sanBefore;
        }

        public int sanAfter() {
            return sanAfter;
        }

        public int loss() {
            return loss;
        }

        /** 是否触发临时性疯狂。 */
        public boolean maddened() {
            return maddened;
        }

        /** 是否理智归零、永久疯狂退场。 */
        public boolean permanent() {
            return permanent;
        }

        /** 临时性疯狂的结束时间；未触发时为 0。 */
        public long madnessUntil() {
            return madnessUntil;
        }

        /** 剧本触发器提示；没有则为空串。 */
        public String triggerHint() {
            return triggerHint;
        }
    }

    /** 对抗检定结果。 */
    public static final class Opposed {

        private final SessionResult result;
        private final long leftUserId;
        private final long rightUserId;
        private final String leftLabel;
        private final String rightLabel;
        private final int leftRolled;
        private final int rightRolled;
        private final int leftTarget;
        private final int rightTarget;
        private final int leftDegree;
        private final int rightDegree;
        private final long winnerUserId;
        private final String tieBreakLabel;
        private final String triggerHint;

        public Opposed(SessionResult result, long leftUserId, long rightUserId, String leftLabel,
                       String rightLabel, int leftRolled, int rightRolled, int leftTarget,
                       int rightTarget, int leftDegree, int rightDegree, long winnerUserId,
                       String tieBreakLabel, String triggerHint) {
            this.result = result;
            this.leftUserId = leftUserId;
            this.rightUserId = rightUserId;
            this.leftLabel = leftLabel == null ? "" : leftLabel;
            this.rightLabel = rightLabel == null ? "" : rightLabel;
            this.leftRolled = leftRolled;
            this.rightRolled = rightRolled;
            this.leftTarget = leftTarget;
            this.rightTarget = rightTarget;
            this.leftDegree = leftDegree;
            this.rightDegree = rightDegree;
            this.winnerUserId = winnerUserId;
            this.tieBreakLabel = tieBreakLabel == null ? "" : tieBreakLabel;
            this.triggerHint = triggerHint == null ? "" : triggerHint;
        }

        public SessionResult result() {
            return result;
        }

        public long leftUserId() {
            return leftUserId;
        }

        public long rightUserId() {
            return rightUserId;
        }

        public String leftLabel() {
            return leftLabel;
        }

        public String rightLabel() {
            return rightLabel;
        }

        public int leftRolled() {
            return leftRolled;
        }

        public int rightRolled() {
            return rightRolled;
        }

        public int leftTarget() {
            return leftTarget;
        }

        public int rightTarget() {
            return rightTarget;
        }

        public int leftDegree() {
            return leftDegree;
        }

        public int rightDegree() {
            return rightDegree;
        }

        /** 胜者用户号；0 表示无胜者。 */
        public long winnerUserId() {
            return winnerUserId;
        }

        public String tieBreakLabel() {
            return tieBreakLabel;
        }

        public String triggerHint() {
            return triggerHint;
        }

        /** 是否分出胜负。 */
        public boolean decided() {
            return winnerUserId > 0;
        }
    }

    /** 技能调整结果。 */
    public static final class Adjust {

        private final SessionResult result;
        private final long targetUserId;
        private final String skill;
        private final int from;
        private final int to;

        public Adjust(SessionResult result, long targetUserId, String skill, int from, int to) {
            this.result = result;
            this.targetUserId = targetUserId;
            this.skill = skill == null ? "" : skill;
            this.from = from;
            this.to = to;
        }

        public SessionResult result() {
            return result;
        }

        public long targetUserId() {
            return targetUserId;
        }

        public String skill() {
            return skill;
        }

        public int from() {
            return from;
        }

        public int to() {
            return to;
        }
    }

    /** 场景推进结果。 */
    public static final class Scene {

        private final SessionResult result;
        private final int sceneIndex;
        private final String sceneTitle;
        private final List<String> newClueKeys;
        private final String triggerHint;

        public Scene(SessionResult result, int sceneIndex, String sceneTitle,
                     List<String> newClueKeys, String triggerHint) {
            this.result = result;
            this.sceneIndex = sceneIndex;
            this.sceneTitle = sceneTitle == null ? "" : sceneTitle;
            this.newClueKeys = newClueKeys == null
                    ? Collections.<String>emptyList()
                    : Collections.unmodifiableList(new ArrayList<String>(newClueKeys));
            this.triggerHint = triggerHint == null ? "" : triggerHint;
        }

        public SessionResult result() {
            return result;
        }

        public int sceneIndex() {
            return sceneIndex;
        }

        public String sceneTitle() {
            return sceneTitle;
        }

        /** 本次进入场景新解锁的线索 key。 */
        public List<String> newClueKeys() {
            return newClueKeys;
        }

        public String triggerHint() {
            return triggerHint;
        }
    }
}
