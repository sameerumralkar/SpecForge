package dev.specbuilder.api.dto;

import dev.specbuilder.domain.FieldType;
import dev.specbuilder.domain.ParameterLocation;

public record ParameterDto(String name, ParameterLocation location, FieldType type, Boolean required, String description) {
}
