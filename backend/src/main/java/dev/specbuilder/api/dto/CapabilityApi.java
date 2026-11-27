package dev.specbuilder.api.dto;

import dev.specbuilder.domain.AsyncOperation;
import dev.specbuilder.domain.EndpointKind;
import dev.specbuilder.domain.HttpMethod;

import java.util.List;
import java.util.UUID;

/**
 * One API in a capability's inventory. {@code direct} means it is linked to this capability itself;
 * {@code via} lists the capabilities below it that it is linked to.
 */
public record CapabilityApi(UUID endpointId, String name, EndpointKind kind, HttpMethod httpMethod, String path, String channel,
                            AsyncOperation operation, String summary, boolean direct, List<Via> via) {

    public record Via(UUID capabilityId, String name, int level) {
    }
}
