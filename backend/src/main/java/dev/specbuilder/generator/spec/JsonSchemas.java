package dev.specbuilder.generator.spec;

import dev.specbuilder.domain.FieldType;
import dev.specbuilder.generator.Problem;
import dev.specbuilder.generator.ProjectSnapshot.FieldSnapshot;
import dev.specbuilder.generator.ProjectSnapshot.PayloadSnapshot;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.specbuilder.generator.spec.Tree.obj;
import static dev.specbuilder.generator.spec.Tree.put;

/**
 * Payloads as JSON Schema. The output is valid both in OpenAPI 3.1 (JSON Schema 2020-12) and in
 * AsyncAPI 3.0 (a superset of draft-07): only keywords the two share are used.
 */
final class JsonSchemas {

    private final String refPrefix;
    private final List<Problem> problems;
    private final String source;

    /**
     * @param refPrefix where payload schemas live, e.g. "#/components/schemas/"
     * @param problems  receives warnings, e.g. a field example that doesn't match its type
     */
    JsonSchemas(String refPrefix, List<Problem> problems, String source) {
        this.refPrefix = refPrefix;
        this.problems = problems;
        this.source = source;
    }

    Map<String, Object> payload(PayloadSnapshot p, String pointer) {
        Map<String, Object> schema = obj();
        schema.put("type", "object");
        put(schema, "description", p.description());
        Map<String, Object> properties = obj();
        List<String> required = new ArrayList<>();
        for (FieldSnapshot f : p.fields()) {
            properties.put(f.name(), field(f, pointer + "/properties/" + Tree.escape(f.name())));
            if (f.required()) {
                required.add(f.name());
            }
        }
        put(schema, "properties", properties);
        put(schema, "required", required);
        if (p.example() != null && !p.example().isBlank()) {
            schema.put("examples", List.of(SpecWriter.readJson(p.example())));
        }
        return schema;
    }

    Map<String, Object> field(FieldSnapshot f, String pointer) {
        Map<String, Object> schema;
        if (f.type() == FieldType.OBJECT) {
            schema = Tree.ref(refPrefix + f.refPayload());
        } else if (f.type() == FieldType.ARRAY) {
            schema = obj();
            schema.put("type", "array");
            schema.put("items", f.itemType() == FieldType.OBJECT ? Tree.ref(refPrefix + f.refPayload()) : valueSchema(f, f.itemType()));
            put(schema, "minItems", f.minLength());
            put(schema, "maxItems", f.maxLength());
        } else {
            schema = valueSchema(f, f.type());
            put(schema, "minLength", f.minLength());
            put(schema, "maxLength", f.maxLength());
        }
        put(schema, "description", f.description());
        if (f.example() != null && !f.example().isBlank()) {
            schema.put("examples", List.of(example(f, pointer)));
        }
        return schema;
    }

    /** A scalar type with the value constraints (pattern, allowed values, min/max). */
    private Map<String, Object> valueSchema(FieldSnapshot f, FieldType type) {
        Map<String, Object> schema = scalar(type);
        if (type == FieldType.STRING && f.format() != null && !f.format().isBlank()) {
            schema.put("format", f.format());
        }
        put(schema, "pattern", f.pattern());
        put(schema, "enum", f.enumValues());
        put(schema, "minimum", Tree.number(f.minimum()));
        put(schema, "maximum", Tree.number(f.maximum()));
        return schema;
    }

    /** The JSON Schema for a simple type, as used for fields, parameters and headers. */
    static Map<String, Object> scalar(FieldType type) {
        Map<String, Object> schema = obj();
        switch (type) {
            case STRING -> schema.put("type", "string");
            case INTEGER -> { schema.put("type", "integer"); schema.put("format", "int32"); }
            case LONG -> { schema.put("type", "integer"); schema.put("format", "int64"); }
            case NUMBER -> { schema.put("type", "number"); schema.put("format", "double"); }
            case DECIMAL -> schema.put("type", "number");
            case BOOLEAN -> schema.put("type", "boolean");
            case DATE -> { schema.put("type", "string"); schema.put("format", "date"); }
            case DATE_TIME -> { schema.put("type", "string"); schema.put("format", "date-time"); }
            case UUID -> { schema.put("type", "string"); schema.put("format", "uuid"); }
            case OBJECT -> schema.put("type", "object");
            case ARRAY -> schema.put("type", "array");
        }
        return schema;
    }

    /** The field's example as the right JSON type; text is kept (with a warning) if it doesn't parse. */
    private Object example(FieldSnapshot f, String pointer) {
        String raw = f.example().strip();
        FieldType type = f.valueType();
        try {
            return switch (type) {
                case INTEGER, LONG -> Long.parseLong(raw);
                case NUMBER, DECIMAL -> new BigDecimal(raw);
                case BOOLEAN -> {
                    if (!raw.equals("true") && !raw.equals("false")) {
                        throw new NumberFormatException();
                    }
                    yield Boolean.parseBoolean(raw);
                }
                case OBJECT, ARRAY -> SpecWriter.readJson(raw);
                default -> raw;
            };
        } catch (RuntimeException e) {
            problems.add(Problem.warning(source, pointer + "/examples",
                    "the example of field '" + f.name() + "' is not a valid " + type + "; it is written as text"));
            return raw;
        }
    }
}
