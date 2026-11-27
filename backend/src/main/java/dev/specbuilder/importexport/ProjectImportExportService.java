package dev.specbuilder.importexport;

import dev.specbuilder.api.dto.*;
import dev.specbuilder.domain.PayloadRole;
import dev.specbuilder.importexport.ProjectExport.*;
import dev.specbuilder.repository.ProjectRepository;
import dev.specbuilder.service.EndpointService;
import dev.specbuilder.service.ErrorCodeService;
import dev.specbuilder.service.FieldDefinitionService;
import dev.specbuilder.service.CapabilityService;
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
    private final ErrorCodeService errorCodes;
    private final FieldDefinitionService fieldDefinitions;
    private final CapabilityService capabilities;

    public ProjectImportExportService(ProjectService projects, ProjectRepository projectRepository, PayloadService payloads,
                                      EndpointService endpoints, RuleService rules, ErrorCodeService errorCodes,
                                      FieldDefinitionService fieldDefinitions, CapabilityService capabilities) {
        this.capabilities = capabilities;
        this.errorCodes = errorCodes;
        this.fieldDefinitions = fieldDefinitions;
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
        List<ErrorCodeView> codeViews = errorCodes.list(projectId);
        Map<UUID, String> codeNames = new HashMap<>();
        codeViews.forEach(c -> codeNames.put(c.id(), c.code()));

        List<PayloadData> payloadData = payloadViews.stream().map(v -> new PayloadData(v.name(), v.description(), v.example(),
                v.fields().stream().map(f -> fieldData(f, codeNames)).toList(), v.role())).toList();
        List<String> levels = capabilities.levels(projectId).levels();
        Map<UUID, String> endpointNames = new HashMap<>();
        endpoints.list(projectId).forEach(e -> endpointNames.put(e.id(), e.name()));
        List<CapabilityData> capabilityTree = capabilityTree(capabilities.list(projectId), null, endpointNames);
        List<FieldData> fieldInventory = fieldDefinitions.list(projectId).stream().map(d -> fieldData(d.field(), codeNames)).toList();

        List<EndpointData> endpointData = endpoints.list(projectId).stream()
                .sorted(Comparator.comparing(EndpointView::name))
                .map(e -> new EndpointData(e.kind(), e.name(), e.summary(), e.description(), e.tags(), e.implementationStyle(),
                        e.parameters(), e.httpMethod(), e.path(), e.requestPayloadName(),
                        e.responses().stream().map(r -> new ResponseData(r.statusCode(), r.description(), r.payloadName())).toList(),
                        e.security(), e.channel(), e.operation(), e.protocol(), e.messagePayloadName(),
                        rules.chain(projectId, e.id()).rules().stream()
                                .map(r -> new RuleData(r.type(), r.name(), r.condition(),
                                        codeIdsToCodes(idsToNames(r.config(), names), codeNames), codes(r.errorCodeIds(), codeNames))).toList()))
                .toList();

        return new ProjectExport(ProjectExport.FORMAT, ProjectExport.VERSION,
                new ProjectData(p.name(), p.basePackage(), p.version(), p.description(), p.defaultImplementationStyle(),
                        p.allowCircularReferences()),
                payloadData, endpointData,
                codeViews.stream().map(c -> new ErrorCodeData(c.code(), c.message(), c.httpStatus(), c.category(), c.description())).toList(),
                fieldInventory.isEmpty() ? null : fieldInventory,
                levels.equals(CapabilityService.DEFAULT_LEVELS) ? null : levels,
                capabilityTree.isEmpty() ? null : capabilityTree);
    }

    /** The capabilities below {@code parentId} (null: the L1 ones), nested. */
    private static List<CapabilityData> capabilityTree(List<CapabilityView> all, UUID parentId, Map<UUID, String> endpointNames) {
        return all.stream().filter(c -> Objects.equals(c.parentId(), parentId))
                .map(c -> new CapabilityData(c.name(), c.code(), c.description(),
                        c.endpointIds().stream().map(endpointNames::get).filter(Objects::nonNull).toList(),
                        nullIfEmpty(capabilityTree(all, c.id(), endpointNames))))
                .toList();
    }

    private static <T> List<T> nullIfEmpty(List<T> list) {
        return list.isEmpty() ? null : list;
    }

    private static FieldData fieldData(FieldDto f, Map<UUID, String> codeNames) {
        return new FieldData(f.name(), f.type(), f.itemType(), f.refPayloadName(), Boolean.TRUE.equals(f.required()),
                f.format(), f.description(), f.example(), f.minLength(), f.maxLength(), f.minimum(), f.maximum(), f.pattern(),
                f.enumValues(), codes(f.errorCodeIds(), codeNames));
    }

    private static List<String> codes(List<UUID> ids, Map<UUID, String> codeNames) {
        return ids == null || ids.isEmpty() ? null : ids.stream().map(codeNames::get).filter(Objects::nonNull).toList();
    }

    /** Anywhere in a config: "errorCodeId": "&lt;uuid&gt;" → "errorCode": "PAY-001". */
    private static JsonNode codeIdsToCodes(JsonNode node, Map<UUID, String> codeNames) {
        if (node == null) return null;
        if (node.isArray()) {
            var out = JSON.createArrayNode();
            node.forEach(n -> out.add(codeIdsToCodes(n, codeNames)));
            return out;
        }
        if (!node.isObject()) return node;
        ObjectNode out = JSON.createObjectNode();
        for (Map.Entry<String, JsonNode> e : node.properties()) {
            if (e.getKey().equals("errorCodeId")) {
                if (e.getValue().isString()) out.put("errorCode", codeNames.get(UUID.fromString(e.getValue().asString())));
            } else {
                out.set(e.getKey(), codeIdsToCodes(e.getValue(), codeNames));
            }
        }
        return out;
    }

    /** The reverse of {@link #codeIdsToCodes}; an unknown code is an error at its place in the file. */
    private static JsonNode codesToCodeIds(JsonNode node, Map<String, UUID> codeIds, String at) {
        if (node == null) return null;
        if (node.isArray()) {
            var out = JSON.createArrayNode();
            for (int i = 0; i < node.size(); i++) out.add(codesToCodeIds(node.get(i), codeIds, at + "[" + i + "]"));
            return out;
        }
        if (!node.isObject()) return node;
        ObjectNode out = JSON.createObjectNode();
        for (Map.Entry<String, JsonNode> e : node.properties()) {
            if (e.getKey().equals("errorCode")) {
                if (e.getValue().isString()) out.put("errorCodeId", codeRef(codeIds, e.getValue().asString(), at + ".errorCode").toString());
            } else {
                out.set(e.getKey(), codesToCodeIds(e.getValue(), codeIds, at + "." + e.getKey()));
            }
        }
        return out;
    }

    private static List<UUID> codeRefs(Map<String, UUID> codeIds, List<String> codes, String at) {
        if (codes == null) return List.of();
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < codes.size(); i++) ids.add(codeRef(codeIds, codes.get(i), at + "[" + i + "]"));
        return ids;
    }

    private static UUID codeRef(Map<String, UUID> codeIds, String code, String at) {
        UUID id = code == null ? null : codeIds.get(code.toUpperCase(Locale.ROOT));
        if (id == null) {
            throw ValidationErrors.single(at, "refers to error code '" + code + "', which is not in the file");
        }
        return id;
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

        List<ErrorCodeData> codeData = data.errorCodes() == null ? List.of() : data.errorCodes();
        Map<String, UUID> codeIds = new HashMap<>();
        for (int i = 0; i < codeData.size(); i++) {
            ErrorCodeData c = codeData.get(i);
            ErrorCodeView view = prefixed("errorCodes[" + i + "]", () -> errorCodes.create(projectId,
                    new ErrorCodeRequest(c.code(), c.message(), c.httpStatus(), c.category(), c.description())));
            codeIds.put(view.code().toUpperCase(Locale.ROOT), view.id());
        }

        List<FieldData> inventory = data.fieldInventory() == null ? List.of() : data.fieldInventory();
        for (int i = 0; i < inventory.size(); i++) {
            FieldData f = inventory.get(i);
            String at = "fieldInventory[" + i + "]";
            List<UUID> fieldCodes = codeRefs(codeIds, f.errorCodes(), at + ".errorCodes");
            prefixed(at, () -> fieldDefinitions.create(projectId, new FieldDto(f.name(), f.type(), f.itemType(), null, null, f.required(),
                    f.format(), f.description(), f.example(), f.minLength(), f.maxLength(), f.minimum(), f.maximum(), f.pattern(),
                    f.enumValues(), fieldCodes)));
        }

        // Payloads first without fields, so fields can refer to any of them; then with fields.
        List<PayloadData> payloadData = data.payloads() == null ? List.of() : data.payloads();
        Map<String, UUID> ids = new HashMap<>();
        Set<String> usedAsFieldType = new HashSet<>();
        payloadData.forEach(pd -> (pd.fields() == null ? List.<FieldData>of() : pd.fields()).stream()
                .map(FieldData::refPayload).filter(r -> r != null && !r.equals(pd.name())).forEach(usedAsFieldType::add));
        for (int i = 0; i < payloadData.size(); i++) {
            PayloadData pd = payloadData.get(i);
            PayloadRole role = pd.role() != null ? pd.role() : usedAsFieldType.contains(pd.name()) ? PayloadRole.OBJECT : PayloadRole.STRUCTURE;
            UUID id = prefixed("payloads[" + i + "]", () -> payloads.create(projectId,
                    new PayloadRequest(pd.name(), pd.description(), pd.example(), List.of(), role))).id();
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
                        f.pattern(), f.enumValues(), codeRefs(codeIds, f.errorCodes(), at + ".fields[" + j + "].errorCodes")));
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
                JsonNode config = codesToCodeIds(namesToIds(r.config(), ids, ruleAt + ".config"), codeIds, ruleAt + ".config");
                List<UUID> ruleCodes = codeRefs(codeIds, r.errorCodes(), ruleAt + ".errorCodes");
                prefixed(ruleAt, () -> rules.create(projectId, endpointId, new RuleRequest(r.type(), r.name(), r.condition(), config, null, ruleCodes)));
            }
        }
        if (data.capabilityLevels() != null && !data.capabilityLevels().isEmpty()) {
            prefixed("capabilityLevels", () -> capabilities.setLevels(projectId, new CapabilityLevels(data.capabilityLevels())));
        }
        Map<String, UUID> endpointIds = new HashMap<>();
        endpoints.list(projectId).forEach(e -> endpointIds.put(e.name(), e.id()));
        importCapabilities(projectId, data.capabilities(), null, "capabilities", endpointIds);
        return projects.get(projectId);
    }

    private void importCapabilities(UUID projectId, List<CapabilityData> list, UUID parentId, String at, Map<String, UUID> endpointIds) {
        if (list == null) return;
        for (int i = 0; i < list.size(); i++) {
            CapabilityData c = list.get(i);
            String here = at + "[" + i + "]";
            UUID id = prefixed(here, () -> capabilities.create(projectId, new CapabilityRequest(c.name(), c.code(), c.description(), parentId, null))).id();
            List<UUID> apis = new ArrayList<>();
            List<String> names = c.apis() == null ? List.of() : c.apis();
            for (int j = 0; j < names.size(); j++) {
                UUID api = endpointIds.get(names.get(j));
                if (api == null) throw ValidationErrors.single(here + ".apis[" + j + "]", "refers to API '" + names.get(j) + "', which is not in the file");
                apis.add(api);
            }
            if (!apis.isEmpty()) capabilities.setApis(projectId, id, new IdList(apis));
            importCapabilities(projectId, c.children(), id, here + ".children", endpointIds);
        }
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
