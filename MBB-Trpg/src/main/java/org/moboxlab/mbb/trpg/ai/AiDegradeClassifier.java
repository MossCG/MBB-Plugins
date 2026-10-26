package org.moboxlab.mbb.trpg.ai;

import java.util.Locale;

/**
 * 失败分类（纯函数）：判定失败类型是否计入连续失败，并换算成降级原因码。
 *
 * <p>只做字符串判定，不读运行状态、不打日志、不改失败计数；是否记账由调用方决定。</p>
 */
final class AiDegradeClassifier {

    private AiDegradeClassifier() {
    }

    /** 该失败类型是否计入连续失败：忙、超时、网络与 HTTP 类计入；停用、参数错误与中断不计。 */
    static boolean countsAsFailure(String errorType) {
        if (errorType == null || errorType.isEmpty()) {
            return false;
        }
        String lower = errorType.toLowerCase(Locale.ROOT);
        return AiErrorTypes.BUSY.equals(lower)
                || AiErrorTypes.TIMEOUT.equals(lower)
                || AiErrorTypes.NETWORK.equals(lower)
                || lower.startsWith(AiErrorTypes.HTTP_PREFIX);
    }

    /**
     * 失败类型换算成审计与提示用的降级原因码。
     *
     * @param fallbackReason 未知失败类型统一折成的降级原因码，由调用方给出（通常是 unavailable）
     */
    static String degradeReason(String errorType, String fallbackReason) {
        if (errorType == null || errorType.isEmpty()) {
            return fallbackReason;
        }
        String lower = errorType.toLowerCase(Locale.ROOT);
        if (AiErrorTypes.BUSY.equals(lower)
                || AiErrorTypes.TIMEOUT.equals(lower)
                || AiErrorTypes.NETWORK.equals(lower)
                || AiErrorTypes.INTERRUPTED.equals(lower)
                || lower.startsWith(AiErrorTypes.HTTP_PREFIX)) {
            return lower;
        }
        return fallbackReason;
    }
}
