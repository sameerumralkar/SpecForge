package dev.specbuilder.api.dto;

import dev.specbuilder.domain.ErrorCategory;

/** Create or replace an error code. {@code httpStatus} left out means the category's default. */
public record ErrorCodeRequest(String code, String message, Integer httpStatus, ErrorCategory category, String description) {
}
