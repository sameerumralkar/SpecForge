package dev.specbuilder.domain.rule;

import dev.specbuilder.validation.ValidationErrors;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Retry, fallback and dead-letter behaviour, for the whole chain or for one named step.
 * Errors are given as categories so generated code can map them to real exception classes.
 */
public record ErrorHandlingConfig(
        List<ErrorCategory> on,
        String targetRule,
        @Valid Retry retry,
        @Valid Fallback fallback,
        @Pattern(regexp = "[A-Za-z0-9._/-]+", message = "use letters, digits, '.', '_', '-' or '/'")
        String deadLetterDestination) implements RuleConfig {

    public enum ErrorCategory { ANY, VALIDATION, DOWNSTREAM_UNAVAILABLE, TIMEOUT, PERSISTENCE, MESSAGING }

    public enum FallbackType { ERROR_RESPONSE, DEFAULT_RESPONSE }

    public ErrorHandlingConfig {
        on = RuleConfig.clean(on).isEmpty() ? List.of(ErrorCategory.ANY) : RuleConfig.clean(on).stream().distinct().toList();
        targetRule = RuleConfig.blank(targetRule) ? null : targetRule.strip();
    }

    public record Retry(
            @Min(1) @Max(10) Integer maxAttempts,
            @Min(0) @Max(60000) Long backoffMs,
            @DecimalMin("1.0") @DecimalMax("10.0") Double multiplier) {

        public Retry {
            maxAttempts = maxAttempts == null ? 3 : maxAttempts;
            backoffMs = backoffMs == null ? 500L : backoffMs;
            multiplier = multiplier == null ? 2.0 : multiplier;
        }
    }

    public record Fallback(@NotNull FallbackType type, @Min(100) @Max(599) Integer httpStatus, String message) {
    }

    @Override
    public void validate(ValidationErrors errors, String prefix) {
        if (retry == null && fallback == null && RuleConfig.blank(deadLetterDestination)) {
            errors.add(prefix.isEmpty() ? "config" : prefix.substring(0, prefix.length() - 1),
                    "set a retry, a fallback or a dead-letter destination");
        }
    }

    @Override
    public String summary(SummaryContext ctx) {
        String what = on.contains(ErrorCategory.ANY) ? "any error"
                : String.join(" or ", on.stream().map(c -> c.name().toLowerCase(Locale.ROOT).replace('_', ' ')).toList()) + " errors";
        String scope = targetRule == null ? "" : " in '" + targetRule + "'";
        List<String> actions = new ArrayList<>();
        if (retry != null) {
            actions.add("retry up to " + retry.maxAttempts() + " times (" + retry.backoffMs() + " ms backoff ×" + trim(retry.multiplier()) + ")");
        }
        if (fallback != null && fallback.type() != null) {
            actions.add(switch (fallback.type()) {
                case ERROR_RESPONSE -> "return " + (fallback.httpStatus() == null ? "an error" : fallback.httpStatus())
                        + (RuleConfig.blank(fallback.message()) ? "" : " \"" + fallback.message() + "\"");
                case DEFAULT_RESPONSE -> "return a default response";
            });
        }
        if (!RuleConfig.blank(deadLetterDestination)) {
            actions.add("send the message to dead-letter " + deadLetterDestination);
        }
        return "On " + what + scope + ": " + String.join(", then ", actions) + ".";
    }

    private static String trim(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
