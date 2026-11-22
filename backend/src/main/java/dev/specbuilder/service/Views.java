package dev.specbuilder.service;

import dev.specbuilder.api.dto.*;
import dev.specbuilder.domain.*;

import java.util.List;
import java.util.UUID;

/** Entity → API record mapping. Must run inside a transaction because it follows lazy references. */
final class Views {

    private Views() {
    }

    static ProjectView project(Project p, long payloadCount, long endpointCount) {
        return new ProjectView(p.getId(), p.getName(), p.getBasePackage(), p.getVersion(), p.getDescription(),
                p.getDefaultImplementationStyle(), p.isAllowCircularReferences(), payloadCount, endpointCount,
                p.getCreatedAt(), p.getUpdatedAt());
    }

    static PayloadView payload(Payload p) {
        return new PayloadView(p.getId(), p.getProject().getId(), p.getName(), p.getDescription(), p.getExample(),
                p.getFields().stream().map(Views::field).toList());
    }

    static FieldDto field(PayloadField f) {
        Payload ref = f.getRefPayload();
        return new FieldDto(f.getName(), f.getType(), f.getItemType(), id(ref), name(ref), f.isRequired(),
                f.getFormat(), f.getDescription(), f.getExample(), f.getMinLength(), f.getMaxLength(),
                f.getMinimum(), f.getMaximum(), f.getPattern(), List.copyOf(f.getEnumValues()));
    }

    static EndpointView endpoint(Endpoint e) {
        return new EndpointView(e.getId(), e.getProject().getId(), e.getKind(), e.getName(), e.getSummary(),
                e.getDescription(), List.copyOf(e.getTags()), e.getImplementationStyle(), e.effectiveImplementationStyle(),
                e.getParameters().stream()
                        .map(p -> new ParameterDto(p.getName(), p.getLocation(), p.getType(), p.isRequired(), p.getDescription()))
                        .toList(),
                e.getHttpMethod(), e.getPath(), id(e.getRequestPayload()), name(e.getRequestPayload()),
                e.getResponses().stream()
                        .map(r -> new ResponseDto(r.getStatusCode(), r.getDescription(), id(r.getPayload()), name(r.getPayload())))
                        .toList(),
                List.copyOf(e.getSecurity()), e.getChannel(), e.getAsyncOperation(), e.getProtocol(),
                id(e.getMessagePayload()), name(e.getMessagePayload()), e.getRules().size());
    }

    private static UUID id(Payload p) {
        return p == null ? null : p.getId();
    }

    private static String name(Payload p) {
        return p == null ? null : p.getName();
    }
}
