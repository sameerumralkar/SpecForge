package dev.specbuilder.api.dto;

import dev.specbuilder.domain.ImplementationStyle;

import java.time.Instant;
import java.util.UUID;

public record ProjectView(
        UUID id,
        String name,
        String basePackage,
        String version,
        String description,
        ImplementationStyle defaultImplementationStyle,
        boolean allowCircularReferences,
        long payloadCount,
        long endpointCount,
        Instant createdAt,
        Instant updatedAt) {
}
