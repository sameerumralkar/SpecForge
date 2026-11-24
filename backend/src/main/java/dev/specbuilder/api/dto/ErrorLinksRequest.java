package dev.specbuilder.api.dto;

import java.util.List;

/** Every field and rule an error code should be linked to; links not listed are removed. CHECK links are read-only. */
public record ErrorLinksRequest(List<ErrorLink> links) {
}
