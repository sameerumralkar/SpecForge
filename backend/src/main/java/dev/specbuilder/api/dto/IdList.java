package dev.specbuilder.api.dto;

import java.util.List;
import java.util.UUID;

/** A list of ids, for replacing links. */
public record IdList(List<UUID> ids) {
}
