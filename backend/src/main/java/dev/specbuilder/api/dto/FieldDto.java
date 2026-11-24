package dev.specbuilder.api.dto;

import dev.specbuilder.domain.FieldType;

import java.util.List;
import java.util.UUID;

/**
 * A payload field. {@code refPayloadName} is filled in on responses and ignored on requests.
 * {@code errorCodeIds} left out (null) on a request keeps the codes the same-named field had.
 */
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
        List<String> enumValues,
        List<UUID> errorCodeIds) {

    public FieldDto(String name, FieldType type, FieldType itemType, UUID refPayloadId, String refPayloadName, Boolean required,
                    String format, String description, String example, Integer minLength, Integer maxLength, Double minimum,
                    Double maximum, String pattern, List<String> enumValues) {
        this(name, type, itemType, refPayloadId, refPayloadName, required, format, description, example, minLength, maxLength,
                minimum, maximum, pattern, enumValues, null);
    }
}
