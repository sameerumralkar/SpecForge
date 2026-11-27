package dev.specbuilder.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * An entry of the field inventory. {@code usedIn} lists the payload fields with the same name
 * ("Money.currency"): the places the definition was most likely added to.
 */
public record FieldDefinitionView(UUID id, UUID projectId, FieldDto field, List<String> usedIn) {
}
