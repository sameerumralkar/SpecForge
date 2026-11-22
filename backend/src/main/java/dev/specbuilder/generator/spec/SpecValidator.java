package dev.specbuilder.generator.spec;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import dev.specbuilder.generator.Problem;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.*;

/**
 * Checks generated specs three ways: against the official JSON Schema of the spec, with
 * swagger-parser (OpenAPI only, which also catches semantic problems such as duplicate operation
 * ids), and by resolving every internal $ref.
 */
@Component
public class SpecValidator {

    private static final int MAX_PROBLEMS = 50;

    private final Schema openApiSchema;
    private final Schema asyncApiSchema;

    public SpecValidator() {
        openApiSchema = load(SpecificationVersion.DRAFT_2020_12, "/schemas/openapi-3.1.json");
        asyncApiSchema = load(SpecificationVersion.DRAFT_7, "/schemas/asyncapi-3.0.0.json");
    }

    public List<Problem> validateOpenApi(String json, String yaml) {
        JsonNode doc = SpecWriter.readJson(json);
        List<Problem> problems = new ArrayList<>(schemaProblems(openApiSchema, doc, "openapi-schema"));
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setValidateInternalRefs(true);
        SwaggerParseResult result = new OpenAPIV3Parser().readContents(yaml, null, options);
        if (result.getMessages() != null) {
            result.getMessages().stream().sorted().distinct()
                    .forEach(m -> problems.add(Problem.error("swagger-parser", "", m)));
        }
        problems.addAll(refProblems(doc));
        return cap(problems);
    }

    public List<Problem> validateAsyncApi(String json) {
        JsonNode doc = SpecWriter.readJson(json);
        List<Problem> problems = new ArrayList<>(schemaProblems(asyncApiSchema, doc, "asyncapi-schema"));
        problems.addAll(refProblems(doc));
        return cap(problems);
    }

    private static List<Problem> schemaProblems(Schema schema, JsonNode doc, String source) {
        Set<String> seen = new LinkedHashSet<>();
        List<Problem> problems = new ArrayList<>();
        for (Error e : schema.validate(doc)) {
            String location = String.valueOf(e.getInstanceLocation());
            if (seen.add(location + " " + e.getMessage())) {
                problems.add(Problem.error(source, location, e.getMessage()));
            }
        }
        return problems;
    }

    /** Every "$ref": "#/..." must point at something in the document. */
    static List<Problem> refProblems(JsonNode doc) {
        List<Problem> problems = new ArrayList<>();
        walk(doc, doc, "", problems);
        return problems;
    }

    private static void walk(JsonNode root, JsonNode node, String pointer, List<Problem> problems) {
        if (node.isObject()) {
            JsonNode ref = node.get("$ref");
            if (ref != null && ref.isString() && ref.asString().startsWith("#/") && root.at(ref.asString().substring(1)).isMissingNode()) {
                problems.add(Problem.error("refs", pointer, "$ref " + ref.asString() + " points at nothing"));
            }
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                walk(root, entry.getValue(), pointer + "/" + Tree.escape(entry.getKey()), problems);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                walk(root, node.get(i), pointer + "/" + i, problems);
            }
        }
    }

    private static List<Problem> cap(List<Problem> problems) {
        if (problems.size() <= MAX_PROBLEMS) {
            return problems;
        }
        List<Problem> capped = new ArrayList<>(problems.subList(0, MAX_PROBLEMS));
        capped.add(Problem.info("validator", "…and " + (problems.size() - MAX_PROBLEMS) + " more problems"));
        return capped;
    }

    private static Schema load(SpecificationVersion version, String resource) {
        try (InputStream in = SpecValidator.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + resource);
            }
            return SchemaRegistry.withDefaultDialect(version).getSchema(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
