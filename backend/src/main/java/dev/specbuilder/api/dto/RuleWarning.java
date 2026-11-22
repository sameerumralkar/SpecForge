package dev.specbuilder.api.dto;

import java.util.UUID;

/** A problem with how rules fit together (e.g. a field path that does not exist). Saving is still allowed. */
public record RuleWarning(UUID ruleId, String ruleName, String message) {
}
