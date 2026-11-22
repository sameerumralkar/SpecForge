package dev.specbuilder.domain.rule;

import dev.specbuilder.validation.ValidationErrors;
import jakarta.validation.constraints.NotBlank;

/** Processes each element of a collection field, then optionally combines the results. */
public record SplitAggregateConfig(
        @NotBlank String collectionField,
        Boolean parallel,
        Aggregation aggregation,
        String sumField) implements RuleConfig {

    public enum Aggregation { COLLECT_LIST, SUM, COUNT, NONE }

    public SplitAggregateConfig {
        parallel = parallel != null && parallel;
        aggregation = aggregation == null ? Aggregation.COLLECT_LIST : aggregation;
    }

    @Override
    public void validate(ValidationErrors errors, String prefix) {
        if (aggregation == Aggregation.SUM && RuleConfig.blank(sumField)) {
            errors.add(prefix + "sumField", "a SUM aggregation needs the item field to add up");
        }
    }

    @Override
    public String summary(SummaryContext ctx) {
        String each = "Split " + collectionField + " and process each item" + (parallel ? " in parallel" : "");
        String agg = switch (aggregation) {
            case COLLECT_LIST -> ", then collect the results into a list";
            case SUM -> ", then add up " + sumField;
            case COUNT -> ", then count the results";
            case NONE -> "";
        };
        return each + agg + ".";
    }
}
