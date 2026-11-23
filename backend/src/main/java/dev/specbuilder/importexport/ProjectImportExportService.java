package dev.specbuilder.importexport;

import dev.specbuilder.api.dto.*;
import dev.specbuilder.importexport.ProjectExport.*;
import dev.specbuilder.repository.ProjectRepository;
import dev.specbuilder.service.EndpointService;
import dev.specbuilder.service.PayloadService;
import dev.specbuilder.service.ProjectService;
import dev.specbuilder.service.RuleService;
import dev.specbuilder.validation.ValidationErrors;
import dev.specbuilder.validation.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.*;

/** Exports a project to {@link ProjectExport} and imports one, through the normal services and their validation. */
@Service
public class ProjectImportExportService {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ProjectService projects;
    private final ProjectRepository projectRepository;
    private final PayloadService payloads;
    private final EndpointService endpoints;
    private final RuleService rules;

    public ProjectImportExportService(ProjectService projects, ProjectRepository projectRepository, PayloadService payloads,
                                      EndpointService endpoints, RuleService rules) {
        this.projects = projects;
        this.projectRepository = projectRepository;
        this.payloads = payloads;
        this.endpoints = endpoints;
        this.rules = rules;
    }

    // ================================================================== export

    @Transactional(readOnly = true)
    public ProjectExport export(UUID projectId) {
        ProjectView p = projects.get(projectId);
        List<PayloadView> payloadViews = payloads.list(projectId);
        Map<UUID, String> names = new HashMap<>();
        payloadViews.forEach(v -> names.put(v.id(), v.name()));

        List<PayloadData> payloadData = payloadViews.stream().map(v -> new PayloadData(v.name(), v.description(), v.example(),
                v.fields().stream().map(f -> new FieldData(f.name(), f.type(), f.itemType(), f.refPayloadName(), Boolean.TRUE.equals(f.required()),
                        f.format(), f.description(), f.example(), f.minLength(), f.maxLength(), f.minimum(), f.maximum(), f.pattern(),
                        f.enumValues())).toList())).toList();

        List<EndpointData> endpointData = endpoints.list(projectId).stream()
                .sorted(Comparator.comparing(EndpointView::name))
                .map(e -> new EndpointData(e.kind(), e.name(), e.summary(), e.description(), e.tags(), e.implementationStyle(),
                        e.parameters(), e.httpMethod(), e.path(), e.requestPayloadName(),
                        e.responses().stream().map(r -> new ResponseData(r.statusCode(), r.description(), r.payloadName())).toList(),
                        e.security(), e.channel(), e.operation(), e.protocol(), e.messagePayloadName(),
                        rules.chain(projectId, e.id()).rules().stream()
                                .map(r -> new RuleData(r.type(), r.name(), r.condition(), idsToNames(r.config(), names))).toList()))
                .toList();

        return new ProjectExport(ProjectExport.FORMAT, ProjectExport.VERSION,
                new ProjectData(p.name(), p.basePackage(), p.version(), p.description(), p.defaultImplementationStyle(),
                        p.allowCircularReferences()),
                payloadData, endpointData);
    }

    /** "targetPayloadId": "&lt;uuid&gt;" → "targetPayload": "Order". */
    private static JsonNode idsToNames(JsonNode config, Map<UUID, String> names) {
        ObjectNode out = JSON.createObjectNode();
        for (Map.Entry<String, JsonNode> e : config.properties()) {
            String key = e.getKey();
            if (key.endsWith("PayloadId") && e.getValue().isString()) {
                out.put(key.substring(0, key.length() - 2), names.getOrDefault(UUID.fromString(e.getValue().asString()), null));
            } else if (key.endsWith("PayloadId") && e.getValue().isNull()) {
                out.putNull(key.substring(0, key.length() - 2));
            } else {
                out.set(key, e.getValue());
            }
        }
        return out;
    }

    // ================================================================== import

    /**
     * Creates a new project from an export. Everything happens in one transaction: if any part is
     * invalid, nothing is created and the errors name the part, e.g. "payloads[2].fields[0].name".
     * The project gets a free name if its own is taken.
     */
    @Transactional
    public ProjectView importProject(ProjectExport data, String nameOverride) {
        if (data == null || !ProjectExport.FORMAT.equals(data.format())) {
            throw ValidationErrors.single("format", "not a Spec Builder project export (expected format \"" + ProjectExport.FORMAT + "\")");
        }
        if (data.version() == null) {
            throw ValidationErrors.single("version", "is missing");
        }
        if (data.version() > ProjectExport.VERSION) {
            throw ValidationErrors.single("version", "made by a newer version of Spec Builder (" + data.version() + ")");
        }
        ProjectData p = data.project();
        if (p == null) {
            throw ValidationErrors.single("project", "is missing");
        }
        String name = freeName(nameOverride != null && !nameOverride.isBlank() ? nameOverride.strip() : p.name());
        UUID projectId = prefixed("project", () -> projects.create(new ProjectRequest(name, p.basePackage(), p.version(),
                p.description(), p.defaultImplementationStyle(), Boolean.TRUE.equals(p.allowCircularReferences())))).id();

        // Payloads first without fields, so fields can refer to any of them; then with fields.
        List<PayloadData> payloadData = data.payloads() == null ? List.of() : data.payloads();
        Map<String, UUID> ids = new HashMap<>();
        for (int i = 0; i < payloadData.size(); i++) {
            PayloadData pd = payloadData.get(i);
            UUID id = prefixed("payloads[" + i + "]", () -> payloads.create(projectId,
                    new PayloadRequest(pd.name(), pd.description(), pd.example(), List.of()))).id();
            ids.put(pd.name(), id);
        }
        for (int i = 0; i < payloadData.size(); i++) {
            PayloadData pd = payloadData.get(i);
            String at = "payloads[" + i + "]";
            List<FieldDto> fields = new ArrayList<>();
            List<FieldData> fieldData = pd.fields() == null ? List.of() : pd.fields();
            for (int j = 0; j < fieldData.size(); j++) {
                FieldData f = fieldData.get(j);
                fields.add(new FieldDto(f.name(), f.type(), f.itemType(), ref(ids, f.refPayload(), at + ".fields[" + j + "].refPayload"), null,
                        f.required(), f.format(), f.description(), f.example(), f.minLength(), f.maxLength(), f.minimum(), f.maximum(),
                        f.pattern(), f.enumValues()));
            }
            prefixed(at, () -> payloads.update(projectId, ids.get(pd.name()), new PayloadRequest(pd.name(), pd.description(), pd.example(), fields)));
        }

        List<EndpointData> endpointData = data.endpoints() == null ? List.of() : data.endpoints();
        for (int i = 0; i < endpointData.size(); i++) {
            EndpointData e = endpointData.get(i);
            String at = "endpoints[" + i + "]";
            List<ResponseDto> responses = new ArrayList<>();
            List<ResponseData> responseData = e.responses() == null ? List.of() : e.responses();
            for (int j = 0; j < responseData.size(); j++) {
                ResponseData r = responseData.get(j);
                responses.add(new ResponseDto(r.statusCode(), r.description(), ref(ids, r.payload(), at + ".responses[" + j + "].payload"), null));
            }
            EndpointRequest request = new EndpointRequest(e.kind(), e.name(), e.summary(), e.description(), e.tags(),
                    e.implementationStyle(), e.parameters(), e.httpMethod(), e.path(), ref(ids, e.requestPayload(), at + ".requestPayload"),
                    responses, e.security(), e.channel(), e.operation(), e.protocol(), ref(ids, e.messagePayload(), at + ".messagePayload"));
            UUID endpointId = prefixed(at, () -> endpoints.create(projectId, request)).id();
            List<RuleData> ruleData = e.rules() == null ? List.of() : e.rules();
            for (int j = 0; j < ruleData.size(); j++) {
                RuleData r = ruleData.get(j);
                String ruleAt = at + ".rules[" + j + "]";
                JsonNode config = namesToIds(r.config(), ids, ruleAt + ".config");
                prefixed(ruleAt, () -> rules.create(projectId, endpointId, new RuleRequest(r.type(), r.name(), r.condition(), config, null)));
            }
        }
        return projects.get(projectId);
    }

    /** "targetPayload": "Order" → "targetPayloadId": "&lt;uuid&gt;". */
    private static JsonNode namesToIds(JsonNode config, Map<String, UUID> ids, String at) {
        if (config == null || !config.isObject()) {
            return config;
        }
        ObjectNode out = JSON.createObjectNode();
        for (Map.Entry<String, JsonNode> e : config.properties()) {
            String key = e.getKey();
            if (key.endsWith("Payload") && (e.getValue().isString() || e.getValue().isNull())) {
                UUID id = e.getValue().isNull() ? null : ref(ids, e.getValue().asString(), at + "." + key);
                if (id == null) out.putNull(key + "Id"); else out.put(key + "Id", id.toString());
            } else {
                out.set(key, e.getValue());
            }
        }
        return out;
    }

    private static UUID ref(Map<String, UUID> ids, String name, String at) {
        if (name == null) {
            return null;
        }
        UUID id = ids.get(name);
        if (id == null) {
            throw ValidationErrors.single(at, "refers to payload '" + name + "', which is not in the file");
        }
        return id;
    }

    /** Runs a service call, adding {@code prefix} to the field paths of any validation errors. */
    private static <T> T prefixed(String prefix, java.util.function.Supplier<T> call) {
        try {
            return call.get();
        } catch (ValidationException e) {
            throw new ValidationException(e.errors().stream()
                    .map(err -> new ValidationErrors.FieldError(prefix + "." + err.field(), err.message())).toList());
        }
    }

    /** The name, or the next free numbered one: "Orders" → "Orders 2"; "Orders 2" → "Orders 3". */
    private String freeName(String wanted) {
        if (!projectRepository.existsByNameIgnoreCase(wanted)) {
            return wanted;
        }
        java.util.regex.Matcher numbered = java.util.regex.Pattern.compile("(.*) (\\d+)").matcher(wanted);
        String base = numbered.matches() ? numbered.group(1) : wanted;
        int n = numbered.matches() ? Integer.parseInt(numbered.group(2)) + 1 : 2;
        String name = base + " " + n;
        while (projectRepository.existsByNameIgnoreCase(name)) {
            name = base + " " + (++n);
        }
        return name;
    }
}
