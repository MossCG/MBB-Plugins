package org.moboxlab.mbb.trpg.ai;

/** 叙述校验结果：通过时带正文与截断标记，不通过时带中文拒绝原因。 */
final class AiNarrationValidation {

    final boolean valid;
    final String text;
    final boolean truncated;
    final String rejectReason;

    private AiNarrationValidation(boolean valid, String text, boolean truncated,
                                  String rejectReason) {
        this.valid = valid;
        this.text = text;
        this.truncated = truncated;
        this.rejectReason = rejectReason;
    }

    static AiNarrationValidation ok(String text, boolean truncated) {
        return new AiNarrationValidation(true, text, truncated, "");
    }

    static AiNarrationValidation invalid(String rejectReason) {
        return new AiNarrationValidation(false, "", false, rejectReason);
    }
}
