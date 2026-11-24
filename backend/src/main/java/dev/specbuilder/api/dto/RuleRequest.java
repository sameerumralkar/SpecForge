package dev.specbuilder.api.dto;

import dev.specbuilder.domain.rule.RuleType;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;

/**
 * Create or replace a rule. {@code position} (0-based) applies on create only; by default the rule goes last.
 * {@code errorCodeIds} are the codes the step can fail with; left out (null) on an update, they stay as they are.
 */
public record RuleRequest(RuleType type, String name, String condition, JsonNode config, Integer position, List<UUID> errorCodeIds) {

    public RuleRequest(RuleType type, String name, String condition, JsonNode config, Integer position) {
        this(type, name, condition, config, position, null);
    }
}
