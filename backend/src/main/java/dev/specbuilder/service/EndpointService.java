package dev.specbuilder.service;

import dev.specbuilder.api.dto.EndpointRequest;
import dev.specbuilder.api.dto.EndpointView;
import dev.specbuilder.api.dto.ParameterDto;
import dev.specbuilder.api.dto.ResponseDto;
import dev.specbuilder.domain.*;
import dev.specbuilder.repository.EndpointRepository;
import dev.specbuilder.repository.PayloadRepository;
import dev.specbuilder.validation.IdentifierRules;
import dev.specbuilder.validation.NotFoundException;
import dev.specbuilder.validation.PathTemplate;
import dev.specbuilder.validation.ValidationErrors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Transactional
public class EndpointService {

    private static final Pattern STATUS = Pattern.compile("[1-5][0-9][0-9]");
    private static final Pattern QUERY_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]*");
    private static final Pattern HEADER_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9-]*");
    private static final Pattern CHANNEL = Pattern.compile("[A-Za-z0-9._/-]+");
    private static final Set<HttpMethod> NO_BODY = EnumSet.of(HttpMethod.GET, HttpMethod.HEAD);

    private final ProjectService projectService;
    private final EndpointRepository endpoints;
    private final PayloadRepository payloads;

    public EndpointService(ProjectService projectService, EndpointRepository endpoints, PayloadRepository payloads) {
        this.projectService = projectService;
        this.endpoints = endpoints;
        this.payloads = payloads;
    }

    @Transactional(readOnly = true)
    public List<EndpointView> list(UUID projectId) {
        projectService.require(projectId);
        return endpoints.findByProjectId(projectId).stream()
                .sorted(Comparator.comparing(Endpoint::getKind).thenComparing(Endpoint::getName, String.CASE_INSENSITIVE_ORDER))
                .map(Views::endpoint)
                .toList();
    }

    @Transactional(readOnly = true)
    public EndpointView get(UUID projectId, UUID id) {
        return Views.endpoint(require(projectId, id));
    }

    public EndpointView create(UUID projectId, EndpointRequest request) {
        Project project = projectService.require(projectId);
        Endpoint endpoint = new Endpoint(project);
        apply(project, endpoint, request);
        endpoints.save(endpoint);
        project.touch();
        return Views.endpoint(endpoint);
    }

    public EndpointView update(UUID projectId, UUID id, EndpointRequest request) {
        Endpoint endpoint = require(projectId, id);
        apply(endpoint.getProject(), endpoint, request);
        endpoint.getProject().touch();
        return Views.endpoint(endpoint);
    }

    public void delete(UUID projectId, UUID id) {
        Endpoint endpoint = require(projectId, id);
        endpoint.getProject().touch();
        endpoints.delete(endpoint);
    }

    /** Copies the endpoint and its rules. A REST copy gets "/copy" added to its path so the route stays unique. */
    public EndpointView duplicate(UUID projectId, UUID id) {
        Endpoint source = require(projectId, id);
        Project project = source.getProject();
        List<Endpoint> all = endpoints.findByProjectId(projectId);
        Set<String> names = all.stream().map(e -> e.getName().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());

        Endpoint copy = new Endpoint(project);
        copy.setKind(source.getKind());
        copy.setName(PayloadService.uniqueName(source.getName() + "Copy", names));
        copy.setSummary(source.getSummary());
        copy.setDescription(source.getDescription());
        copy.setImplementationStyle(source.getImplementationStyle());
        copy.getTags().addAll(source.getTags());
        source.getParameters().forEach(p -> copy.getParameters().add(p.copy()));
        if (source.getKind() == EndpointKind.REST) {
            copy.setHttpMethod(source.getHttpMethod());
            copy.setRequestPayload(source.getRequestPayload());
            source.getResponses().forEach(r -> copy.getResponses().add(r.copy()));
            copy.getSecurity().addAll(source.getSecurity());
            Set<String> routes = all.stream()
                    .filter(e -> e.getHttpMethod() == source.getHttpMethod())
                    .map(Endpoint::getPathKey).collect(Collectors.toSet());
            String base = "/".equals(source.getPath()) ? "/copy" : source.getPath() + "/copy";
            String path = base;
            for (int n = 2; routes.contains(PathTemplate.parse(path).key()); n++) {
                path = base + n;
            }
            copy.setPath(path);
            copy.setPathKey(PathTemplate.parse(path).key());
        } else {
            copy.setChannel(source.getChannel());
            copy.setAsyncOperation(source.getAsyncOperation());
            copy.setProtocol(source.getProtocol());
            copy.setMessagePayload(source.getMessagePayload());
        }
        for (ProcessingRule rule : source.getRules()) {
            ProcessingRule r = new ProcessingRule(copy);
            r.setType(rule.getType());
            r.setName(rule.getName());
            r.setCondition(rule.getCondition());
            r.setConfig(rule.getConfig());
            r.setErrorCodeIds(rule.getErrorCodeIds());
            copy.getRules().add(r);
        }
        copy.renumberRules();
        endpoints.save(copy);
        project.touch();
        return Views.endpoint(copy);
    }

    Endpoint require(UUID projectId, UUID id) {
        return endpoints.findByIdAndProjectId(id, projectId).orElseThrow(() -> new NotFoundException("Endpoint", id));
    }

    private void apply(Project project, Endpoint target, EndpointRequest request) {
        ValidationErrors errors = new ValidationErrors();
        List<Endpoint> others = endpoints.findByProjectId(project.getId()).stream()
                .filter(e -> !e.getId().equals(target.getId())).toList();
        Map<UUID, Payload> projectPayloads = payloads.findByProjectId(project.getId()).stream()
                .collect(Collectors.toMap(Payload::getId, Function.identity()));

        EndpointKind kind = request.kind();
        if (kind == null) {
            errors.add("kind", "is required (REST or ASYNC)");
        }
        String name = ValidationErrors.trimToNull(request.name());
        String nameError = IdentifierRules.checkMemberName(name);
        if (nameError != null) {
            errors.add("name", nameError);
        } else {
            errors.maxLength("name", name, 100);
            others.stream().filter(e -> e.getName().equalsIgnoreCase(name)).findFirst()
                    .ifPresent(e -> errors.add("name", "another endpoint is already called '" + e.getName() + "'"));
        }
        errors.maxLength("summary", request.summary(), 500);
        errors.maxLength("description", request.description(), 4000);
        List<String> tags = tags(request.tags(), errors);
        List<ParameterDefinition> parameters = parameters(kind, request.parameters(), errors);

        String path = null;
        String pathKey = null;
        Payload requestPayload = null;
        Payload messagePayload = null;
        List<ResponseDefinition> responses = List.of();
        List<SecurityScheme> security = List.of();
        String channel = null;

        if (kind == EndpointKind.REST) {
            if (request.httpMethod() == null) {
                errors.add("httpMethod", "is required");
            }
            path = ValidationErrors.trimToNull(request.path());
            PathTemplate.Parsed parsed = PathTemplate.parse(path);
            parsed.errors().forEach(e -> errors.add("path", e));
            if (parsed.valid()) {
                pathKey = parsed.key();
                checkPathParameters(parsed.parameterNames(), parameters, errors);
                String key = pathKey;
                others.stream()
                        .filter(e -> e.getHttpMethod() == request.httpMethod() && key.equals(e.getPathKey()))
                        .findFirst()
                        .ifPresent(e -> errors.add("path", request.httpMethod() + " " + e.getPath()
                                + " is already defined by endpoint '" + e.getName() + "'"));
            }
            if (request.requestPayloadId() != null) {
                if (NO_BODY.contains(request.httpMethod())) {
                    errors.add("requestPayloadId", request.httpMethod() + " requests cannot have a body");
                }
                requestPayload = payload(request.requestPayloadId(), "requestPayloadId", projectPayloads, errors);
            }
            responses = responses(request.responses(), projectPayloads, errors);
            security = request.security() == null ? List.of()
                    : request.security().stream().filter(Objects::nonNull).distinct().toList();
        } else if (kind == EndpointKind.ASYNC) {
            channel = ValidationErrors.trimToNull(request.channel());
            if (channel == null) {
                errors.add("channel", "is required");
            } else if (!CHANNEL.matcher(channel).matches() || channel.length() > 249) {
                errors.add("channel", "use up to 249 letters, digits, '.', '_', '-' or '/'");
            }
            if (request.operation() == null) {
                errors.add("operation", "is required (SEND or RECEIVE)");
            }
            if (request.protocol() == null) {
                errors.add("protocol", "is required (KAFKA, JMS or AMQP)");
            }
            if (request.messagePayloadId() == null) {
                errors.add("messagePayloadId", "choose the message payload");
            } else {
                messagePayload = payload(request.messagePayloadId(), "messagePayloadId", projectPayloads, errors);
            }
        }
        errors.throwIfAny();

        target.setKind(kind);
        target.setName(name);
        target.setSummary(ValidationErrors.trimToNull(request.summary()));
        target.setDescription(ValidationErrors.trimToNull(request.description()));
        target.setImplementationStyle(request.implementationStyle());
        Endpoint.replace(target.getTags(), tags);
        Endpoint.replace(target.getParameters(), parameters);
        boolean rest = kind == EndpointKind.REST;
        target.setHttpMethod(rest ? request.httpMethod() : null);
        target.setPath(path);
        target.setPathKey(pathKey);
        target.setRequestPayload(requestPayload);
        Endpoint.replace(target.getResponses(), responses);
        Endpoint.replace(target.getSecurity(), security);
        target.setChannel(channel);
        target.setAsyncOperation(rest ? null : request.operation());
        target.setProtocol(rest ? null : request.protocol());
        target.setMessagePayload(messagePayload);
    }

    private static List<String> tags(List<String> requested, ValidationErrors errors) {
        List<String> tags = new ArrayList<>();
        if (requested == null) {
            return tags;
        }
        for (int i = 0; i < requested.size(); i++) {
            String tag = ValidationErrors.trimToNull(requested.get(i));
            if (tag == null) {
                continue;
            }
            errors.maxLength("tags[" + i + "]", tag, 100);
            if (tags.stream().noneMatch(t -> t.equalsIgnoreCase(tag))) {
                tags.add(tag);
            }
        }
        return tags;
    }

    private static List<ParameterDefinition> parameters(EndpointKind kind, List<ParameterDto> requested, ValidationErrors errors) {
        List<ParameterDefinition> result = new ArrayList<>();
        if (requested == null) {
            return result;
        }
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < requested.size(); i++) {
            ParameterDto p = requested.get(i);
            String at = "parameters[" + i + "].";
            if (p == null) {
                errors.add("parameters[" + i + "]", "is empty");
                continue;
            }
            String name = ValidationErrors.trimToNull(p.name());
            ParameterLocation location = p.location();
            if (location == null) {
                errors.add(at + "location", "is required (PATH, QUERY or HEADER)");
            } else if (kind == EndpointKind.ASYNC && location != ParameterLocation.HEADER) {
                errors.add(at + "location", "async endpoints only have HEADER parameters (message headers)");
            }
            if (name == null) {
                errors.add(at + "name", "is required");
            } else if (location != null) {
                String problem = switch (location) {
                    case PATH -> IdentifierRules.checkIdentifier(name);
                    case QUERY -> QUERY_NAME.matcher(name).matches() ? null : "use letters, digits, '_', '-' or '.'";
                    case HEADER -> HEADER_NAME.matcher(name).matches() ? null : "use letters, digits and '-'";
                };
                if (problem != null) {
                    errors.add(at + "name", problem);
                } else if (!seen.add(location + ":" + name.toLowerCase(Locale.ROOT))) {
                    errors.add(at + "name", "another " + location + " parameter already has this name");
                }
                errors.maxLength(at + "name", name, 100);
            }
            if (p.type() == null) {
                errors.add(at + "type", "is required");
            } else if (!p.type().isScalar()) {
                errors.add(at + "type", "parameters must be a simple type, not OBJECT or ARRAY");
            }
            errors.maxLength(at + "description", p.description(), 2000);
            boolean required = location == ParameterLocation.PATH || Boolean.TRUE.equals(p.required());
            result.add(new ParameterDefinition(name, location, p.type(), required, ValidationErrors.trimToNull(p.description())));
        }
        return result;
    }

    private static void checkPathParameters(Set<String> inPath, List<ParameterDefinition> parameters, ValidationErrors errors) {
        Set<String> declared = new LinkedHashSet<>();
        for (int i = 0; i < parameters.size(); i++) {
            ParameterDefinition p = parameters.get(i);
            if (p.getLocation() == ParameterLocation.PATH && p.getName() != null) {
                declared.add(p.getName());
                if (!inPath.contains(p.getName())) {
                    errors.add("parameters[" + i + "].name", "the path has no {" + p.getName() + "}");
                }
            }
        }
        for (String name : inPath) {
            if (!declared.contains(name)) {
                errors.add("parameters", "the path uses {" + name + "}: add a PATH parameter called " + name);
            }
        }
    }

    private static List<ResponseDefinition> responses(List<ResponseDto> requested, Map<UUID, Payload> projectPayloads, ValidationErrors errors) {
        List<ResponseDefinition> result = new ArrayList<>();
        if (requested == null || requested.stream().allMatch(Objects::isNull)) {
            errors.add("responses", "add at least one response");
            return result;
        }
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < requested.size(); i++) {
            ResponseDto r = requested.get(i);
            String at = "responses[" + i + "].";
            if (r == null) {
                continue;
            }
            String status = ValidationErrors.trimToNull(r.statusCode());
            if (status == null) {
                errors.add(at + "statusCode", "is required");
            } else if (!(STATUS.matcher(status).matches() || "default".equals(status))) {
                errors.add(at + "statusCode", "use a status code from 100 to 599, or 'default'");
            } else if (!seen.add(status)) {
                errors.add(at + "statusCode", "another response already uses " + status);
            }
            errors.maxLength(at + "description", r.description(), 2000);
            Payload payload = r.payloadId() == null ? null : payload(r.payloadId(), at + "payloadId", projectPayloads, errors);
            result.add(new ResponseDefinition(status, ValidationErrors.trimToNull(r.description()), payload));
        }
        return result;
    }

    private static Payload payload(UUID id, String field, Map<UUID, Payload> projectPayloads, ValidationErrors errors) {
        Payload p = projectPayloads.get(id);
        if (p == null) {
            errors.add(field, "no payload with this id in the project");
        }
        return p;
    }
}
