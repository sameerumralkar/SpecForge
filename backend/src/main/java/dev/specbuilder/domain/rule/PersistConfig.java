package dev.specbuilder.domain.rule;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PersistConfig(
        @NotBlank @Pattern(regexp = "[A-Z][A-Za-z0-9]*", message = "use an UpperCamelCase class name, e.g. OrderEntity")
        String entityName,
        Operation operation,
        String idField) implements RuleConfig {

    public enum Operation { SAVE, UPDATE, DELETE, FIND_BY_ID }

    public PersistConfig {
        operation = operation == null ? Operation.SAVE : operation;
    }

    @Override
    public String summary(SummaryContext ctx) {
        String id = RuleConfig.blank(idField) ? "" : " by " + idField;
        return switch (operation) {
            case SAVE -> "Save " + ctx.current() + " as a new " + entityName + ".";
            case UPDATE -> "Update the stored " + entityName + id + " from " + ctx.current() + ".";
            case DELETE -> "Delete the " + entityName + id + ".";
            case FIND_BY_ID -> "Load the " + entityName + id + " from the database.";
        };
    }
}
