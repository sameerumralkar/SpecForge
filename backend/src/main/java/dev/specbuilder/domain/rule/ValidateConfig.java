package dev.specbuilder.domain.rule;

import dev.specbuilder.validation.ValidationErrors;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

public record ValidateConfig(
        @NotEmpty(message = "add at least one check") List<@Valid Check> checks,
        @Min(400) @Max(499) Integer failureStatus) implements RuleConfig {

    public ValidateConfig {
        checks = RuleConfig.clean(checks);
        failureStatus = failureStatus == null ? 400 : failureStatus;
    }

    public enum CheckKind { REQUIRED, NOT_BLANK, PATTERN, MIN, MAX, MIN_LENGTH, MAX_LENGTH, CUSTOM }

    /** One check on a field path such as {@code customerId} or {@code items[].quantity}. */
    public record Check(@NotBlank String field, @NotNull CheckKind kind, String value, String message) {
    }

    @Override
    public void validate(ValidationErrors errors, String prefix) {
        for (int i = 0; i < checks.size(); i++) {
            Check c = checks.get(i);
            String at = prefix + "checks[" + i + "].value";
            if (c.kind() == null) {
                continue;
            }
            switch (c.kind()) {
                case PATTERN -> {
                    if (RuleConfig.blank(c.value())) {
                        errors.add(at, "a PATTERN check needs a regular expression");
                    } else {
                        try {
                            Pattern.compile(c.value());
                        } catch (PatternSyntaxException e) {
                            errors.add(at, "not a valid regular expression: " + e.getDescription());
                        }
                    }
                }
                case MIN, MAX -> {
                    if (!isNumber(c.value())) {
                        errors.add(at, "a " + c.kind() + " check needs a number");
                    }
                }
                case MIN_LENGTH, MAX_LENGTH -> {
                    if (!isNonNegativeInt(c.value())) {
                        errors.add(at, "a " + c.kind() + " check needs a whole number of 0 or more");
                    }
                }
                case CUSTOM -> {
                    if (RuleConfig.blank(c.value())) {
                        errors.add(at, "a CUSTOM check needs a condition, e.g. total > 0");
                    } else {
                        RuleConfig.checkSyntax(errors, at, c.value());
                    }
                }
                default -> { }
            }
        }
    }

    @Override
    public String summary(SummaryContext ctx) {
        String list = checks.stream().map(ValidateConfig::describe).collect(Collectors.joining("; "));
        return "Validate " + ctx.current() + ": " + list + ". Reject with " + failureStatus + " if any check fails.";
    }

    private static String describe(Check c) {
        String v = c.value();
        return switch (c.kind()) {
            case REQUIRED -> c.field() + " is required";
            case NOT_BLANK -> c.field() + " is not blank";
            case PATTERN -> c.field() + " matches " + v;
            case MIN -> c.field() + " ≥ " + v;
            case MAX -> c.field() + " ≤ " + v;
            case MIN_LENGTH -> "length of " + c.field() + " ≥ " + v;
            case MAX_LENGTH -> "length of " + c.field() + " ≤ " + v;
            case CUSTOM -> c.field() + " satisfies " + v;
        };
    }

    private static boolean isNumber(String s) {
        try {
            Double.parseDouble(s);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean isNonNegativeInt(String s) {
        try {
            return Integer.parseInt(s) >= 0;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
