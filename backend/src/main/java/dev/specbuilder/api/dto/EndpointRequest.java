package dev.specbuilder.api.dto;

import dev.specbuilder.domain.*;

import java.util.List;
import java.util.UUID;

/**
 * Create or replace an endpoint. REST endpoints use httpMethod, path, requestPayloadId, responses
 * and security; async endpoints use channel, operation, protocol and messagePayloadId. Fields for
 * the other kind are ignored.
 */
public record EndpointRequest(
        EndpointKind kind,
        String name,
        String summary,
        String description,
        List<String> tags,
        ImplementationStyle implementationStyle,
        List<ParameterDto> parameters,
        HttpMethod httpMethod,
        String path,
        UUID requestPayloadId,
        List<ResponseDto> responses,
        List<SecurityScheme> security,
        String channel,
        AsyncOperation operation,
        MessagingProtocol protocol,
        UUID messagePayloadId) {
}
