package dev.specbuilder.api.dto;

import dev.specbuilder.domain.rule.RuleType;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

public record RuleView(
        UUID id,
        UUID endpointId,
        int position,
        RuleType type,
        String typeLabel,
        String name,
        String condition,
        JsonNode config,
        String summary) {
}
