package dev.specbuilder.generator;

import dev.specbuilder.generator.spec.SpecValidator;
import dev.specbuilder.generator.spec.SpecWriter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The validators must actually catch broken documents, from each of their sources. */
class SpecValidatorTest {

    private final SpecValidator validator = new SpecValidator();

    private static final String OPENAPI = """
            {"openapi": "3.1.0", "info": {"title": "T", "version": "1"},
             "paths": {
               "/a": {"get": {"operationId": "same", "responses": {"200": {"description": "ok",
                   "content": {"application/json": {"schema": {"$ref": "#/components/schemas/Missing"}}}}}}},
               "/b": {"get": {"operationId": "same", "responses": {"200": {"description": "ok"}}}}
             }}
            """;

    private List<Problem> openApi(String json) {
        return validator.validateOpenApi(json, SpecWriter.yaml(SpecWriter.readJson(json)));
    }

    @Test
    void catchesOpenApiSchemaViolations() {
        String broken = OPENAPI.replace("\"version\": \"1\"", "\"versión\": \"1\"");
        assertThat(openApi(broken)).anySatisfy(p -> {
            assertThat(p.source()).isEqualTo("openapi-schema");
            assertThat(p.severity()).isEqualTo(Problem.Severity.ERROR);
        });
    }

    @Test
    void catchesDuplicateOperationIdsAndDanglingRefs() {
        List<Problem> problems = openApi(OPENAPI);
        assertThat(problems).anySatisfy(p -> {
            assertThat(p.source()).isEqualTo("swagger-parser");
            assertThat(p.message()).contains("operationId").contains("repeated");
        });
        assertThat(problems).anySatisfy(p -> {
            assertThat(p.source()).isEqualTo("refs");
            assertThat(p.location()).isEqualTo("/paths/~1a/get/responses/200/content/application~1json/schema");
        });
    }

    @Test
    void catchesAsyncApiSchemaViolations() {
        String doc = """
                {"asyncapi": "3.0.0", "info": {"title": "T", "version": "1"},
                 "channels": {"c": {"address": "c"}},
                 "operations": {"o": {"action": "publish", "channel": {"$ref": "#/channels/nope"}}}}
                """;
        List<Problem> problems = validator.validateAsyncApi(doc);
        assertThat(problems).anySatisfy(p -> assertThat(p.source()).isEqualTo("asyncapi-schema"));
        assertThat(problems).anySatisfy(p -> {
            assertThat(p.source()).isEqualTo("refs");
            assertThat(p.message()).contains("#/channels/nope");
        });
    }
}
