package dev.specbuilder.service;

import dev.specbuilder.api.dto.FieldDefinitionView;
import dev.specbuilder.api.dto.FieldDto;
import dev.specbuilder.domain.*;
import dev.specbuilder.repository.FieldDefinitionRepository;
import dev.specbuilder.repository.PayloadRepository;
import dev.specbuilder.validation.NotFoundException;
import dev.specbuilder.validation.ValidationErrors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * The project's field inventory: plain fields (scalars and arrays of scalars) with their constraints and
 * error codes, ready to copy into payloads. Objects belong in the object inventory instead.
 */
@Service
@Transactional
public class FieldDefinitionService {

    private final ProjectService projectService;
    private final FieldDefinitionRepository definitions;
    private final PayloadRepository payloads;
    private final ErrorCodeService errorCodes;

    public FieldDefinitionService(ProjectService projectService, FieldDefinitionRepository definitions, PayloadRepository payloads,
                                  ErrorCodeService errorCodes) {
        this.projectService = projectService;
        this.definitions = definitions;
        this.payloads = payloads;
        this.errorCodes = errorCodes;
    }

    @Transactional(readOnly = true)
    public List<FieldDefinitionView> list(UUID projectId) {
        projectService.require(projectId);
        Map<String, List<String>> usedIn = usedIn(projectId);
        return definitions.findByProjectId(projectId).stream()
                .sorted(Comparator.comparing((FieldDefinition d) -> d.getField().getName(), String.CASE_INSENSITIVE_ORDER))
                .map(d -> view(d, usedIn))
                .toList();
    }

    @Transactional(readOnly = true)
    public FieldDefinitionView get(UUID projectId, UUID id) {
        return view(require(projectId, id), usedIn(projectId));
    }

    public FieldDefinitionView create(UUID projectId, FieldDto request) {
        Project project = projectService.require(projectId);
        FieldDefinition definition = new FieldDefinition(project);
        apply(definition, request);
        definitions.save(definition);
        project.touch();
        return view(definition, usedIn(projectId));
    }

    public FieldDefinitionView update(UUID projectId, UUID id, FieldDto request) {
        FieldDefinition definition = require(projectId, id);
        apply(definition, request);
        definition.getProject().touch();
        return view(definition, usedIn(projectId));
    }

    public void delete(UUID projectId, UUID id) {
        FieldDefinition definition = require(projectId, id);
        definition.getProject().touch();
        definitions.delete(definition);
    }

    FieldDefinition require(UUID projectId, UUID id) {
        return definitions.findByIdAndProjectId(id, projectId).orElseThrow(() -> new NotFoundException("Field definition", id));
    }

    private void apply(FieldDefinition target, FieldDto request) {
        ValidationErrors errors = new ValidationErrors();
        if (request == null) {
            throw ValidationErrors.single("field", "is required");
        }
        UUID projectId = target.getProject().getId();
        if (request.type() == FieldType.OBJECT || (request.type() == FieldType.ARRAY && request.itemType() == FieldType.OBJECT)) {
            throw ValidationErrors.single("type", "the field inventory holds plain fields; put objects in the object inventory");
        }
        PayloadField field = PayloadService.field(request, "", Map.of(), errors);
        if (field.getName() != null) {
            definitions.findByProjectId(projectId).stream()
                    .filter(d -> !d.getId().equals(target.getId()) && d.getField().getName().equalsIgnoreCase(field.getName()))
                    .findFirst()
                    .ifPresent(d -> errors.add("name", "the field inventory already has '" + d.getField().getName() + "'"));
        }
        if (request.errorCodeIds() == null) {
            field.setErrorCodeIds(target.getField().getErrorCodeIds());
        } else {
            errorCodes.checkIds(projectId, request.errorCodeIds(), "errorCodeIds", errors);
            field.setErrorCodeIds(request.errorCodeIds());
        }
        errors.throwIfAny();
        target.setField(field);
    }

    private FieldDefinitionView view(FieldDefinition d, Map<String, List<String>> usedIn) {
        return new FieldDefinitionView(d.getId(), d.getProject().getId(), Views.field(d.getField()),
                usedIn.getOrDefault(d.getField().getName().toLowerCase(Locale.ROOT), List.of()));
    }

    /** "currency" → ["Money.currency", "Fee.currency"]: payload fields by lower-case name. */
    private Map<String, List<String>> usedIn(UUID projectId) {
        Map<String, List<String>> result = new HashMap<>();
        payloads.findByProjectId(projectId).stream()
                .sorted(Comparator.comparing(Payload::getName, String.CASE_INSENSITIVE_ORDER))
                .forEach(p -> p.getFields().forEach(f -> result.computeIfAbsent(f.getName().toLowerCase(Locale.ROOT), k -> new ArrayList<>())
                        .add(p.getName() + "." + f.getName())));
        return result;
    }
}
