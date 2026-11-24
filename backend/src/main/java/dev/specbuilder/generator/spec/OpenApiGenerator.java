package dev.specbuilder.generator.spec;

import dev.specbuilder.domain.ParameterLocation;
import dev.specbuilder.domain.SecurityScheme;
import dev.specbuilder.generator.*;
import dev.specbuilder.generator.ProjectSnapshot.*;
import dev.specbuilder.domain.rule.ValidateConfig;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static dev.specbuilder.generator.spec.Tree.obj;
import static dev.specbuilder.generator.spec.Tree.put;

/** OpenAPI 3.1 for the project's REST endpoints: openapi.yaml and openapi.json. */
@Component
public class OpenApiGenerator implements Generator {

    public static final String ID = "openapi";
    private static final String SCHEMAS = "#/components/schemas/";
    private static final Map<String, String> REASONS = Map.ofEntries(
            Map.entry("200", "OK"), Map.entry("201", "Created"), Map.entry("202", "Accepted"), Map.entry("204", "No Content"),
            Map.entry("301", "Moved Permanently"), Map.entry("302", "Found"), Map.entry("304", "Not Modified"),
            Map.entry("400", "Bad Request"), Map.entry("401", "Unauthorized"), Map.entry("403", "Forbidden"),
            Map.entry("404", "Not Found"), Map.entry("409", "Conflict"), Map.entry("422", "Unprocessable Content"),
            Map.entry("429", "Too Many Requests"), Map.entry("500", "Internal Server Error"), Map.entry("502", "Bad Gateway"),
            Map.entry("503", "Service Unavailable"), Map.entry("504", "Gateway Timeout"), Map.entry("default", "Unexpected error"));

    @Override
    public String id() {
        return ID;
    }

    @Override
    public GenerationResult generate(ProjectSnapshot project) {
        List<Problem> problems = new ArrayList<>();
        List<EndpointSnapshot> rest = project.restEndpoints().stream()
                .sorted(Comparator.comparing(EndpointSnapshot::path).thenComparing(e -> e.httpMethod().ordinal()))
                .toList();
        if (rest.isEmpty()) {
            problems.add(Problem.info(ID, "The project has no REST endpoints, so there is no OpenAPI document."));
            return new GenerationResult(List.of(), problems);
        }
        JsonSchemas schemas = new JsonSchemas(SCHEMAS, problems, ID);

        Map<String, Object> doc = obj();
        doc.put("openapi", "3.1.0");
        Map<String, Object> info = obj();
        info.put("title", project.name());
        info.put("version", project.version());
        put(info, "description", project.description());
        doc.put("info", info);

        SortedSet<String> tags = new TreeSet<>();
        rest.forEach(e -> tags.addAll(e.tags()));
        put(doc, "tags", tags.stream().map(t -> Map.<String, Object>of("name", t)).toList());

        Map<String, Object> paths = obj();
        for (EndpointSnapshot e : rest) {
            @SuppressWarnings("unchecked")
            Map<String, Object> item = (Map<String, Object>) paths.computeIfAbsent(e.path(), k -> obj());
            item.put(e.httpMethod().name().toLowerCase(Locale.ROOT), operation(e, project));
        }
        doc.put("paths", paths);

        Map<String, Object> components = obj();
        Set<String> roots = new TreeSet<>();
        rest.forEach(e -> {
            if (e.requestPayload() != null) roots.add(e.requestPayload());
            e.responses().forEach(r -> { if (r.payload() != null) roots.add(r.payload()); });
        });
        Map<String, Object> componentSchemas = obj();
        Map<String, PayloadSnapshot> byName = project.payloadsByName();
        for (String name : project.reachableFrom(roots)) {
            componentSchemas.put(name, schemas.payload(byName.get(name), "/components/schemas/" + Tree.escape(name)));
        }
        put(components, "schemas", componentSchemas);
        SortedSet<SecurityScheme> used = new TreeSet<>();
        rest.forEach(e -> used.addAll(e.security()));
        Map<String, Object> securitySchemes = obj();
        used.forEach(s -> securitySchemes.put(schemeName(s), schemeDefinition(s)));
        put(components, "securitySchemes", securitySchemes);
        put(doc, "components", components);
        put(doc, "x-error-codes", project.errorCodes().stream().map(OpenApiGenerator::errorCode).toList());

        if (used.contains(SecurityScheme.OAUTH2)) {
            problems.add(Problem.info(ID, "The OAuth2 scheme uses a placeholder token URL; replace it with your authorization server's."));
        }
        return new GenerationResult(List.of(
                new GeneratedFile("openapi.yaml", SpecWriter.yaml(doc), "application/yaml"),
                new GeneratedFile("openapi.json", SpecWriter.json(doc), "application/json")), problems);
    }

    private static Map<String, Object> operation(EndpointSnapshot e, ProjectSnapshot project) {
        Map<String, Object> op = obj();
        put(op, "tags", e.tags());
        put(op, "summary", e.summary());
        put(op, "description", e.description());
        op.put("operationId", e.name());
        List<Map<String, Object>> parameters = new ArrayList<>();
        for (ParameterSnapshot p : e.parameters()) {
            Map<String, Object> param = obj();
            param.put("name", p.name());
            param.put("in", p.location().name().toLowerCase(Locale.ROOT));
            param.put("required", p.location() == ParameterLocation.PATH || p.required());
            put(param, "description", p.description());
            param.put("schema", JsonSchemas.scalar(p.type()));
            parameters.add(param);
        }
        put(op, "parameters", parameters);
        if (e.requestPayload() != null) {
            Map<String, Object> body = obj();
            body.put("required", true);
            body.put("content", jsonContent(e.requestPayload()));
            op.put("requestBody", body);
        }
        Map<String, Object> responses = obj();
        e.responses().stream()
                .sorted(Comparator.comparing((ResponseSnapshot r) -> r.statusCode().equals("default")).thenComparing(ResponseSnapshot::statusCode))
                .forEach(r -> {
                    Map<String, Object> response = obj();
                    response.put("description", r.description() == null || r.description().isBlank()
                            ? REASONS.getOrDefault(r.statusCode(), "Response " + r.statusCode()) : r.description());
                    if (r.payload() != null) {
                        response.put("content", jsonContent(r.payload()));
                    }
                    responses.put(r.statusCode(), response);
                });
        List<ErrorCodeSnapshot> codes = errorCodes(e, project);
        Map<String, Object> sorted = responses;
        op.put("responses", sorted);
        put(op, "x-error-codes", codes.stream().map(OpenApiGenerator::errorCode).toList());
        put(op, "security", e.security().stream().map(s -> Map.<String, Object>of(schemeName(s), List.of())).toList());
        return op;
    }

    /**
     * The codes an operation can answer with: those linked to its rules and checks, then those on the fields of its
     * request body (and the objects inside it), each once, in that order.
     */
    static List<ErrorCodeSnapshot> errorCodes(EndpointSnapshot e, ProjectSnapshot project) {
        Set<String> codes = new LinkedHashSet<>();
        for (RuleSnapshot r : e.rules()) {
            if (r.errorCodes() != null) codes.addAll(r.errorCodes());
            if (r.config() instanceof ValidateConfig v) {
                v.checks().forEach(c -> project.errorCode(c.errorCodeId()).ifPresent(x -> codes.add(x.code())));
            }
        }
        String body = e.isRest() ? e.requestPayload() : e.messagePayload();
        if (body != null) {
            Map<String, PayloadSnapshot> byName = project.payloadsByName();
            for (String name : project.reachableFrom(List.of(body))) {
                byName.get(name).fields().forEach(f -> { if (f.errorCodes() != null) codes.addAll(f.errorCodes()); });
            }
        }
        return codes.stream().map(project::errorCode).flatMap(Optional::stream).toList();
    }

    static Map<String, Object> errorCode(ErrorCodeSnapshot c) {
        Map<String, Object> m = obj();
        m.put("code", c.code());
        m.put("status", c.status());
        m.put("category", c.category().name());
        m.put("message", c.message());
        put(m, "description", c.description());
        return m;
    }

    private static Map<String, Object> jsonContent(String payload) {
        Map<String, Object> media = obj();
        media.put("schema", Tree.ref(SCHEMAS + payload));
        Map<String, Object> content = obj();
        content.put("application/json", media);
        return content;
    }

    static String schemeName(SecurityScheme s) {
        return switch (s) {
            case BEARER_JWT -> "bearerAuth";
            case API_KEY -> "apiKeyAuth";
            case BASIC -> "basicAuth";
            case OAUTH2 -> "oauth2";
        };
    }

    private static Map<String, Object> schemeDefinition(SecurityScheme s) {
        Map<String, Object> m = obj();
        switch (s) {
            case BEARER_JWT -> { m.put("type", "http"); m.put("scheme", "bearer"); m.put("bearerFormat", "JWT"); }
            case API_KEY -> { m.put("type", "apiKey"); m.put("in", "header"); m.put("name", "X-API-Key"); }
            case BASIC -> { m.put("type", "http"); m.put("scheme", "basic"); }
            case OAUTH2 -> {
                m.put("type", "oauth2");
                Map<String, Object> flow = obj();
                flow.put("tokenUrl", "https://auth.example.com/oauth2/token");
                flow.put("scopes", obj());
                m.put("flows", Map.of("clientCredentials", flow));
            }
        }
        return m;
    }
}
