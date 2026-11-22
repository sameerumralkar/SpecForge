package dev.specbuilder.api.dto;

import dev.specbuilder.domain.*;

import java.util.List;
import java.util.UUID;

public record EndpointView(
        UUID id,
        UUID projectId,
        EndpointKind kind,
        String name,
        String summary,
        String description,
        List<String> tags,
        ImplementationStyle implementationStyle,
        ImplementationStyle effectiveImplementationStyle,
        List<ParameterDto> parameters,
        HttpMethod httpMethod,
        String path,
        UUID requestPayloadId,
        String requestPayloadName,
        List<ResponseDto> responses,
        List<SecurityScheme> security,
        String channel,
        AsyncOperation operation,
        MessagingProtocol protocol,
        UUID messagePayloadId,
        String messagePayloadName,
        int ruleCount) {
}
