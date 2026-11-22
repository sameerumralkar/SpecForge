package dev.specbuilder.api.dto;

import dev.specbuilder.domain.rule.RuleType;
import tools.jackson.databind.JsonNode;

/** Create or replace a rule. {@code position} (0-based) applies on create only; by default the rule goes last. */
public record RuleRequest(RuleType type, String name, String condition, JsonNode config, Integer position) {
}
