package dev.specbuilder.reports;

import dev.specbuilder.api.dto.PayloadUsage;
import dev.specbuilder.domain.Capability;
import dev.specbuilder.domain.Endpoint;
import dev.specbuilder.domain.EndpointKind;
import dev.specbuilder.domain.FieldDefinition;
import dev.specbuilder.domain.FieldType;
import dev.specbuilder.domain.Payload;
import dev.specbuilder.domain.PayloadField;
import dev.specbuilder.domain.PayloadRole;
import dev.specbuilder.domain.Project;
import dev.specbuilder.domain.ProcessingRule;
import dev.specbuilder.domain.ResponseDefinition;
import dev.specbuilder.reports.Reports.CapabilityMap;
import dev.specbuilder.reports.Reports.MapCapability;
import dev.specbuilder.reports.Reports.MapEndpoint;
import dev.specbuilder.reports.Reports.MapField;
import dev.specbuilder.reports.Reports.MapPayload;
import dev.specbuilder.reports.Reports.MapRule;
import dev.specbuilder.reports.Reports.MapUse;
import dev.specbuilder.reports.Reports.EndpointRow;
import dev.specbuilder.reports.Reports.EndpointsReport;
import dev.specbuilder.reports.Reports.FieldRow;
import dev.specbuilder.reports.Reports.FieldsReport;
import dev.specbuilder.reports.Reports.LinkedObjectCount;
import dev.specbuilder.reports.Reports.ObjectRow;
import dev.specbuilder.reports.Reports.ObjectsReport;
import dev.specbuilder.reports.Reports.ProjectEndpointCount;
import dev.specbuilder.reports.Reports.ProjectRow;
import dev.specbuilder.reports.Reports.ProjectTotals;
import dev.specbuilder.reports.Reports.ProjectsReport;
import dev.specbuilder.repository.CapabilityRepository;
import dev.specbuilder.repository.EndpointRepository;
import dev.specbuilder.repository.ErrorCodeRepository;
import dev.specbuilder.repository.FieldDefinitionRepository;
import dev.specbuilder.repository.PayloadRepository;
import dev.specbuilder.repository.ProjectRepository;
import dev.specbuilder.service.CapabilityService;
import dev.specbuilder.service.PayloadService;
import dev.specbuilder.service.RuleConfigCodec;
import dev.specbuilder.validation.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The reports: counts and details across projects. Each one reads the saved projects and changes nothing.
 * Rows are sorted by project name, then by the item's name, so a report reads the same way every time.
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    private static final Comparator<String> BY_NAME = String.CASE_INSENSITIVE_ORDER;

    private final ProjectRepository projects;
    private final PayloadRepository payloads;
    private final EndpointRepository endpoints;
    private final ErrorCodeRepository errorCodes;
    private final CapabilityRepository capabilities;
    private final FieldDefinitionRepository fieldDefinitions;
    private final PayloadService payloadService;
    private final RuleConfigCodec codec;

    public ReportService(ProjectRepository projects, PayloadRepository payloads, EndpointRepository endpoints,
                         ErrorCodeRepository errorCodes, CapabilityRepository capabilities,
                         FieldDefinitionRepository fieldDefinitions, PayloadService payloadService,
                         RuleConfigCodec codec) {
        this.codec = codec;
        this.projects = projects;
        this.payloads = payloads;
        this.endpoints = endpoints;
        this.errorCodes = errorCodes;
        this.capabilities = capabilities;
        this.fieldDefinitions = fieldDefinitions;
        this.payloadService = payloadService;
    }

    /** 1. The projects, with their settings and how much each holds. */
    public ProjectsReport projects(UUID projectId) {
        List<ProjectRow> rows = new ArrayList<>();
        for (Project p : scope(projectId)) {
            List<Endpoint> eps = endpoints.findByProjectId(p.getId());
            List<Payload> pls = payloads.findByProjectId(p.getId());
            Set<UUID> mapped = mappedEndpoints(p.getId());
            int rest = (int) eps.stream().filter(e -> e.getKind() == EndpointKind.REST).count();
            int objects = (int) pls.stream().filter(pl -> pl.getRole() == PayloadRole.OBJECT).count();
            rows.add(new ProjectRow(p.getId(), p.getName(), p.getDescription(), p.getVersion(), p.getBasePackage(),
                    p.getDefaultImplementationStyle().name(), p.getCreatedAt(), p.getUpdatedAt(),
                    eps.size(), rest, eps.size() - rest, objects, pls.size() - objects,
                    pls.stream().mapToInt(pl -> pl.getFields().size()).sum(),
                    fieldDefinitions.findByProjectId(p.getId()).size(),
                    errorCodes.findByProjectId(p.getId()).size(),
                    capabilities.findByProjectId(p.getId()).size(),
                    eps.stream().mapToInt(e -> e.getRules().size()).sum(),
                    (int) eps.stream().filter(e -> mapped.contains(e.getId())).count()));
        }
        ProjectTotals totals = new ProjectTotals(rows.size(),
                sum(rows, ProjectRow::endpoints), sum(rows, ProjectRow::restEndpoints), sum(rows, ProjectRow::asyncEndpoints),
                sum(rows, ProjectRow::objects), sum(rows, ProjectRow::structures), sum(rows, ProjectRow::fields),
                sum(rows, ProjectRow::fieldDefinitions), sum(rows, ProjectRow::errorCodes),
                sum(rows, ProjectRow::capabilities), sum(rows, ProjectRow::rules));
        return new ProjectsReport(totals, rows);
    }

    /** 2. The endpoints of each project: a count per project, then every endpoint with its details. */
    public EndpointsReport endpoints(UUID projectId) {
        List<ProjectEndpointCount> counts = new ArrayList<>();
        List<EndpointRow> rows = new ArrayList<>();
        for (Project p : scope(projectId)) {
            Map<UUID, List<String>> capabilityNames = capabilityNamesByEndpoint(p.getId());
            List<Endpoint> eps = new ArrayList<>(endpoints.findByProjectId(p.getId()));
            eps.sort(Comparator.comparing(Endpoint::getName, BY_NAME));
            int rest = 0;
            int withRules = 0;
            int mapped = 0;
            for (Endpoint e : eps) {
                boolean isRest = e.getKind() == EndpointKind.REST;
                rest += isRest ? 1 : 0;
                withRules += e.getRules().isEmpty() ? 0 : 1;
                List<String> caps = capabilityNames.getOrDefault(e.getId(), List.of());
                mapped += caps.isEmpty() ? 0 : 1;
                rows.add(new EndpointRow(p.getId(), p.getName(), e.getId(), e.getName(), e.getKind().name(),
                        isRest && e.getHttpMethod() != null ? e.getHttpMethod().name() : null,
                        isRest ? e.getPath() : null,
                        !isRest && e.getAsyncOperation() != null ? e.getAsyncOperation().name() : null,
                        isRest ? null : e.getChannel(),
                        !isRest && e.getProtocol() != null ? e.getProtocol().name() : null,
                        e.getSummary(), List.copyOf(e.getTags()), e.effectiveImplementationStyle().name(),
                        isRest ? name(e.getRequestPayload()) : null,
                        isRest ? e.getResponses().stream().map(ReportService::response).toList() : List.of(),
                        isRest ? null : name(e.getMessagePayload()),
                        e.getParameters().size(), e.getRules().size(),
                        e.getSecurity().stream().map(Enum::name).toList(), caps));
            }
            counts.add(new ProjectEndpointCount(p.getId(), p.getName(), eps.size(), rest, eps.size() - rest, withRules, mapped));
        }
        return new EndpointsReport(rows.size(), counts, rows);
    }

    /** 3. The payloads, objects first: their fields, the objects they hold, and where each is used. */
    public ObjectsReport objects(UUID projectId) {
        List<ObjectRow> rows = new ArrayList<>();
        for (Project p : scope(projectId)) {
            List<Payload> pls = new ArrayList<>(payloads.findByProjectId(p.getId()));
            pls.sort(Comparator.comparing(Payload::getRole).thenComparing(Payload::getName, BY_NAME));
            for (Payload pl : pls) {
                List<PayloadField> fields = pl.getFields();
                List<String> linked = fields.stream().map(PayloadField::getRefPayload).filter(r -> r != null)
                        .map(Payload::getName).distinct().sorted(BY_NAME).toList();
                List<String> usages = payloadService.usages(p.getId(), pl.getId()).stream()
                        .map(ReportService::usage).toList();
                rows.add(new ObjectRow(p.getId(), p.getName(), pl.getId(), pl.getName(), pl.getRole().name(),
                        pl.getDescription(), fields.size(),
                        (int) fields.stream().filter(PayloadField::isRequired).count(),
                        (int) fields.stream().filter(f -> f.getRefPayload() != null).count(),
                        linked, usages.size(), usages));
            }
        }
        int objects = (int) rows.stream().filter(r -> r.role().equals(PayloadRole.OBJECT.name())).count();
        return new ObjectsReport(rows.size(), objects, rows.size() - objects, rows);
    }

    /** 4. Every payload field, with the object it holds, and a count of fields per object held. */
    public FieldsReport fields(UUID projectId) {
        List<FieldRow> rows = new ArrayList<>();
        List<LinkedObjectCount> perObject = new ArrayList<>();
        for (Project p : scope(projectId)) {
            Set<String> inventory = fieldDefinitions.findByProjectId(p.getId()).stream()
                    .map(FieldDefinition::getField).map(f -> f.getName().toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());
            List<Payload> pls = new ArrayList<>(payloads.findByProjectId(p.getId()));
            pls.sort(Comparator.comparing(Payload::getName, BY_NAME));
            Map<UUID, Payload> objects = new LinkedHashMap<>();
            Map<UUID, List<String>> heldBy = new HashMap<>();
            for (Payload pl : pls) {
                List<PayloadField> fields = pl.getFields();
                for (int i = 0; i < fields.size(); i++) {
                    PayloadField f = fields.get(i);
                    Payload ref = f.getRefPayload();
                    if (ref != null) {
                        objects.putIfAbsent(ref.getId(), ref);
                        heldBy.computeIfAbsent(ref.getId(), k -> new ArrayList<>()).add(pl.getName() + "." + f.getName());
                    }
                    rows.add(new FieldRow(p.getId(), p.getName(), pl.getId(), pl.getName(), pl.getRole().name(), i + 1,
                            f.getName(), f.getType().name(), f.getType() == FieldType.ARRAY && f.getItemType() != null ? f.getItemType().name() : null,
                            f.isRequired(), ref == null ? null : ref.getId(), name(ref), ref == null ? null : ref.getRole().name(),
                            constraints(f), f.getDescription(), inventory.contains(f.getName().toLowerCase(Locale.ROOT))));
                }
            }
            objects.values().stream().sorted(Comparator.comparing(Payload::getName, BY_NAME)).forEach(o ->
                    perObject.add(new LinkedObjectCount(p.getId(), p.getName(), o.getId(), o.getName(), o.getRole().name(),
                            heldBy.get(o.getId()).size(), heldBy.get(o.getId()))));
        }
        int linked = (int) rows.stream().filter(r -> r.linkedObjectId() != null).count();
        return new FieldsReport(rows.size(), linked, perObject.size(), perObject, rows);
    }

    /** The capability map of one project: capabilities in tree order, endpoints and payloads by name. */
    public CapabilityMap map(UUID projectId) {
        Project project = projects.findById(projectId).orElseThrow(() -> new NotFoundException("Project", projectId));

        List<Capability> caps = capabilities.findByProjectId(projectId);
        Map<UUID, List<Capability>> children = new HashMap<>();
        caps.forEach(c -> children.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c));
        children.values().forEach(list -> list.sort(Comparator.comparingInt(Capability::getPosition).thenComparing(Capability::getName, BY_NAME)));
        List<MapCapability> tree = new ArrayList<>();
        walk(null, children, tree, new HashSet<>());

        List<Endpoint> eps = new ArrayList<>(endpoints.findByProjectId(projectId));
        eps.sort(Comparator.comparing(Endpoint::getName, BY_NAME));
        List<MapEndpoint> endpointRows = eps.stream().map(this::mapEndpoint).toList();

        List<Payload> pls = new ArrayList<>(payloads.findByProjectId(projectId));
        pls.sort(Comparator.comparing(Payload::getName, BY_NAME));
        List<MapPayload> payloadRows = pls.stream().map(p -> new MapPayload(p.getId(), p.getName(), p.getRole().name(), p.getDescription(),
                p.getFields().stream().map(f -> new MapField(f.getName(), f.getType().name(),
                        f.getType() == FieldType.ARRAY && f.getItemType() != null ? f.getItemType().name() : null, f.isRequired(),
                        f.getRefPayload() == null ? null : f.getRefPayload().getId(), constraints(f), f.getDescription())).toList())).toList();

        return new CapabilityMap(project.getId(), project.getName(), CapabilityService.levelNames(project), tree, endpointRows, payloadRows);
    }

    private static void walk(UUID parent, Map<UUID, List<Capability>> children, List<MapCapability> out, Set<UUID> seen) {
        for (Capability c : children.getOrDefault(parent, List.of())) {
            if (!seen.add(c.getId())) continue;
            out.add(new MapCapability(c.getId(), c.getParentId(), c.getLevel(), c.getName(), c.getCode(), c.getDescription(),
                    List.copyOf(c.getEndpointIds())));
            walk(c.getId(), children, out, seen);
        }
    }

    private MapEndpoint mapEndpoint(Endpoint e) {
        boolean rest = e.getKind() == EndpointKind.REST;
        List<MapUse> uses = new ArrayList<>();
        if (rest && e.getRequestPayload() != null) uses.add(new MapUse(e.getRequestPayload().getId(), "request body"));
        if (rest) {
            for (ResponseDefinition r : e.getResponses()) {
                if (r.getPayload() != null) uses.add(new MapUse(r.getPayload().getId(), "response " + r.getStatusCode()));
            }
        }
        if (!rest && e.getMessagePayload() != null) uses.add(new MapUse(e.getMessagePayload().getId(), "message"));
        List<MapRule> rules = new ArrayList<>();
        List<ProcessingRule> ordered = new ArrayList<>(e.getRules());
        ordered.sort(Comparator.comparingInt(ProcessingRule::getOrdinal));
        for (int i = 0; i < ordered.size(); i++) {
            ProcessingRule r = ordered.get(i);
            List<UUID> refs = List.copyOf(new java.util.LinkedHashSet<>(codec.read(r.getType(), r.getConfig()).payloadRefs()));
            refs.forEach(id -> uses.add(new MapUse(id, "rule '" + r.getName() + "'")));
            rules.add(new MapRule(r.getId(), i + 1, r.getName(), r.getType().name(), r.getType().label(), r.getCondition(), refs,
                    r.getErrorCodeIds().size()));
        }
        return new MapEndpoint(e.getId(), e.getName(), e.getKind().name(),
                rest && e.getHttpMethod() != null ? e.getHttpMethod().name() : null, rest ? e.getPath() : null,
                !rest && e.getAsyncOperation() != null ? e.getAsyncOperation().name() : null, rest ? null : e.getChannel(),
                !rest && e.getProtocol() != null ? e.getProtocol().name() : null, e.getSummary(), uses, rules);
    }

    // ---- helpers ----

    private List<Project> scope(UUID projectId) {
        if (projectId != null) {
            return List.of(projects.findById(projectId).orElseThrow(() -> new NotFoundException("Project", projectId)));
        }
        return projects.findAllByOrderByNameAsc();
    }

    private Set<UUID> mappedEndpoints(UUID projectId) {
        Set<UUID> ids = new HashSet<>();
        capabilities.findByProjectId(projectId).forEach(c -> ids.addAll(c.getEndpointIds()));
        return ids;
    }

    /** For each endpoint, the capabilities it is linked to, as "L2 Card payments". */
    private Map<UUID, List<String>> capabilityNamesByEndpoint(UUID projectId) {
        Map<UUID, List<String>> out = new HashMap<>();
        List<Capability> caps = new ArrayList<>(capabilities.findByProjectId(projectId));
        caps.sort(Comparator.comparingInt(Capability::getLevel).thenComparing(Capability::getName, BY_NAME));
        for (Capability c : caps) {
            for (UUID id : c.getEndpointIds()) {
                out.computeIfAbsent(id, k -> new ArrayList<>()).add("L" + c.getLevel() + " " + c.getName());
            }
        }
        return out;
    }

    private static String name(Payload p) {
        return p == null ? null : p.getName();
    }

    private static String response(ResponseDefinition r) {
        return r.getPayload() == null ? r.getStatusCode() : r.getStatusCode() + " " + r.getPayload().getName();
    }

    /** "OrderRequest (field total)", "createOrder (request body)". */
    private static String usage(PayloadUsage u) {
        return u.ownerName() + " (" + u.detail() + ")";
    }

    /** The field's limits in short form, e.g. "length 3..3, pattern [A-Z]{3}". */
    static String constraints(PayloadField f) {
        List<String> parts = new ArrayList<>();
        if (f.getFormat() != null && !f.getFormat().isBlank()) parts.add("format " + f.getFormat());
        if (f.getMinLength() != null || f.getMaxLength() != null) {
            parts.add((f.getType() == FieldType.ARRAY ? "items " : "length ") + range(f.getMinLength(), f.getMaxLength()));
        }
        if (f.getMinimum() != null || f.getMaximum() != null) parts.add("value " + range(number(f.getMinimum()), number(f.getMaximum())));
        if (f.getPattern() != null && !f.getPattern().isBlank()) parts.add("pattern " + f.getPattern());
        if (!f.getEnumValues().isEmpty()) parts.add("one of " + String.join(", ", f.getEnumValues()));
        return String.join("; ", parts);
    }

    private static String range(Object min, Object max) {
        return (min == null ? "" : min) + ".." + (max == null ? "" : max);
    }

    private static Object number(Double d) {
        return d == null ? null : d == Math.rint(d) && !Double.isInfinite(d) ? (Object) (long) d.doubleValue() : d;
    }

    private static <T> int sum(List<T> rows, java.util.function.ToIntFunction<T> f) {
        return rows.stream().mapToInt(f).sum();
    }
}
