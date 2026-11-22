package dev.specbuilder.api.dto;

import dev.specbuilder.domain.FieldType;

import java.util.List;
import java.util.UUID;

/** A payload field. {@code refPayloadName} is filled in on responses and ignored on requests. */
public record FieldDto(
        String name,
        FieldType type,
        FieldType itemType,
        UUID refPayloadId,
        String refPayloadName,
        Boolean required,
        String format,
        String description,
        String example,
        Integer minLength,
        Integer maxLength,
        Double minimum,
        Double maximum,
        String pattern,
        List<String> enumValues) {
}
