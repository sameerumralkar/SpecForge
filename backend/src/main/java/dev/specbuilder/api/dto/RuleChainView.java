package dev.specbuilder.api.dto;

import dev.specbuilder.domain.ImplementationStyle;

import java.util.List;
import java.util.UUID;

/** An endpoint's rules in order, a numbered plain-English summary, and any warnings about the chain. */
public record RuleChainView(
        UUID endpointId,
        String endpointName,
        ImplementationStyle effectiveImplementationStyle,
        UUID inputPayloadId,
        String inputPayloadName,
        List<RuleView> rules,
        List<String> summary,
        List<RuleWarning> warnings) {
}
