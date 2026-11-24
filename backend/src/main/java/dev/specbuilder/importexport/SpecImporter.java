package dev.specbuilder.importexport;

import dev.specbuilder.api.dto.ParameterDto;
import dev.specbuilder.domain.*;
import dev.specbuilder.generator.code.Names;
import dev.specbuilder.importexport.ProjectExport.*;
import dev.specbuilder.validation.IdentifierRules;
import dev.specbuilder.validation.ValidationErrors;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Turns OpenAPI 3.x and AsyncAPI 2.x/3.0 documents (YAML or JSON) into a {@link ProjectExport}, which
 * is then imported like any project file. Several documents (e.g. an OpenAPI and an AsyncAPI file)
 * become one project. What the project model cannot hold is skipped or simplified, with a warning.
 */
public final class SpecImporter {

    public record Result(ProjectExport export, List<String> warnings) {
    }

    private static final YAMLMapper YAML = YAMLMapper.builder().build();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<String> HTTP_METHODS = List.of("get", "post", "put", "patch", "delete", "head", "options");
    private static final Pattern STATUS = Pattern.compile("[1-5][0-9][0-9]|default");
    private static final Pattern LITERAL_SEGMENT = Pattern.compile("[A-Za-z0-9._~-]+");
    private static final Pattern PARAM_SEGMENT = Pattern.compile("\\{([^}]+)}");
    private static final Pattern QUERY_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]*");
    private static final Pattern HEADER_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9-]*");
    private static final Pattern VERSION = Pattern.compile("[0-9A-Za-z][0-9A-Za-z.+_-]*");
    private static final Set<String> RESERVED_HEADERS = Set.of("accept", "content-type", "authorization");

    /** One input document and the payload names its component schemas got. */
    private static final class Doc {
        final int index;
        final JsonNode root;
        final String label;
        final Map<String, String> componentNames = new HashMap<>();

        Doc(int index, JsonNode root, String label) {
            this.index = index;
            this.root = root;
            this.label = label;
        }
    }

    private static final class PayloadBuilder {
        final String name;
        final int owner;
        String description;
        String example;
        List<FieldData> fields = List.of();

        PayloadBuilder(String name, int owner) {
            this.name = name;
            this.owner = owner;
        }
    }

    private final List<String> warnings = new ArrayList<>();
    private final Map<String, PayloadBuilder> payloads = new LinkedHashMap<>();
    private final List<EndpointData> endpoints = new ArrayList<>();
    private final Set<String> endpointNames = new HashSet<>();
    /** The x-error-codes catalogs of the documents, by code, and the codes fields refer to. */
    private final Map<String, ErrorCodeData> errorCatalog = new LinkedHashMap<>();
    private final Set<String> referencedCodes = new LinkedHashSet<>();
    private String title;
    private String version;
    private String description;

    private SpecImporter() {
    }

    /**
     * @param documents   the text of each file
     * @param name        project name, or null for the first document's title
     * @param basePackage Java base package, or null to derive one from the name
     */
    public static Result read(List<String> documents, String name, String basePackage) {
        if (documents == null || documents.isEmpty()) {
            throw ValidationErrors.single("documents", "add at least one OpenAPI or AsyncAPI file");
        }
        SpecImporter importer = new SpecImporter();
        for (int i = 0; i < documents.size(); i++) {
            importer.document(i, documents.get(i));
        }
        return importer.result(name, basePackage);
    }

    private void document(int index, String text) {
        JsonNode root;
        try {
            root = YAML.readTree(text == null ? "" : text);
        } catch (JacksonException e) {
            throw ValidationErrors.single("documents[" + index + "]", "is neither valid YAML nor JSON: " + e.getOriginalMessage());
        }
        if (root == null || !root.isObject()) {
            throw ValidationErrors.single("documents[" + index + "]", "is empty or not an object");
        }
        if (root.has("swagger")) {
            throw ValidationErrors.single("documents[" + index + "]", "Swagger 2.0 is not supported; convert it to OpenAPI 3 first");
        }
        String openapi = text(root, "openapi");
        String asyncapi = text(root, "asyncapi");
        String label = "file " + (index + 1);
        if (title == null) {
            JsonNode info = root.path("info");
            title = text(info, "title");
            version = text(info, "version");
            description = text(info, "description");
        }
        Doc doc = new Doc(index, root, label);
        errorCatalog(root.path("x-error-codes"));
        if (openapi != null && openapi.startsWith("3.")) {
            components(doc);
            openApi(doc);
        } else if (asyncapi != null && asyncapi.startsWith("3.")) {
            components(doc);
            asyncApi3(doc);
        } else if (asyncapi != null && asyncapi.startsWith("2.")) {
            components(doc);
            asyncApi2(doc);
        } else {
            throw ValidationErrors.single("documents[" + index + "]",
                    "is not an OpenAPI 3 or AsyncAPI 2/3 document (no openapi or asyncapi version at the top)");
        }
    }

    // ================================================================== result

    private Result result(String nameOverride, String basePackage) {
        String projectName = nameOverride != null && !nameOverride.isBlank() ? nameOverride.strip()
                : title != null && !title.isBlank() ? title.strip() : "Imported API";
        if (projectName.length() > 100) projectName = projectName.substring(0, 100);
        String projectVersion = version != null && VERSION.matcher(version).matches() && version.length() <= 50 ? version : "1.0.0";
        if (version != null && !version.equals(projectVersion)) {
            warn("version '" + version + "' is not a Maven version; using 1.0.0");
        }
        String pkg = basePackage != null && !basePackage.isBlank() ? basePackage.strip() : derivePackage(projectName);

        boolean cycles = hasCycle();
        if (cycles) {
            warn("payloads refer to each other in a circle, so circular references are allowed for this project");
        }
        List<PayloadData> payloadData = payloads.values().stream()
                .sorted(Comparator.comparing(b -> b.name))
                .map(b -> new PayloadData(b.name, b.description, b.example, b.fields)).toList();
        List<EndpointData> endpointData = endpoints.stream().sorted(Comparator.comparing(EndpointData::name)).toList();
        for (String code : referencedCodes) {
            if (!errorCatalog.containsKey(code)) {
                warn("error code " + code + " is used but not described in x-error-codes; imported with the code as its message");
                errorCatalog.put(code, new ErrorCodeData(code, code, null, ErrorCategory.VALIDATION, null));
            }
        }
        ProjectExport export = new ProjectExport(ProjectExport.FORMAT, ProjectExport.VERSION,
                new ProjectData(projectName, pkg, projectVersion, description, ImplementationStyle.IMPLEMENTATION_CLASS, cycles),
                payloadData, endpointData, List.copyOf(errorCatalog.values()));
        return new Result(export, List.copyOf(warnings));
    }

    /** Reads a SpecForge x-error-codes list: [{code, status, category, message, description}]. */
    private void errorCatalog(JsonNode list) {
        for (JsonNode c : list) {
            String code = text(c, "code");
            if (code == null || errorCatalog.containsKey(code)) continue;
            ErrorCategory category;
            try {
                category = ErrorCategory.valueOf(Objects.requireNonNullElse(text(c, "category"), "VALIDATION"));
            } catch (IllegalArgumentException e) {
                category = ErrorCategory.VALIDATION;
            }
            Integer status = intOrNull(c, "status");
            String message = text(c, "message");
            errorCatalog.put(code, new ErrorCodeData(code, message == null ? code : message,
                    status == null || status == category.defaultStatus() ? null : status, category, text(c, "description")));
        }
    }

    private static String derivePackage(String name) {
        String word = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "").replaceAll("^[0-9]+", "");
        if (word.length() > 30) word = word.substring(0, 30);
        if (word.isEmpty() || IdentifierRules.isKeyword(word)) word = "api";
        return "com.example." + word;
    }

    private boolean hasCycle() {
        Map<String, Set<String>> edges = new HashMap<>();
        payloads.values().forEach(b -> edges.put(b.name, new HashSet<>(b.fields.stream().map(FieldData::refPayload).filter(Objects::nonNull).toList())));
        Set<String> done = new HashSet<>();
        for (String start : edges.keySet()) {
            if (cycleFrom(start, edges, new HashSet<>(), done)) return true;
        }
        return false;
    }

    private static boolean cycleFrom(String node, Map<String, Set<String>> edges, Set<String> path, Set<String> done) {
        if (path.contains(node)) return true;
        if (!done.add(node)) return false;
        path.add(node);
        for (String next : edges.getOrDefault(node, Set.of())) {
            if (cycleFrom(next, edges, path, done)) return true;
        }
        path.remove(node);
        return false;
    }

    // ================================================================== schemas

    /** Every object schema under components.schemas becomes a payload, used or not. */
    private void components(Doc d) {
        JsonNode schemas = d.root.path("components").path("schemas");
        for (Map.Entry<String, JsonNode> e : schemas.properties()) {
            if (objectLike(d, e.getValue())) {
                componentPayload(d, e.getKey());
            }
        }
    }

    /** The payload of components.schemas[key], created on first use. */
    private String componentPayload(Doc d, String key) {
        String known = d.componentNames.get(key);
        if (known != null) {
            return known;
        }
        JsonNode schema = d.root.path("components").path("schemas").path(key);
        String wanted = typeName(key);
        PayloadBuilder existing = findPayload(wanted);
        if (existing != null && existing.owner != d.index) {
            // The same name in an earlier file: reuse it if this one has the same fields.
            d.componentNames.put(key, existing.name);
            List<FieldData> fields = fields(d, schema, existing.name);
            if (sameShape(existing.fields, fields)) {
                return existing.name;
            }
            String renamed = freePayloadName(wanted);
            warn(d.label + ": schema '" + key + "' differs from the one with the same name in another file; imported as " + renamed);
            PayloadBuilder b = new PayloadBuilder(renamed, d.index);
            payloads.put(renamed, b);
            d.componentNames.put(key, renamed);
            describe(b, schema);
            b.fields = fields;
            return renamed;
        }
        String name = freePayloadName(wanted);
        if (!name.equals(Names.pascal(key)) && !key.equals(name)) {
            warn(d.label + ": schema '" + key + "' is imported as payload " + name);
        }
        PayloadBuilder b = new PayloadBuilder(name, d.index);
        payloads.put(name, b);
        d.componentNames.put(key, name);
        describe(b, schema);
        b.fields = fields(d, schema, name);
        return name;
    }

    /** A payload for an inline object schema, named after where it appears. */
    private String inlinePayload(Doc d, JsonNode schema, String suggested) {
        String name = freePayloadName(typeName(suggested));
        PayloadBuilder b = new PayloadBuilder(name, d.index);
        payloads.put(name, b);
        describe(b, schema);
        b.fields = fields(d, schema, name);
        return name;
    }

    private void describe(PayloadBuilder b, JsonNode schema) {
        b.description = text(schema, "description");
        JsonNode example = schema.has("example") ? schema.get("example") : schema.path("examples").path(0);
        if (!example.isMissingNode() && !example.isNull()) {
            b.example = JSON.writeValueAsString(example);
        }
    }

    /** The fields of an object schema, including those of allOf members. */
    private List<FieldData> fields(Doc d, JsonNode schemaRaw, String owner) {
        Map<String, JsonNode> properties = new LinkedHashMap<>();
        Set<String> required = new HashSet<>();
        collect(d, schemaRaw, properties, required, 0);
        List<FieldData> out = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (Map.Entry<String, JsonNode> p : properties.entrySet()) {
            String name = fieldName(p.getKey(), owner, used);
            out.add(field(d, name, p.getValue(), owner, required.contains(p.getKey())));
        }
        return out;
    }

    private void collect(Doc d, JsonNode schemaRaw, Map<String, JsonNode> properties, Set<String> required, int depth) {
        JsonNode schema = resolve(d, schemaRaw);
        if (schema == null || depth > 10) return;
        for (JsonNode member : schema.path("allOf")) {
            collect(d, member, properties, required, depth + 1);
        }
        if (schema.has("oneOf") || schema.has("anyOf")) {
            warn(d.label + ": oneOf/anyOf in an object schema is not supported; only its own properties are imported");
        }
        for (Map.Entry<String, JsonNode> p : schema.path("properties").properties()) {
            properties.put(p.getKey(), p.getValue());
        }
        schema.path("required").forEach(r -> required.add(r.asString()));
    }

    /** A field, with the error codes SpecForge lists on its schema as x-error-codes. */
    private FieldData field(Doc d, String name, JsonNode raw, String owner, boolean required) {
        FieldData f = fieldShape(d, name, raw, owner, required);
        List<String> codes = new ArrayList<>();
        raw.path("x-error-codes").forEach(c -> { if (c.isString() && !c.asString().isBlank()) codes.add(c.asString().strip()); });
        if (codes.isEmpty()) {
            return f;
        }
        codes.forEach(referencedCodes::add);
        return new FieldData(f.name(), f.type(), f.itemType(), f.refPayload(), f.required(), f.format(), f.description(), f.example(),
                f.minLength(), f.maxLength(), f.minimum(), f.maximum(), f.pattern(), f.enumValues(), List.copyOf(new LinkedHashSet<>(codes)));
    }

    private FieldData fieldShape(Doc d, String name, JsonNode raw, String owner, boolean required) {
        String desc = text(raw, "description");
        JsonNode schema = raw;
        // allOf with a single $ref is a common way to add a description to a reference.
        if (schema.has("allOf") && schema.path("allOf").size() == 1 && schema.path("allOf").path(0).has("$ref")) {
            schema = schema.path("allOf").path(0);
        }
        if (schema.has("oneOf") || schema.has("anyOf")) {
            JsonNode options = schema.has("oneOf") ? schema.get("oneOf") : schema.get("anyOf");
            warn(d.label + ": " + owner + "." + name + " is oneOf/anyOf; imported as its first option");
            schema = options.path(0);
        }
        if (schema.has("$ref")) {
            String key = componentKey(schema.get("$ref").asString());
            JsonNode target = resolve(d, schema);
            if (key != null && target != null && objectLike(d, target)) {
                return new FieldData(name, FieldType.OBJECT, null, componentPayload(d, key), required, null, desc, null,
                        null, null, null, null, null, List.of());
            }
            if (target == null) {
                warn(d.label + ": " + owner + "." + name + " refers to " + schema.get("$ref").asString() + ", which could not be found; imported as text");
                return scalar(name, FieldType.STRING, required, desc, null, null);
            }
            if (desc == null) desc = text(target, "description");
            schema = target;
        }
        String type = typeOf(schema);
        if ("array".equals(type)) {
            FieldData item = field(d, name, schema.path("items").isMissingNode() ? JSON.createObjectNode() : schema.get("items"), owner + Names.pascal(name) + "Item", false);
            Integer minItems = intOrNull(schema, "minItems");
            Integer maxItems = intOrNull(schema, "maxItems");
            if (item.type() == FieldType.ARRAY) {
                warn(d.label + ": " + owner + "." + name + " is a list of lists; imported as a list of text");
                return new FieldData(name, FieldType.ARRAY, FieldType.STRING, null, required, null, desc, null, minItems, maxItems,
                        null, null, null, List.of());
            }
            return new FieldData(name, FieldType.ARRAY, item.type(), item.refPayload(), required, item.format(), desc, null,
                    minItems, maxItems, item.minimum(), item.maximum(), item.pattern(), item.enumValues());
        }
        if ("object".equals(type)) {
            if (schema.has("properties") || schema.has("allOf")) {
                return new FieldData(name, FieldType.OBJECT, null, inlinePayload(d, schema, owner + Names.pascal(name)), required,
                        null, desc, null, null, null, null, null, null, List.of());
            }
            warn(d.label + ": " + owner + "." + name + " is a free-form object or map; imported as text (JSON)");
            return scalar(name, FieldType.STRING, required, desc, null, null);
        }
        FieldType fieldType = scalarType(type, text(schema, "format"));
        if (type == null) {
            warn(d.label + ": " + owner + "." + name + " has no type; imported as text");
        }
        String format = fieldType == FieldType.STRING ? text(schema, "format") : null;
        if ("binary".equals(format) || "byte".equals(format)) {
            warn(d.label + ": " + owner + "." + name + " is binary data; imported as text");
        }
        List<String> enumValues = new ArrayList<>();
        if (schema.has("enum")) {
            if (fieldType == FieldType.STRING) {
                schema.get("enum").forEach(v -> { if (v.isString() && !v.asString().isBlank()) enumValues.add(v.asString()); });
            } else {
                warn(d.label + ": " + owner + "." + name + " lists allowed values for a " + fieldType + "; they are not imported");
            }
        }
        boolean isString = fieldType == FieldType.STRING;
        boolean isNumber = fieldType.isNumeric();
        JsonNode example = schema.has("example") ? schema.get("example") : schema.path("examples").path(0);
        String exampleText = example.isMissingNode() || example.isNull() ? null : example.isString() ? example.asString() : JSON.writeValueAsString(example);
        if (exampleText != null && exampleText.length() > 1000) exampleText = null;
        return new FieldData(name, fieldType, null, null, required, format, desc, exampleText,
                isString ? intOrNull(schema, "minLength") : null, isString ? intOrNull(schema, "maxLength") : null,
                isNumber ? doubleOrNull(schema, "minimum") : null, isNumber ? doubleOrNull(schema, "maximum") : null,
                isString ? text(schema, "pattern") : null, List.copyOf(new LinkedHashSet<>(enumValues)));
    }

    private static FieldData scalar(String name, FieldType type, boolean required, String desc, Integer min, Integer max) {
        return new FieldData(name, type, null, null, required, null, desc, null, min, max, null, null, null, List.of());
    }

    private static FieldType scalarType(String type, String format) {
        if (type == null) return FieldType.STRING;
        return switch (type) {
            case "integer" -> "int64".equals(format) ? FieldType.LONG : FieldType.INTEGER;
            case "number" -> "double".equals(format) || "float".equals(format) ? FieldType.NUMBER : FieldType.DECIMAL;
            case "boolean" -> FieldType.BOOLEAN;
            case "string" -> switch (format == null ? "" : format) {
                case "date" -> FieldType.DATE;
                case "date-time" -> FieldType.DATE_TIME;
                case "uuid" -> FieldType.UUID;
                default -> FieldType.STRING;
            };
            default -> FieldType.STRING;
        };
    }

    /** The type of a schema; "null" in type lists is ignored. */
    private static String typeOf(JsonNode schema) {
        JsonNode type = schema.get("type");
        if (type != null && type.isString()) return type.asString();
        if (type != null && type.isArray()) {
            for (JsonNode t : type) if (!"null".equals(t.asString())) return t.asString();
        }
        if (schema.has("properties") || schema.has("allOf")) return "object";
        if (schema.has("items")) return "array";
        if (schema.has("enum")) return "string";
        return null;
    }

    private boolean objectLike(Doc d, JsonNode raw) {
        JsonNode schema = resolve(d, raw);
        return schema != null && ("object".equals(typeOf(schema)) && (schema.has("properties") || schema.has("allOf")));
    }

    /**
     * The payload for a request, response or message body. A $ref to an object schema is that
     * payload; an inline object gets its own; anything else is wrapped in a payload.
     */
    private String bodyPayload(Doc d, JsonNode schema, String suggested) {
        if (schema == null || schema.isMissingNode()) return null;
        if (schema.has("$ref")) {
            String key = componentKey(schema.get("$ref").asString());
            if (key != null && objectLike(d, schema)) {
                return componentPayload(d, key);
            }
        }
        JsonNode resolved = resolve(d, schema);
        if (resolved == null) {
            warn(d.label + ": the body of " + suggested + " refers to a schema that could not be found; it is left out");
            return null;
        }
        String type = typeOf(resolved);
        if ("object".equals(type) && (resolved.has("properties") || resolved.has("allOf"))) {
            return inlinePayload(d, resolved, suggested);
        }
        String wrapper = freePayloadName(typeName(suggested));
        PayloadBuilder b = new PayloadBuilder(wrapper, d.index);
        payloads.put(wrapper, b);
        String fieldName = "array".equals(type) ? "items" : "value";
        b.fields = List.of(field(d, fieldName, schema, wrapper, true));
        warn(d.label + ": the body of " + suggested + " is " + (type == null ? "not an object" : ("array".equals(type) || "integer".equals(type) ? "an " : "a ") + type)
                + "; it is wrapped in payload " + wrapper + " (field " + fieldName + ")");
        return wrapper;
    }

    // ================================================================== OpenAPI

    private void openApi(Doc d) {
        JsonNode paths = d.root.path("paths");
        for (Map.Entry<String, JsonNode> p : paths.properties()) {
            JsonNode item = resolve(d, p.getValue());
            if (item == null) continue;
            for (String method : HTTP_METHODS) {
                if (item.has(method)) {
                    operation(d, p.getKey(), method, item, item.get(method));
                }
            }
        }
    }

    private void operation(Doc d, String rawPath, String method, JsonNode pathItem, JsonNode op) {
        String where = d.label + ": " + method.toUpperCase(Locale.ROOT) + " " + rawPath;
        // Parameters: path-level ones, overridden by operation-level ones with the same name and place.
        Map<String, JsonNode> params = new LinkedHashMap<>();
        for (JsonNode list : List.of(pathItem.path("parameters"), op.path("parameters"))) {
            for (JsonNode raw : list) {
                JsonNode param = resolve(d, raw);
                if (param != null) params.put(text(param, "in") + ":" + text(param, "name"), param);
            }
        }
        // Path: rename {params} that aren't Java names.
        Map<String, String> renamed = new HashMap<>();
        StringBuilder path = new StringBuilder();
        for (String segment : rawPath.split("/")) {
            if (segment.isEmpty()) continue;
            var m = PARAM_SEGMENT.matcher(segment);
            if (m.matches()) {
                String javaName = IdentifierRules.checkIdentifier(m.group(1)) == null ? m.group(1) : Names.camel(m.group(1));
                renamed.put(m.group(1), javaName);
                path.append("/{").append(javaName).append("}");
            } else if (LITERAL_SEGMENT.matcher(segment).matches()) {
                path.append('/').append(segment);
            } else {
                warn(where + " is skipped: path segment '" + segment + "' is not supported");
                return;
            }
        }
        String finalPath = path.isEmpty() ? "/" : path.toString();
        String name = endpointName(text(op, "operationId"), method + " " + rawPath);

        List<ParameterDto> parameters = new ArrayList<>();
        for (JsonNode param : params.values()) {
            String in = text(param, "in");
            String paramName = text(param, "name");
            if (paramName == null || in == null) continue;
            ParameterLocation location = switch (in) {
                case "path" -> ParameterLocation.PATH;
                case "query" -> ParameterLocation.QUERY;
                case "header" -> ParameterLocation.HEADER;
                default -> null;
            };
            if (location == null) {
                warn(where + ": " + in + " parameter '" + paramName + "' is not supported and is skipped");
                continue;
            }
            if (location == ParameterLocation.PATH) {
                paramName = renamed.getOrDefault(paramName, paramName);
            } else if (location == ParameterLocation.HEADER && RESERVED_HEADERS.contains(paramName.toLowerCase(Locale.ROOT))) {
                continue;
            } else if ((location == ParameterLocation.QUERY && !QUERY_NAME.matcher(paramName).matches())
                    || (location == ParameterLocation.HEADER && !HEADER_NAME.matcher(paramName).matches())) {
                warn(where + ": parameter '" + paramName + "' has an unsupported name and is skipped");
                continue;
            }
            parameters.add(new ParameterDto(paramName, location, parameterType(d, param.path("schema"), where + " parameter " + paramName),
                    location == ParameterLocation.PATH || param.path("required").asBoolean(false), text(param, "description")));
        }
        // Path parameters the document forgot to declare.
        for (String javaName : renamed.values()) {
            if (parameters.stream().noneMatch(p -> p.location() == ParameterLocation.PATH && p.name().equals(javaName))) {
                parameters.add(new ParameterDto(javaName, ParameterLocation.PATH, FieldType.STRING, true, null));
            }
        }

        HttpMethod httpMethod = HttpMethod.valueOf(method.toUpperCase(Locale.ROOT));
        String requestPayload = null;
        JsonNode body = resolve(d, op.path("requestBody"));
        if (body != null && !body.isMissingNode() && body.has("content")) {
            if (httpMethod == HttpMethod.GET || httpMethod == HttpMethod.HEAD) {
                warn(where + ": a request body on " + httpMethod + " is not supported and is skipped");
            } else {
                requestPayload = bodyPayload(d, jsonSchema(body.get("content"), where), Names.pascal(name) + "Request");
            }
        }

        List<ResponseData> responses = new ArrayList<>();
        for (Map.Entry<String, JsonNode> r : op.path("responses").properties()) {
            if (!STATUS.matcher(r.getKey()).matches()) {
                warn(where + ": response '" + r.getKey() + "' is skipped (only exact status codes and 'default' are supported)");
                continue;
            }
            JsonNode response = resolve(d, r.getValue());
            if (response == null) continue;
            String payload = response.has("content")
                    ? bodyPayload(d, jsonSchema(response.get("content"), where), Names.pascal(name) + "Response"
                            + (r.getKey().equals("default") ? "Default" : r.getKey()))
                    : null;
            responses.add(new ResponseData(r.getKey(), text(response, "description"), payload));
        }
        if (responses.isEmpty()) {
            warn(where + " has no responses; a 200 response was added");
            responses.add(new ResponseData("200", "OK", null));
        }

        List<String> tags = new ArrayList<>();
        op.path("tags").forEach(t -> tags.add(t.asString()));
        endpoints.add(new EndpointData(EndpointKind.REST, name, text(op, "summary"), text(op, "description"), tags, null,
                parameters, httpMethod, finalPath, requestPayload, responses, security(d, op), null, null, null, null, List.of()));
    }

    /** The JSON schema of a content map: application/json, any other JSON type, or else the first. */
    private JsonNode jsonSchema(JsonNode content, String where) {
        JsonNode chosen = null;
        for (Map.Entry<String, JsonNode> c : content.properties()) {
            if (c.getKey().equals("application/json")) { chosen = c.getValue(); break; }
            if (chosen == null && c.getKey().contains("json")) chosen = c.getValue();
        }
        if (chosen == null && !content.isEmpty()) {
            Map.Entry<String, JsonNode> first = content.properties().iterator().next();
            warn(where + ": content type " + first.getKey() + " is imported as if it were JSON");
            chosen = first.getValue();
        }
        return chosen == null ? null : chosen.get("schema");
    }

    private List<SecurityScheme> security(Doc d, JsonNode op) {
        JsonNode requirements = op.has("security") ? op.get("security") : d.root.path("security");
        JsonNode schemes = d.root.path("components").path("securitySchemes");
        Set<SecurityScheme> out = new LinkedHashSet<>();
        for (JsonNode requirement : requirements) {
            for (Map.Entry<String, JsonNode> r : requirement.properties()) {
                JsonNode scheme = resolve(d, schemes.path(r.getKey()));
                if (scheme == null) continue;
                String type = text(scheme, "type");
                String httpScheme = text(scheme, "scheme");
                if ("http".equals(type) && "bearer".equalsIgnoreCase(httpScheme)) out.add(SecurityScheme.BEARER_JWT);
                else if ("http".equals(type) && "basic".equalsIgnoreCase(httpScheme)) out.add(SecurityScheme.BASIC);
                else if ("apiKey".equals(type)) out.add(SecurityScheme.API_KEY);
                else if ("oauth2".equals(type) || "openIdConnect".equals(type)) out.add(SecurityScheme.OAUTH2);
            }
        }
        return List.copyOf(out);
    }

    private FieldType parameterType(Doc d, JsonNode raw, String where) {
        JsonNode schema = resolve(d, raw);
        if (schema == null || schema.isMissingNode()) return FieldType.STRING;
        String type = typeOf(schema);
        if ("array".equals(type) || "object".equals(type)) {
            warn(where + " is " + ("array".equals(type) ? "a list" : "an object") + "; imported as text");
            return FieldType.STRING;
        }
        return scalarType(type, text(schema, "format"));
    }

    // ================================================================== AsyncAPI

    private void asyncApi3(Doc d) {
        for (Map.Entry<String, JsonNode> o : d.root.path("operations").properties()) {
            JsonNode op = resolve(d, o.getValue());
            if (op == null) continue;
            String where = d.label + ": operation " + o.getKey();
            String action = text(op, "action");
            AsyncOperation operation = "send".equals(action) ? AsyncOperation.SEND : "receive".equals(action) ? AsyncOperation.RECEIVE : null;
            if (operation == null) {
                warn(where + " has no send/receive action and is skipped");
                continue;
            }
            String channelRef = text(op.path("channel"), "$ref");
            JsonNode channel = resolve(d, op.path("channel"));
            if (channel == null) {
                warn(where + " has no channel and is skipped");
                continue;
            }
            String channelKey = channelRef == null ? o.getKey() : channelRef.substring(channelRef.lastIndexOf('/') + 1);
            String address = text(channel, "address") != null ? text(channel, "address") : channelKey;
            JsonNode messageRef = op.path("messages").path(0);
            if (op.path("messages").size() > 1) {
                warn(where + " carries several messages; only the first is imported");
            }
            if (messageRef.isMissingNode()) {
                messageRef = channel.path("messages").properties().iterator().hasNext()
                        ? channel.path("messages").properties().iterator().next().getValue() : null;
            }
            asyncEndpoint(d, where, o.getKey(), op, operation, address, messageRef, protocol(d, channel.path("servers"), where));
        }
    }

    private void asyncApi2(Doc d) {
        for (Map.Entry<String, JsonNode> c : d.root.path("channels").properties()) {
            JsonNode channel = resolve(d, c.getValue());
            if (channel == null) continue;
            for (String kind : List.of("publish", "subscribe")) {
                if (!channel.has(kind)) continue;
                JsonNode op = channel.get(kind);
                // In AsyncAPI 2, "publish" means others publish to this app, so the app receives.
                AsyncOperation operation = kind.equals("publish") ? AsyncOperation.RECEIVE : AsyncOperation.SEND;
                String where = d.label + ": " + kind + " on " + c.getKey();
                JsonNode message = op.path("message");
                if (message.has("oneOf")) {
                    warn(where + " carries several messages; only the first is imported");
                    message = message.path("oneOf").path(0);
                }
                String id = text(op, "operationId") != null ? text(op, "operationId")
                        : (operation == AsyncOperation.RECEIVE ? "on " : "send ") + c.getKey();
                JsonNode servers = channel.path("servers");
                asyncEndpoint(d, where, id, op, operation, c.getKey(), message, protocol(d, servers, where));
            }
        }
    }

    private void asyncEndpoint(Doc d, String where, String id, JsonNode op, AsyncOperation operation, String address,
                               JsonNode messageRef, MessagingProtocol protocol) {
        JsonNode message = messageRef == null ? null : resolve(d, messageRef);
        if (message == null || message.isMissingNode()) {
            warn(where + " has no message and is skipped");
            return;
        }
        String messageName = text(message, "name") != null ? text(message, "name")
                : messageRef.has("$ref") ? messageRef.get("$ref").asString().replaceAll(".*/", "") : id + "Message";
        String payload = bodyPayload(d, message.path("payload"), Names.pascal(messageName));
        if (payload == null) {
            warn(where + ": the message has no payload; the operation is skipped");
            return;
        }
        List<ParameterDto> headers = new ArrayList<>();
        JsonNode headerSchema = resolve(d, message.path("headers"));
        if (headerSchema != null) {
            Set<String> required = new HashSet<>();
            headerSchema.path("required").forEach(r -> required.add(r.asString()));
            for (Map.Entry<String, JsonNode> h : headerSchema.path("properties").properties()) {
                String headerName = h.getKey();
                if (!HEADER_NAME.matcher(headerName).matches()) {
                    String fixed = headerName.replaceAll("[^A-Za-z0-9-]", "-").replaceAll("^-+", "");
                    if (fixed.isEmpty()) continue;
                    warn(where + ": header '" + headerName + "' is imported as '" + fixed + "'");
                    headerName = fixed;
                }
                headers.add(new ParameterDto(headerName, ParameterLocation.HEADER, parameterType(d, h.getValue(), where + " header " + headerName),
                        required.contains(h.getKey()), text(resolve(d, h.getValue()), "description")));
            }
        }
        String channel = address.replaceAll("[^A-Za-z0-9._/-]", ".").replaceAll("\\.{2,}", ".");
        if (channel.length() > 249) channel = channel.substring(0, 249);
        if (!channel.equals(address)) {
            warn(where + ": channel '" + address + "' is imported as '" + channel + "'");
        }
        List<String> tags = new ArrayList<>();
        op.path("tags").forEach(t -> tags.add(text(t, "name") != null ? text(t, "name") : t.asString()));
        endpoints.add(new EndpointData(EndpointKind.ASYNC, endpointName(id, id), text(op, "summary"), text(op, "description"), tags, null,
                headers, null, null, null, List.of(), List.of(), channel, operation, protocol, payload, List.of()));
    }

    /** The protocol of the servers a channel uses, or of the document's servers. */
    private MessagingProtocol protocol(Doc d, JsonNode channelServers, String where) {
        List<JsonNode> servers = new ArrayList<>();
        for (JsonNode s : channelServers) {
            JsonNode server = s.isString() ? d.root.path("servers").path(s.asString()) : resolve(d, s);
            if (server != null) servers.add(server);
        }
        if (servers.isEmpty()) {
            for (Map.Entry<String, JsonNode> s : d.root.path("servers").properties()) servers.add(s.getValue());
        }
        for (JsonNode server : servers) {
            String p = Objects.requireNonNullElse(text(server, "protocol"), "").toLowerCase(Locale.ROOT);
            if (p.startsWith("kafka")) return MessagingProtocol.KAFKA;
            if (p.startsWith("amqp")) return MessagingProtocol.AMQP;
            if (p.equals("jms") || p.contains("mq") || p.equals("stomp")) return MessagingProtocol.JMS;
        }
        warn(where + ": no Kafka, JMS or AMQP server found; Kafka is assumed");
        return MessagingProtocol.KAFKA;
    }

    // ================================================================== helpers

    /** Follows local $refs ("#/..."); null for refs that can't be resolved. */
    private JsonNode resolve(Doc d, JsonNode node) {
        JsonNode current = node;
        for (int i = 0; current != null && current.has("$ref") && i < 20; i++) {
            String ref = current.get("$ref").asString();
            if (!ref.startsWith("#/")) {
                warn(d.label + ": external reference " + ref + " is not supported");
                return null;
            }
            JsonNode target = d.root.at(ref.substring(1));
            current = target.isMissingNode() ? null : target;
        }
        return current;
    }

    private static String componentKey(String ref) {
        String prefix = "#/components/schemas/";
        return ref.startsWith(prefix) ? ref.substring(prefix.length()).replace("~1", "/").replace("~0", "~") : null;
    }

    private String typeName(String raw) {
        String name = Names.pascal(raw);
        if (IdentifierRules.checkTypeName(name) != null) {
            name = name + "Payload";
        }
        return name;
    }

    private PayloadBuilder findPayload(String name) {
        return payloads.values().stream().filter(b -> b.name.equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    private String freePayloadName(String wanted) {
        String name = wanted;
        for (int n = 2; findPayload(name) != null; n++) {
            name = wanted + n;
        }
        return name;
    }

    private String fieldName(String raw, String owner, Set<String> used) {
        String name = IdentifierRules.checkIdentifier(raw) == null ? raw : Names.camel(raw);
        if (!name.equals(raw)) {
            warn(owner + "." + raw + " is imported as " + name);
        }
        String unique = name;
        for (int n = 2; !used.add(unique.toLowerCase(Locale.ROOT)); n++) {
            unique = name + n;
        }
        return unique;
    }

    private String endpointName(String operationId, String fallback) {
        String name = operationId != null && IdentifierRules.checkMemberName(operationId) == null ? operationId
                : Names.camel(operationId != null ? operationId : fallback.replaceAll("[{}]", " "));
        String unique = name;
        for (int n = 2; !endpointNames.add(unique.toLowerCase(Locale.ROOT)); n++) {
            unique = name + n;
        }
        return unique;
    }

    private static boolean sameShape(List<FieldData> a, List<FieldData> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            FieldData x = a.get(i), y = b.get(i);
            if (!x.name().equals(y.name()) || x.type() != y.type() || x.itemType() != y.itemType()
                    || !Objects.equals(x.refPayload(), y.refPayload())) return false;
        }
        return true;
    }

    private void warn(String message) {
        if (!warnings.contains(message)) warnings.add(message);
    }

    private static String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.get(field);
        return v == null || v.isNull() || !v.isValueNode() ? null : v.asString();
    }

    private static Integer intOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && v.isNumber() ? v.asInt() : null;
    }

    private static Double doubleOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && v.isNumber() ? v.asDouble() : null;
    }
}
