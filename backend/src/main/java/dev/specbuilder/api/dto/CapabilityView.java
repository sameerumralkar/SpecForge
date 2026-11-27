package dev.specbuilder.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * A capability. {@code path} is the names from its L1 capability down to it. {@code endpointIds} are the
 * APIs linked to it directly; {@code apiCount} counts its API inventory: those plus every API linked below it.
 */
public record CapabilityView(UUID id, UUID projectId, UUID parentId, int level, String levelName, int position, String name,
                             String code, String description, List<String> path, List<UUID> endpointIds, int apiCount,
                             int childCount) {
}
