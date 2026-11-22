package dev.specbuilder.api.dto;

import dev.specbuilder.domain.ImplementationStyle;

public record ProjectRequest(
        String name,
        String basePackage,
        String version,
        String description,
        ImplementationStyle defaultImplementationStyle,
        Boolean allowCircularReferences) {
}
