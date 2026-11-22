package dev.specbuilder.generator;

import dev.specbuilder.domain.*;
import dev.specbuilder.generator.ProjectSnapshot.*;
import dev.specbuilder.repository.EndpointRepository;
import dev.specbuilder.repository.PayloadRepository;
import dev.specbuilder.service.ProjectService;
import dev.specbuilder.service.RuleConfigCodec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Reads a project from the database into a {@link ProjectSnapshot}. */
@Service
public class SnapshotService {

    private final ProjectService projects;
    private final PayloadRepository payloads;
    private final EndpointRepository endpoints;
    private final RuleConfigCodec codec;

    public SnapshotService(ProjectService projects, PayloadRepository payloads, EndpointRepository endpoints, RuleConfigCodec codec) {
        this.projects = projects;
        this.payloads = payloads;
        this.endpoints = endpoints;
        this.codec = codec;
    }

    @Transactional(readOnly = true)
    public ProjectSnapshot load(UUID projectId) {
        Project p = projects.require(projectId);
        List<PayloadSnapshot> payloadSnapshots = payloads.findByProjectId(projectId).stream()
                .sorted(Comparator.comparing(Payload::getName))
                .map(SnapshotService::payload)
                .toList();
        List<EndpointSnapshot> endpointSnapshots = endpoints.findByProjectId(projectId).stream()
                .sorted(Comparator.comparing(Endpoint::getName))
                .map(this::endpoint)
                .toList();
        return new ProjectSnapshot(p.getId(), p.getName(), p.getBasePackage(), p.getVersion(), p.getDescription(),
                p.getDefaultImplementationStyle(), payloadSnapshots, endpointSnapshots);
    }

    private static PayloadSnapshot payload(Payload p) {
        return new PayloadSnapshot(p.getId(), p.getName(), p.getDescription(), p.getExample(),
                p.getFields().stream().map(f -> new FieldSnapshot(f.getName(), f.getType(), f.getItemType(), name(f.getRefPayload()),
                        f.isRequired(), f.getFormat(), f.getDescription(), f.getExample(), f.getMinLength(), f.getMaxLength(),
                        f.getMinimum(), f.getMaximum(), f.getPattern(), List.copyOf(f.getEnumValues()))).toList());
    }

    private EndpointSnapshot endpoint(Endpoint e) {
        return new EndpointSnapshot(e.getId(), e.getKind(), e.getName(), e.getSummary(), e.getDescription(), List.copyOf(e.getTags()),
                e.effectiveImplementationStyle(),
                e.getParameters().stream().map(x -> new ParameterSnapshot(x.getName(), x.getLocation(), x.getType(), x.isRequired(), x.getDescription())).toList(),
                e.getHttpMethod(), e.getPath(), name(e.getRequestPayload()),
                e.getResponses().stream().map(r -> new ResponseSnapshot(r.getStatusCode(), r.getDescription(), name(r.getPayload()))).toList(),
                List.copyOf(e.getSecurity()), e.getChannel(), e.getAsyncOperation(), e.getProtocol(), name(e.getMessagePayload()),
                e.getRules().stream().map(r -> new RuleSnapshot(r.getType(), r.getName(), r.getCondition(), codec.read(r.getType(), r.getConfig()))).toList());
    }

    private static String name(Payload p) {
        return p == null ? null : p.getName();
    }
}
