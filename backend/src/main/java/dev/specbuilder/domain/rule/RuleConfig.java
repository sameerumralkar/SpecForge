package dev.specbuilder.domain.rule;

import dev.specbuilder.expression.ExpressionParser;
import dev.specbuilder.validation.ValidationErrors;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Type-specific settings of a processing rule. Bean Validation annotations on the records cover
 * shape checks; {@link #validate} adds the checks that need more than one property.
 */
public sealed interface RuleConfig
        permits ValidateConfig, TransformConfig, EnrichConfig, ConditionalRouteConfig,
                PersistConfig, PublishEventConfig, SplitAggregateConfig, ErrorHandlingConfig {

    /** One plain-English sentence describing what this step does. */
    String summary(SummaryContext ctx);

    /** Payloads this config points at, so payload deletes can find every use. */
    default Set<UUID> payloadRefs() {
        return Set.of();
    }

    /** Error codes this config picks itself (beyond the ones linked to the rule), so they can be checked and exported. */
    default Set<UUID> errorCodeRefs() {
        return Set.of();
    }

    default void validate(ValidationErrors errors, String prefix) {
    }

    static <T> List<T> clean(List<T> list) {
        return list == null ? List.of() : list.stream().filter(Objects::nonNull).toList();
    }

    /** Adds an error if {@code expression} is not valid shared expression syntax. */
    static void checkSyntax(ValidationErrors errors, String field, String expression) {
        String problem = ExpressionParser.problem(expression);
        if (problem != null) {
            errors.add(field, problem);
        }
    }

    static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
