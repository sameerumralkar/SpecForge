package dev.specbuilder.service;

import dev.specbuilder.api.dto.ErrorCodeRequest;
import dev.specbuilder.api.dto.ErrorCodeView;
import dev.specbuilder.api.dto.ErrorLink;
import dev.specbuilder.api.dto.ErrorLinksRequest;
import dev.specbuilder.domain.*;
import dev.specbuilder.domain.rule.RuleConfig;
import dev.specbuilder.domain.rule.ValidateConfig;
import dev.specbuilder.repository.ErrorCodeRepository;
import dev.specbuilder.repository.FieldDefinitionRepository;
import dev.specbuilder.repository.PayloadRepository;
import dev.specbuilder.repository.ProcessingRuleRepository;
import dev.specbuilder.validation.NotFoundException;
import dev.specbuilder.validation.ValidationErrors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The project's error-code inventory, and the links from codes to payload fields and processing rules.
 * A link lives on the field or rule (as a list of code ids), so it follows renames and duplicates.
 */
@Service
@Transactional
public class ErrorCodeService {

    /** PAY-001, SGQR_AMOUNT_INVALID, E1001, orders.not-found is too lower-case: codes are upper-case. */
    static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9]*([-_.][A-Z0-9]+)*");

    private final ProjectService projectService;
    private final ErrorCodeRepository codes;
    private final PayloadRepository payloads;
    private final ProcessingRuleRepository rules;
    private final RuleConfigCodec codec;
    private final FieldDefinitionRepository definitions;

    public ErrorCodeService(ProjectService projectService, ErrorCodeRepository codes, PayloadRepository payloads,
                            ProcessingRuleRepository rules, RuleConfigCodec codec, FieldDefinitionRepository definitions) {
        this.definitions = definitions;
        this.projectService = projectService;
        this.codes = codes;
        this.payloads = payloads;
        this.rules = rules;
        this.codec = codec;
    }

    @Transactional(readOnly = true)
    public List<ErrorCodeView> list(UUID projectId) {
        projectService.require(projectId);
        Map<UUID, List<ErrorLink>> links = links(projectId);
        return codes.findByProjectId(projectId).stream()
                .sorted(Comparator.comparing(ErrorCode::getCode))
                .map(c -> view(c, links.getOrDefault(c.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ErrorCodeView get(UUID projectId, UUID id) {
        return view(require(projectId, id));
    }

    public ErrorCodeView create(UUID projectId, ErrorCodeRequest request) {
        Project project = projectService.require(projectId);
        ErrorCode code = new ErrorCode(project);
        apply(code, request);
        codes.save(code);
        project.touch();
        return view(code, List.of());
    }

    public ErrorCodeView update(UUID projectId, UUID id, ErrorCodeRequest request) {
        ErrorCode code = require(projectId, id);
        apply(code, request);
        code.getProject().touch();
        return view(code);
    }

    /** Deletes the code and takes it off every field, field-inventory entry, rule and check that used it. */
    public void delete(UUID projectId, UUID id) {
        ErrorCode code = require(projectId, id);
        for (FieldDefinition d : definitions.findByProjectId(projectId)) {
            if (d.getField().getErrorCodeIds().contains(id)) {
                d.getField().setErrorCodeIds(without(d.getField().getErrorCodeIds(), id));
            }
        }
        for (Payload p : payloads.findByProjectId(projectId)) {
            for (PayloadField f : p.getFields()) {
                if (f.getErrorCodeIds().contains(id)) {
                    f.setErrorCodeIds(without(f.getErrorCodeIds(), id));
                }
            }
        }
        for (ProcessingRule r : rules.findByProjectId(projectId)) {
            if (r.getErrorCodeIds().contains(id)) {
                r.setErrorCodeIds(without(r.getErrorCodeIds(), id));
            }
            if (readConfig(r) instanceof ValidateConfig v && v.errorCodeRefs().contains(id)) {
                r.setConfig(codec.write(new ValidateConfig(v.checks().stream()
                        .map(c -> id.equals(c.errorCodeId()) ? new ValidateConfig.Check(c.field(), c.kind(), c.value(), c.message()) : c)
                        .toList(), v.failureStatus())));
            }
        }
        code.getProject().touch();
        codes.delete(code);
    }

    /**
     * Links the code to exactly the given fields and rules: it is added (last) to the ones it wasn't on, and
     * taken off the ones not listed. Checks that pick the code in their own settings are left alone.
     */
    public ErrorCodeView setLinks(UUID projectId, UUID id, ErrorLinksRequest request) {
        ErrorCode code = require(projectId, id);
        List<ErrorLink> wanted = request == null || request.links() == null ? List.of() : request.links();
        List<Payload> projectPayloads = payloads.findByProjectId(projectId);
        Map<UUID, Payload> payloadsById = projectPayloads.stream().collect(Collectors.toMap(Payload::getId, Function.identity()));
        Map<UUID, ProcessingRule> rulesById = rules.findByProjectId(projectId).stream()
                .collect(Collectors.toMap(ProcessingRule::getId, Function.identity()));

        ValidationErrors errors = new ValidationErrors();
        Set<PayloadField> fields = new HashSet<>();
        Set<ProcessingRule> steps = new HashSet<>();
        for (int i = 0; i < wanted.size(); i++) {
            ErrorLink l = wanted.get(i);
            String at = "links[" + i + "]";
            if (l == null || l.kind() == null) {
                errors.add(at + ".kind", "choose FIELD or RULE");
            } else if (l.kind() == ErrorLink.Kind.FIELD) {
                Payload p = l.payloadId() == null ? null : payloadsById.get(l.payloadId());
                if (p == null) {
                    errors.add(at + ".payloadId", "no payload with this id in the project");
                    continue;
                }
                p.getFields().stream().filter(f -> f.getName().equals(l.field())).findFirst().ifPresentOrElse(fields::add,
                        () -> errors.add(at + ".field", p.getName() + " has no field '" + l.field() + "'"));
            } else if (l.kind() == ErrorLink.Kind.RULE) {
                ProcessingRule r = l.ruleId() == null ? null : rulesById.get(l.ruleId());
                if (r == null) {
                    errors.add(at + ".ruleId", "no rule with this id in the project");
                } else {
                    steps.add(r);
                }
            }
        }
        errors.throwIfAny();

        for (Payload p : projectPayloads) {
            for (PayloadField f : p.getFields()) {
                f.setErrorCodeIds(toggle(f.getErrorCodeIds(), id, fields.contains(f)));
            }
        }
        for (ProcessingRule r : rulesById.values()) {
            r.setErrorCodeIds(toggle(r.getErrorCodeIds(), id, steps.contains(r)));
        }
        code.getProject().touch();
        return view(code);
    }

    /** Adds an error for each id that is not one of the project's codes; {@code at} names the list. */
    @Transactional(readOnly = true)
    public void checkIds(UUID projectId, Collection<UUID> ids, String at, ValidationErrors errors) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        Set<UUID> known = codes.findByProjectId(projectId).stream().map(ErrorCode::getId).collect(Collectors.toSet());
        for (UUID id : ids) {
            if (id == null || !known.contains(id)) {
                errors.add(at, "refers to error code " + id + ", which is not in this project");
            }
        }
    }

    ErrorCode require(UUID projectId, UUID id) {
        return codes.findByIdAndProjectId(id, projectId).orElseThrow(() -> new NotFoundException("Error code", id));
    }

    // ------------------------------------------------------------------ internals

    private void apply(ErrorCode target, ErrorCodeRequest request) {
        ValidationErrors errors = new ValidationErrors();
        String code = ValidationErrors.trimToNull(request.code());
        if (code == null) {
            errors.add("code", "is required");
        } else if (code.length() > 40) {
            errors.add("code", "must be at most 40 characters");
        } else if (!CODE.matcher(code).matches()) {
            errors.add("code", "use upper-case letters and digits, separated by '-', '_' or '.', e.g. PAY-001");
        } else {
            codes.findByProjectId(target.getProject().getId()).stream()
                    .filter(c -> !c.getId().equals(target.getId()) && c.getCode().equalsIgnoreCase(code))
                    .findFirst()
                    .ifPresent(c -> errors.add("code", "the project already has " + c.getCode()));
        }
        String message = ValidationErrors.trimToNull(request.message());
        if (message == null) {
            errors.add("message", "is required");
        } else {
            errors.maxLength("message", message, 500);
        }
        if (request.httpStatus() != null && (request.httpStatus() < 400 || request.httpStatus() > 599)) {
            errors.add("httpStatus", "use an error status from 400 to 599");
        }
        if (request.category() == null) {
            errors.add("category", "is required");
        }
        errors.maxLength("description", request.description(), 2000);
        errors.throwIfAny();

        target.setCode(code);
        target.setMessage(message);
        target.setHttpStatus(request.httpStatus());
        target.setCategory(request.category());
        target.setDescription(ValidationErrors.trimToNull(request.description()));
    }

    private ErrorCodeView view(ErrorCode c) {
        return view(c, links(c.getProject().getId()).getOrDefault(c.getId(), List.of()));
    }

    private static ErrorCodeView view(ErrorCode c, List<ErrorLink> links) {
        return new ErrorCodeView(c.getId(), c.getProject().getId(), c.getCode(), c.getMessage(), c.getHttpStatus(),
                c.effectiveStatus(), c.getCategory(), c.getDescription(), links);
    }

    /** Every link in the project, by code id: fields (by payload name), then rules and checks (by endpoint and order). */
    private Map<UUID, List<ErrorLink>> links(UUID projectId) {
        Map<UUID, List<ErrorLink>> result = new HashMap<>();
        payloads.findByProjectId(projectId).stream()
                .sorted(Comparator.comparing(Payload::getName, String.CASE_INSENSITIVE_ORDER))
                .forEach(p -> p.getFields().forEach(f -> f.getErrorCodeIds().forEach(id -> result.computeIfAbsent(id, k -> new ArrayList<>())
                        .add(new ErrorLink(ErrorLink.Kind.FIELD, p.getId(), p.getName(), f.getName(), null, null, null, null, null,
                                p.getName() + "." + f.getName())))));
        rules.findByProjectId(projectId).stream()
                .sorted(Comparator.comparing((ProcessingRule r) -> r.getEndpoint().getName(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(ProcessingRule::getOrdinal))
                .forEach(r -> {
                    Endpoint e = r.getEndpoint();
                    r.getErrorCodeIds().forEach(id -> result.computeIfAbsent(id, k -> new ArrayList<>())
                            .add(new ErrorLink(ErrorLink.Kind.RULE, null, null, null, e.getId(), e.getName(), r.getId(), r.getName(),
                                    r.getType(), e.getName() + " › " + r.getName())));
                    if (readConfig(r) instanceof ValidateConfig v) {
                        for (int i = 0; i < v.checks().size(); i++) {
                            ValidateConfig.Check c = v.checks().get(i);
                            if (c.errorCodeId() != null) {
                                result.computeIfAbsent(c.errorCodeId(), k -> new ArrayList<>())
                                        .add(new ErrorLink(ErrorLink.Kind.CHECK, null, null, c.field(), e.getId(), e.getName(), r.getId(),
                                                r.getName(), r.getType(), e.getName() + " › " + r.getName() + " › check " + (i + 1)
                                                + ": " + c.field() + " " + c.kind()));
                            }
                        }
                    }
                });
        return result;
    }

    private RuleConfig readConfig(ProcessingRule r) {
        try {
            return codec.read(r.getType(), r.getConfig());
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    private static List<UUID> without(List<UUID> ids, UUID id) {
        return ids.stream().filter(x -> !x.equals(id)).toList();
    }

    private static List<UUID> toggle(List<UUID> ids, UUID id, boolean on) {
        if (on == ids.contains(id)) {
            return ids;
        }
        if (!on) {
            return without(ids, id);
        }
        List<UUID> more = new ArrayList<>(ids);
        more.add(id);
        return more;
    }
}
