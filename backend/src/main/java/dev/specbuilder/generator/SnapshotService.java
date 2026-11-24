package dev.specbuilder.generator;

import dev.specbuilder.domain.*;
import dev.specbuilder.generator.ProjectSnapshot.*;
import dev.specbuilder.repository.EndpointRepository;
import dev.specbuilder.repository.ErrorCodeRepository;
import dev.specbuilder.repository.PayloadRepository;
import dev.specbuilder.service.ProjectService;
import dev.specbuilder.service.RuleConfigCodec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Reads a project from the database into a {@link ProjectSnapshot}. */
@Service
public class SnapshotService {

    private final ProjectService projects;
    private final PayloadRepository payloads;
    private final EndpointRepository endpoints;
    private final RuleConfigCodec codec;
    private final ErrorCodeRepository errorCodes;

    public SnapshotService(ProjectService projects, PayloadRepository payloads, EndpointRepository endpoints, RuleConfigCodec codec,
                           ErrorCodeRepository errorCodes) {
        this.errorCodes = errorCodes;
        this.projects = projects;
        this.payloads = payloads;
        this.endpoints = endpoints;
        this.codec = codec;
    }

    @Transactional(readOnly = true)
    public ProjectSnapshot load(UUID projectId) {
        Project p = projects.require(projectId);
        List<ErrorCodeSnapshot> codes = errorCodes.findByProjectId(projectId).stream()
                .sorted(Comparator.comparing(ErrorCode::getCode))
                .map(c -> new ErrorCodeSnapshot(c.getId(), c.getCode(), c.getMessage(), c.effectiveStatus(), c.getCategory(), c.getDescription()))
                .toList();
        Map<UUID, String> codeOf = new HashMap<>();
        codes.forEach(c -> codeOf.put(c.id(), c.code()));
        List<PayloadSnapshot> payloadSnapshots = payloads.findByProjectId(projectId).stream()
                .sorted(Comparator.comparing(Payload::getName))
                .map(x -> payload(x, codeOf))
                .toList();
        List<EndpointSnapshot> endpointSnapshots = endpoints.findByProjectId(projectId).stream()
                .sorted(Comparator.comparing(Endpoint::getName))
                .map(e -> endpoint(e, codeOf))
                .toList();
        return new ProjectSnapshot(p.getId(), p.getName(), p.getBasePackage(), p.getVersion(), p.getDescription(),
                p.getDefaultImplementationStyle(), payloadSnapshots, endpointSnapshots, codes);
    }

    private static List<String> codes(List<UUID> ids, Map<UUID, String> codeOf) {
        return ids.stream().map(codeOf::get).filter(Objects::nonNull).toList();
    }

    private static PayloadSnapshot payload(Payload p, Map<UUID, String> codeOf) {
        return new PayloadSnapshot(p.getId(), p.getName(), p.getDescription(), p.getExample(),
                p.getFields().stream().map(f -> new FieldSnapshot(f.getName(), f.getType(), f.getItemType(), name(f.getRefPayload()),
                        f.isRequired(), f.getFormat(), f.getDescription(), f.getExample(), f.getMinLength(), f.getMaxLength(),
                        f.getMinimum(), f.getMaximum(), f.getPattern(), List.copyOf(f.getEnumValues()),
                        codes(f.getErrorCodeIds(), codeOf))).toList());
    }

    private EndpointSnapshot endpoint(Endpoint e, Map<UUID, String> codeOf) {
        return new EndpointSnapshot(e.getId(), e.getKind(), e.getName(), e.getSummary(), e.getDescription(), List.copyOf(e.getTags()),
                e.effectiveImplementationStyle(),
                e.getParameters().stream().map(x -> new ParameterSnapshot(x.getName(), x.getLocation(), x.getType(), x.isRequired(), x.getDescription())).toList(),
                e.getHttpMethod(), e.getPath(), name(e.getRequestPayload()),
                e.getResponses().stream().map(r -> new ResponseSnapshot(r.getStatusCode(), r.getDescription(), name(r.getPayload()))).toList(),
                List.copyOf(e.getSecurity()), e.getChannel(), e.getAsyncOperation(), e.getProtocol(), name(e.getMessagePayload()),
                e.getRules().stream().map(r -> new RuleSnapshot(r.getType(), r.getName(), r.getCondition(), codec.read(r.getType(), r.getConfig()),
                        codes(r.getErrorCodeIds(), codeOf))).toList());
    }

    private static String name(Payload p) {
        return p == null ? null : p.getName();
    }
}
