package org.moboxlab.mbb.trpg.ai;

import org.moboxlab.mbb.trpg.config.TrpgConfig;

import java.util.List;

/** 叙述校验：解析模型输出、拼接建议行、识别截断；拒绝原因保持中文，供日志与提示使用。 */
final class AiNarrationValidator {

    private static final String FINISH_LENGTH = "length";

    private final TrpgConfig config;
    private final AiEngineLog log;

    AiNarrationValidator(TrpgConfig config, AiEngineLog log) {
        this.config = config;
        this.log = log;
    }

    /** 校验一次模型输出；解析异常与拒绝都由本方法折成不通过结果。 */
    AiNarrationValidation validate(String content, String finishReason) {
        if (content == null) {
            return AiNarrationValidation.invalid("empty");
        }
        AiNarrationParser.ParseResult parsed;
        try {
            parsed = AiNarrationParser.parse(content, Math.max(1, config.aiNarrationMaxChars()));
        } catch (RuntimeException error) {
            log.warn("AI 叙述解析异常：" + error);
            return AiNarrationValidation.invalid("parse_error");
        }
        if (parsed == null || !parsed.valid()) {
            String reason = parsed == null ? "parse_error" : parsed.rejectReason();
            return AiNarrationValidation.invalid(reason == null ? "invalid" : reason);
        }
        String text = parsed.narration();
        if (text == null || text.trim().isEmpty()) {
            return AiNarrationValidation.invalid("empty");
        }
        List<String> suggestions = parsed.suggestions();
        if (suggestions != null && !suggestions.isEmpty()) {
            StringBuilder builder = new StringBuilder(text);
            for (String suggestion : suggestions) {
                if (suggestion == null || suggestion.trim().isEmpty()) {
                    continue;
                }
                builder.append('\n').append(formatSuggestion(suggestion));
            }
            text = builder.toString();
        }
        boolean truncated = parsed.truncated() || FINISH_LENGTH.equalsIgnoreCase(finishReason);
        return AiNarrationValidation.ok(text, truncated);
    }

    private static String formatSuggestion(String suggestion) {
        String text = suggestion.trim();
        if (text.startsWith("建议")) {
            return text;
        }
        return "建议：" + text;
    }
}
