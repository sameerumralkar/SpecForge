package dev.specbuilder.api.dto;

import java.util.UUID;

/** One place that refers to a payload: what kind of place, which payload or endpoint owns it, and where exactly. */
public record PayloadUsage(Kind kind, UUID ownerId, String ownerName, String detail) {

    public enum Kind { PAYLOAD_FIELD, REQUEST_BODY, RESPONSE_BODY, MESSAGE, RULE }
}
