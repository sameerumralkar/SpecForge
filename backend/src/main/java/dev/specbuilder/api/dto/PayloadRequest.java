package dev.specbuilder.api.dto;

import java.util.List;

public record PayloadRequest(String name, String description, String example, List<FieldDto> fields) {
}
