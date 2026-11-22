package dev.specbuilder.api.dto;

import java.util.List;
import java.util.UUID;

public record PayloadView(UUID id, UUID projectId, String name, String description, String example, List<FieldDto> fields) {
}
