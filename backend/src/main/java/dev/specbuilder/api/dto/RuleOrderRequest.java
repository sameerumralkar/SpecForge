package dev.specbuilder.api.dto;

import java.util.List;
import java.util.UUID;

/** Every rule id of the endpoint, in the new order. */
public record RuleOrderRequest(List<UUID> ruleIds) {
}
