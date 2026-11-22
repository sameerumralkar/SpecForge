package dev.specbuilder.api.dto;

import java.util.UUID;

/** {@code payloadName} is filled in on responses and ignored on requests. */
public record ResponseDto(String statusCode, String description, UUID payloadId, String payloadName) {
}
