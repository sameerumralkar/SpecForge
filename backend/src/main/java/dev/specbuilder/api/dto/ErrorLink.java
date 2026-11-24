package dev.specbuilder.api.dto;

import dev.specbuilder.domain.rule.RuleType;

import java.util.UUID;

/**
 * Where an error code is used. FIELD: a field of a payload ({@code payloadId} + {@code field}); RULE: a step of an
 * endpoint's rule chain ({@code ruleId}); CHECK: one check of a Validate rule that picks the code itself.
 * On requests only the ids and the field name are read; the names are filled in on responses.
 */
public record ErrorLink(Kind kind, UUID payloadId, String payloadName, String field, UUID endpointId, String endpointName,
                        UUID ruleId, String ruleName, RuleType ruleType, String detail) {

    public enum Kind { FIELD, RULE, CHECK }

    public static ErrorLink field(UUID payloadId, String field) {
        return new ErrorLink(Kind.FIELD, payloadId, null, field, null, null, null, null, null, null);
    }

    public static ErrorLink rule(UUID ruleId) {
        return new ErrorLink(Kind.RULE, null, null, null, null, null, ruleId, null, null, null);
    }
}
