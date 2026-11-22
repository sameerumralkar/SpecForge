package dev.specbuilder.service;

import dev.specbuilder.api.dto.FieldDto;
import dev.specbuilder.api.dto.PayloadRequest;
import dev.specbuilder.api.dto.PayloadUsage;
import dev.specbuilder.api.dto.PayloadView;
import dev.specbuilder.domain.*;
import dev.specbuilder.domain.rule.RuleConfig;
import dev.specbuilder.repository.EndpointRepository;
import dev.specbuilder.repository.PayloadRepository;
import dev.specbuilder.repository.ProcessingRuleRepository;
import dev.specbuilder.validation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

@Service
@Transactional
public class PayloadService {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ProjectService projectService;
    private final PayloadRepository payloads;
    private final EndpointRepository endpoints;
    private final ProcessingRuleRepository rules;
    private final RuleConfigCodec codec;

    public PayloadService(ProjectService projectService, PayloadRepository payloads, EndpointRepository endpoints,
                          ProcessingRuleRepository rules, RuleConfigCodec codec) {
        this.projectService = projectService;
        this.payloads = payloads;
        this.endpoints = endpoints;
        this.rules = rules;
        this.codec = codec;
    }

    @Transactional(readOnly = true)
    public List<PayloadView> list(UUID projectId) {
        projectService.require(projectId);
        return payloads.findByProjectId(projectId).stream()
                .sorted(Comparator.comparing(Payload::getName, String.CASE_INSENSITIVE_ORDER))
                .map(Views::payload)
                .toList();
    }

    @Transactional(readOnly = true)
    public PayloadView get(UUID projectId, UUID id) {
        return Views.payload(require(projectId, id));
    }

    public PayloadView create(UUID projectId, PayloadRequest request) {
        Project project = projectService.require(projectId);
        Payload payload = new Payload(project);
        apply(project, payload, request);
        payloads.save(payload);
        project.touch();
        return Views.payload(payload);
    }

    /** Replaces the payload. References elsewhere use the id, so a rename shows up everywhere at once. */
    public PayloadView update(UUID projectId, UUID id, PayloadRequest request) {
        Payload payload = require(projectId, id);
        apply(payload.getProject(), payload, request);
        payload.getProject().touch();
        return Views.payload(payload);
    }

    /** Refuses with {@link PayloadInUseException}, listing every use, while anything still refers to the payload. */
    public void delete(UUID projectId, UUID id) {
        Payload payload = require(projectId, id);
        List<PayloadUsage> usages = usages(payload);
        if (!usages.isEmpty()) {
            throw new PayloadInUseException(payload.getName(), usages);
        }
        payload.getProject().touch();
        payloads.delete(payload);
    }

    public PayloadView duplicate(UUID projectId, UUID id) {
        Payload source = require(projectId, id);
        Project project = source.getProject();
        Set<String> taken = payloads.findByProjectId(projectId).stream()
                .map(p -> p.getName().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        Payload copy = new Payload(project);
        copy.setName(uniqueName(source.getName() + "Copy", taken));
        copy.setDescription(source.getDescription());
        copy.setExample(source.getExample());
        for (PayloadField f : source.getFields()) {
            PayloadField c = f.copy();
            if (f.getRefPayload() == source) {
                c.setRefPayload(copy); // a self-reference stays a self-reference
            }
            copy.getFields().add(c);
        }
        payloads.save(copy);
        project.touch();
        return Views.payload(copy);
    }

    @Transactional(readOnly = true)
    public List<PayloadUsage> usages(UUID projectId, UUID id) {
        return usages(require(projectId, id));
    }

    Payload require(UUID projectId, UUID id) {
        return payloads.findByIdAndProjectId(id, projectId).orElseThrow(() -> new NotFoundException("Payload", id));
    }

    private List<PayloadUsage> usages(Payload payload) {
        UUID id = payload.getId();
        List<PayloadUsage> result = new ArrayList<>();
        for (Object[] row : payloads.findFieldReferences(id)) {
            if (!id.equals(row[0])) {
                result.add(new PayloadUsage(PayloadUsage.Kind.PAYLOAD_FIELD, (UUID) row[0], (String) row[1], "field " + row[2]));
            }
        }
        for (Endpoint e : endpoints.findUsingAsBodyOrMessage(id)) {
            if (e.getRequestPayload() == payload) {
                result.add(new PayloadUsage(PayloadUsage.Kind.REQUEST_BODY, e.getId(), e.getName(), "request body"));
            }
            if (e.getMessagePayload() == payload) {
                result.add(new PayloadUsage(PayloadUsage.Kind.MESSAGE, e.getId(), e.getName(), "message on " + e.getChannel()));
            }
        }
        for (Object[] row : endpoints.findResponseReferences(id)) {
            result.add(new PayloadUsage(PayloadUsage.Kind.RESPONSE_BODY, (UUID) row[0], (String) row[1], "response " + row[2]));
        }
        for (ProcessingRule r : rules.findByProjectId(payload.getProject().getId())) {
            RuleConfig config = codec.read(r.getType(), r.getConfig());
            if (config.payloadRefs().contains(id)) {
                result.add(new PayloadUsage(PayloadUsage.Kind.RULE, r.getEndpoint().getId(), r.getEndpoint().getName(),
                        "rule '" + r.getName() + "' (" + r.getType().label() + ")"));
            }
        }
        result.sort(Comparator.comparing(PayloadUsage::kind)
                .thenComparing(PayloadUsage::ownerName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(PayloadUsage::detail));
        return result;
    }

    private void apply(Project project, Payload target, PayloadRequest request) {
        ValidationErrors errors = new ValidationErrors();
        List<Payload> all = payloads.findByProjectId(project.getId());
        Map<UUID, Payload> byId = all.stream().collect(Collectors.toMap(Payload::getId, Function.identity()));

        String name = ValidationErrors.trimToNull(request.name());
        String nameError = IdentifierRules.checkTypeName(name);
        if (nameError != null) {
            errors.add("name", nameError);
        } else {
            errors.maxLength("name", name, 100);
            all.stream()
                    .filter(p -> !p.getId().equals(target.getId()) && p.getName().equalsIgnoreCase(name))
                    .findFirst()
                    .ifPresent(p -> errors.add("name", "another payload is already called '" + p.getName() + "'"));
        }
        errors.maxLength("description", request.description(), 4000);
        String example = ValidationErrors.trimToNull(request.example());
        if (example != null) {
            errors.maxLength("example", example, 20000);
            try {
                JSON.readTree(example);
            } catch (JacksonException e) {
                errors.add("example", "must be valid JSON");
            }
        }

        List<FieldDto> requested = request.fields() == null ? List.of() : request.fields();
        List<PayloadField> fields = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < requested.size(); i++) {
            FieldDto dto = requested.get(i);
            String at = "fields[" + i + "].";
            if (dto == null) {
                errors.add("fields[" + i + "]", "is empty");
                continue;
            }
            PayloadField field = field(dto, at, byId, errors);
            if (field.getName() != null && !seen.add(field.getName().toLowerCase(Locale.ROOT))) {
                errors.add(at + "name", "another field already has this name (names must differ in more than letter case)");
            }
            fields.add(field);
        }

        if (!project.isAllowCircularReferences() && errors.isEmpty()) {
            Map<UUID, Set<UUID>> edges = PayloadGraphs.edges(all);
            edges.put(target.getId(), PayloadGraphs.refs(fields));
            new PayloadGraph(edges).cycleThrough(target.getId()).ifPresent(cycle -> {
                Map<UUID, String> names = new HashMap<>();
                byId.values().forEach(p -> names.put(p.getId(), p.getName()));
                names.put(target.getId(), name);
                errors.add("fields", "this would make payloads refer to each other in a circle: "
                        + PayloadGraphs.describe(cycle, names)
                        + ". Allow circular references in the project settings if that is intended.");
            });
        }
        errors.throwIfAny();

        target.setName(name);
        target.setDescription(ValidationErrors.trimToNull(request.description()));
        target.setExample(example);
        target.replaceFields(fields);
    }

    private static PayloadField field(FieldDto dto, String at, Map<UUID, Payload> byId, ValidationErrors errors) {
        PayloadField f = new PayloadField();
        String name = ValidationErrors.trimToNull(dto.name());
        String nameError = IdentifierRules.checkIdentifier(name);
        if (nameError != null) {
            errors.add(at + "name", nameError);
        } else {
            errors.maxLength(at + "name", name, 100);
        }
        f.setName(name);

        FieldType type = dto.type();
        if (type == null) {
            errors.add(at + "type", "is required");
        }
        f.setType(type);
        if (type == FieldType.ARRAY) {
            if (dto.itemType() == null) {
                errors.add(at + "itemType", "an ARRAY field needs an item type");
            } else if (dto.itemType() == FieldType.ARRAY) {
                errors.add(at + "itemType", "arrays of arrays are not supported; wrap the inner array in a payload");
            }
            f.setItemType(dto.itemType());
        }

        boolean needsRef = type == FieldType.OBJECT || (type == FieldType.ARRAY && dto.itemType() == FieldType.OBJECT);
        if (needsRef) {
            if (dto.refPayloadId() == null) {
                errors.add(at + "refPayloadId", "choose the payload this field holds");
            } else if (!byId.containsKey(dto.refPayloadId())) {
                errors.add(at + "refPayloadId", "no payload with this id in the project");
            } else {
                f.setRefPayload(byId.get(dto.refPayloadId()));
            }
        }

        FieldType valueType = type == FieldType.ARRAY ? dto.itemType() : type;
        boolean lengthApplies = type == FieldType.STRING || type == FieldType.ARRAY;
        if (dto.minLength() != null || dto.maxLength() != null) {
            if (!lengthApplies) {
                errors.add(at + "minLength", "length limits only apply to STRING and ARRAY fields");
            } else if ((dto.minLength() != null && dto.minLength() < 0) || (dto.maxLength() != null && dto.maxLength() < 0)) {
                errors.add(at + "minLength", "length limits cannot be negative");
            } else if (dto.minLength() != null && dto.maxLength() != null && dto.minLength() > dto.maxLength()) {
                errors.add(at + "maxLength", "must be at least minLength");
            }
        }
        if (dto.minimum() != null || dto.maximum() != null) {
            if (valueType == null || !valueType.isNumeric()) {
                errors.add(at + "minimum", "minimum and maximum only apply to numeric fields");
            } else if (dto.minimum() != null && dto.maximum() != null && dto.minimum() > dto.maximum()) {
                errors.add(at + "maximum", "must be at least minimum");
            }
        }
        String pattern = ValidationErrors.trimToNull(dto.pattern());
        if (pattern != null) {
            if (valueType != FieldType.STRING) {
                errors.add(at + "pattern", "a pattern only applies to STRING fields");
            } else {
                errors.maxLength(at + "pattern", pattern, 500);
                try {
                    Pattern.compile(pattern);
                } catch (PatternSyntaxException e) {
                    errors.add(at + "pattern", "not a valid regular expression: " + e.getDescription());
                }
            }
        }
        List<String> enumValues = dto.enumValues() == null ? List.of() : dto.enumValues();
        if (!enumValues.isEmpty()) {
            if (valueType != FieldType.STRING) {
                errors.add(at + "enumValues", "allowed values only apply to STRING fields");
            } else if (enumValues.stream().anyMatch(v -> v == null || v.isBlank())) {
                errors.add(at + "enumValues", "values cannot be empty");
            } else if (new HashSet<>(enumValues).size() != enumValues.size()) {
                errors.add(at + "enumValues", "values must be unique");
            }
        }
        errors.maxLength(at + "format", dto.format(), 50);
        errors.maxLength(at + "description", dto.description(), 2000);
        errors.maxLength(at + "example", dto.example(), 1000);

        f.setRequired(Boolean.TRUE.equals(dto.required()));
        f.setFormat(ValidationErrors.trimToNull(dto.format()));
        f.setDescription(ValidationErrors.trimToNull(dto.description()));
        f.setExample(ValidationErrors.trimToNull(dto.example()));
        f.setMinLength(dto.minLength());
        f.setMaxLength(dto.maxLength());
        f.setMinimum(dto.minimum());
        f.setMaximum(dto.maximum());
        f.setPattern(pattern);
        f.setEnumValues(enumValues);
        return f;
    }

    static String uniqueName(String base, Set<String> takenLowerCase) {
        String candidate = base;
        for (int n = 2; takenLowerCase.contains(candidate.toLowerCase(Locale.ROOT)); n++) {
            candidate = base + n;
        }
        return candidate;
    }
}
