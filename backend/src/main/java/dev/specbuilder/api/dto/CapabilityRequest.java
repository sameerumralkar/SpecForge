package dev.specbuilder.api.dto;

import java.util.UUID;

/**
 * Create or change a capability. {@code parentId} null makes it an L1 capability; changing it moves the
 * capability (with everything below it). {@code position} (0-based among its siblings) is optional: by
 * default a new capability goes last and a changed one stays where it is.
 */
public record CapabilityRequest(String name, String code, String description, UUID parentId, Integer position) {
}
