package dev.specbuilder.service;

import dev.specbuilder.api.dto.RuleChainView;
import dev.specbuilder.api.dto.RuleOrderRequest;
import dev.specbuilder.api.dto.RuleRequest;
import dev.specbuilder.api.dto.RuleView;
import dev.specbuilder.domain.Endpoint;
import dev.specbuilder.domain.Payload;
import dev.specbuilder.domain.ProcessingRule;
import dev.specbuilder.domain.rule.PersistConfig;
import dev.specbuilder.domain.rule.RuleConfig;
import dev.specbuilder.expression.ExpressionParser;
import dev.specbuilder.repository.EndpointRepository;
import dev.specbuilder.repository.PayloadRepository;
import dev.specbuilder.domain.rule.TransformConfig;
import dev.specbuilder.validation.IdentifierRules;
import dev.specbuilder.validation.NotFoundException;
import dev.specbuilder.validation.ValidationErrors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class RuleService {

    private final EndpointService endpointService;
    private final PayloadRepository payloads;
    private final RuleConfigCodec codec;
    private final EndpointRepository endpointRepository;

    public RuleService(EndpointService endpointService, PayloadRepository payloads, RuleConfigCodec codec,
                       EndpointRepository endpointRepository) {
        this.endpointRepository = endpointRepository;
        this.endpointService = endpointService;
        this.payloads = payloads;
        this.codec = codec;
    }

    @Transactional(readOnly = true)
    public RuleChainView chain(UUID projectId, UUID endpointId) {
        return chainView(endpointService.require(projectId, endpointId));
    }

    @Transactional(readOnly = true)
    public RuleView get(UUID projectId, UUID endpointId, UUID ruleId) {
        Endpoint endpoint = endpointService.require(projectId, endpointId);
        return view(endpoint, require(endpoint, ruleId));
    }

    public RuleView create(UUID projectId, UUID endpointId, RuleRequest request) {
        Endpoint endpoint = endpointService.require(projectId, endpointId);
        ProcessingRule rule = new ProcessingRule(endpoint);
        apply(endpoint, rule, request);
        int size = endpoint.getRules().size();
        int position = request.position() == null ? size : Math.clamp(request.position(), 0, size);
        endpoint.getRules().add(position, rule);
        endpoint.renumberRules();
        endpoint.getProject().touch();
        return view(endpoint, rule);
    }

    public RuleView update(UUID projectId, UUID endpointId, UUID ruleId, RuleRequest request) {
        Endpoint endpoint = endpointService.require(projectId, endpointId);
        ProcessingRule rule = require(endpoint, ruleId);
        apply(endpoint, rule, request);
        endpoint.getProject().touch();
        return view(endpoint, rule);
    }

    public void delete(UUID projectId, UUID endpointId, UUID ruleId) {
        Endpoint endpoint = endpointService.require(projectId, endpointId);
        endpoint.getRules().remove(require(endpoint, ruleId));
        endpoint.renumberRules();
        endpoint.getProject().touch();
    }

    /** Puts the rules in the given order. The list must name every rule of the endpoint exactly once. */
    public RuleChainView reorder(UUID projectId, UUID endpointId, RuleOrderRequest request) {
        Endpoint endpoint = endpointService.require(projectId, endpointId);
        List<UUID> ids = request.ruleIds() == null ? List.of() : request.ruleIds();
        Map<UUID, ProcessingRule> byId = endpoint.getRules().stream()
                .collect(Collectors.toMap(ProcessingRule::getId, Function.identity()));
        if (ids.size() != byId.size() || !byId.keySet().equals(new HashSet<>(ids))) {
            throw ValidationErrors.single("ruleIds", "list every rule of this endpoint exactly once ("
                    + byId.size() + " rules)");
        }
        List<ProcessingRule> ordered = ids.stream().map(byId::get).toList();
        Endpoint.replace(endpoint.getRules(), new ArrayList<>(ordered));
        endpoint.renumberRules();
        endpoint.getProject().touch();
        return chainView(endpoint);
    }

    private void apply(Endpoint endpoint, ProcessingRule target, RuleRequest request) {
        ValidationErrors errors = new ValidationErrors();
        if (request.type() == null) {
            errors.add("type", "is required");
        }
        String name = ValidationErrors.trimToNull(request.name());
        if (name == null) {
            errors.add("name", "is required");
        } else {
            errors.maxLength("name", name, 100);
            endpoint.getRules().stream()
                    .filter(r -> r != target && r.getName().equalsIgnoreCase(name))
                    .findFirst()
                    .ifPresent(r -> errors.add("name", "another rule on this endpoint is already called '" + r.getName() + "'"));
        }
        String condition = ValidationErrors.trimToNull(request.condition());
        errors.maxLength("condition", condition, 500);
        if (condition != null) {
            String problem = ExpressionParser.problem(condition);
            if (problem != null) {
                errors.add("condition", problem);
            }
        }

        RuleConfig config = request.type() == null ? null : codec.parse(request.type(), request.config(), errors);
        if (config != null) {
            Map<UUID, Payload> projectPayloads = projectPayloads(endpoint);
            for (UUID ref : config.payloadRefs().stream().sorted().toList()) {
                if (!projectPayloads.containsKey(ref)) {
                    errors.add("config", "refers to payload " + ref + ", which is not in this project");
                }
            }
            if (config instanceof PersistConfig p && p.entityName() != null
                    && IdentifierRules.RESERVED_TYPE_NAMES.contains(p.entityName())) {
                errors.add("config.entityName", "'" + p.entityName() + "' clashes with a Java class the generated code uses");
            }
        }
        errors.throwIfAny();

        target.setType(request.type());
        target.setName(name);
        target.setCondition(condition);
        target.setConfig(codec.write(config));
    }

    private ProcessingRule require(Endpoint endpoint, UUID ruleId) {
        return endpoint.getRules().stream().filter(r -> r.getId().equals(ruleId)).findFirst()
                .orElseThrow(() -> new NotFoundException("Rule", ruleId));
    }

    private Map<UUID, Payload> projectPayloads(Endpoint endpoint) {
        return payloads.findByProjectId(endpoint.getProject().getId()).stream()
                .collect(Collectors.toMap(Payload::getId, Function.identity()));
    }

    private RuleChainAnalyzer.Result analyze(Endpoint endpoint) {
        List<RuleChainAnalyzer.Step> steps = endpoint.getRules().stream()
                .map(r -> new RuleChainAnalyzer.Step(r, codec.read(r.getType(), r.getConfig())))
                .toList();
        Map<UUID, Payload> all = projectPayloads(endpoint);
        return RuleChainAnalyzer.analyze(endpoint, steps, all, storedEntities(endpoint, all));
    }

    /**
     * What each entity holds, as code generation works it out: the payload a SAVE rule stores, in any
     * endpoint of the project. A FIND_BY_ID of that entity gives back this payload.
     */
    private Map<String, Payload> storedEntities(Endpoint endpoint, Map<UUID, Payload> all) {
        Map<String, Payload> stored = new HashMap<>();
        List<Endpoint> endpoints = endpointRepository.findByProjectId(endpoint.getProject().getId());
        for (int pass = 0; pass < 2; pass++) { // twice, so a lookup before the SAVE that defines it is followed
            for (Endpoint e : endpoints) {
                Payload current = e.inputPayload();
                for (ProcessingRule r : e.getRules()) {
                    RuleConfig config;
                    try {
                        config = codec.read(r.getType(), r.getConfig());
                    } catch (RuntimeException unreadable) {
                        continue;
                    }
                    if (config instanceof TransformConfig t && t.targetPayloadId() != null) {
                        current = all.get(t.targetPayloadId());
                    } else if (config instanceof PersistConfig p && p.operation() == PersistConfig.Operation.SAVE && current != null) {
                        stored.putIfAbsent(p.entityName(), current);
                    } else if (config instanceof PersistConfig p && p.operation() == PersistConfig.Operation.FIND_BY_ID
                            && (p.targetField() == null || p.targetField().isBlank()) && stored.containsKey(p.entityName())) {
                        current = stored.get(p.entityName());
                    }
                }
            }
        }
        return stored;
    }

    private RuleView view(Endpoint endpoint, ProcessingRule rule) {
        return view(rule, analyze(endpoint).summaries().get(rule.getId()));
    }

    private RuleView view(ProcessingRule r, String summary) {
        return new RuleView(r.getId(), r.getEndpoint().getId(), r.getOrdinal(), r.getType(), r.getType().label(),
                r.getName(), r.getCondition(), codec.tree(r.getConfig()), summary);
    }

    private RuleChainView chainView(Endpoint endpoint) {
        RuleChainAnalyzer.Result result = analyze(endpoint);
        List<RuleView> views = endpoint.getRules().stream().map(r -> view(r, result.summaries().get(r.getId()))).toList();
        List<String> summary = new ArrayList<>();
        for (int i = 0; i < views.size(); i++) {
            summary.add((i + 1) + ". " + views.get(i).summary());
        }
        Payload input = endpoint.inputPayload();
        return new RuleChainView(endpoint.getId(), endpoint.getName(), endpoint.effectiveImplementationStyle(),
                input == null ? null : input.getId(), input == null ? null : input.getName(), views, summary, result.warnings());
    }
}
