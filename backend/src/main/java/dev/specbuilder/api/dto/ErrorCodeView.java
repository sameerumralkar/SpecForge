package dev.specbuilder.api.dto;

import dev.specbuilder.domain.ErrorCategory;

import java.util.List;
import java.util.UUID;

/** {@code status} is the effective one: {@code httpStatus}, or the category's default when that is null. */
public record ErrorCodeView(UUID id, UUID projectId, String code, String message, Integer httpStatus, int status,
                            ErrorCategory category, String description, List<ErrorLink> links) {
}
