package dev.specbuilder.generator;

import dev.specbuilder.domain.*;
import dev.specbuilder.generator.ProjectSnapshot.*;
import dev.specbuilder.generator.spec.OpenApiGenerator;
import dev.specbuilder.generator.spec.SpecValidator;
import dev.specbuilder.generator.spec.SpecWriter;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.List;

import static dev.specbuilder.generator.Snapshots.*;
import static org.assertj.core.api.Assertions.assertThat;

class OpenApiGeneratorTest {

    private final OpenApiGenerator generator = new OpenApiGenerator();

    private final PayloadSnapshot item = new PayloadSnapshot(java.util.UUID.randomUUID(), "Item", "One line", "{\"sku\":\"A-1\",\"qty\":2}", List.of(
            new FieldSnapshot("sku", FieldType.STRING, null, null, true, null, "Stock unit", "A-1", 2, 20, null, null, "[A-Z0-9-]+", List.of()),
            new FieldSnapshot("qty", FieldType.INTEGER, null, null, true, null, null, "2", null, null, 1.0, 99.0, null, List.of()),
            new FieldSnapshot("price", FieldType.DECIMAL, null, null, false, null, null, "cheap", null, null, 0.5, null, null, List.of()),
            new FieldSnapshot("status", FieldType.STRING, null, null, false, null, null, null, null, null, null, null, null, List.of("NEW", "DONE")),
            new FieldSnapshot("email", FieldType.STRING, null, null, false, "email", null, null, null, null, null, null, null, List.of()),
            new FieldSnapshot("labels", FieldType.ARRAY, FieldType.STRING, null, false, null, null, null, 0, 5, null, null, "[a-z]+", List.of())));
    private final PayloadSnapshot cart = payload("Cart", field("id", FieldType.UUID, true), listOf("items", "Item"),
            field("createdAt", FieldType.DATE_TIME, false));
    private final PayloadSnapshot unused = payload("Unused", field("x", FieldType.STRING, false));
    private final PayloadSnapshot error = payload("Problem", field("message", FieldType.STRING, true));

    private ProjectSnapshot project() {
        return Snapshots.project(List.of(cart, item, error, unused), List.of(
                rest("getCart", HttpMethod.GET, "/carts/{cartId}", null,
                        List.of(new ResponseSnapshot("404", null, "Problem"), new ResponseSnapshot("200", "The cart", "Cart"),
                                new ResponseSnapshot("default", null, "Problem")),
                        List.of(new ParameterSnapshot("cartId", ParameterLocation.PATH, FieldType.UUID, true, "Cart id"),
                                new ParameterSnapshot("X-Trace", ParameterLocation.HEADER, FieldType.STRING, false, null)),
                        List.of(SecurityScheme.BEARER_JWT, SecurityScheme.API_KEY), "Carts"),
                rest("addItem", HttpMethod.POST, "/carts/{cartId}", "Item",
                        List.of(new ResponseSnapshot("204", null, null)),
                        List.of(new ParameterSnapshot("cartId", ParameterLocation.PATH, FieldType.UUID, true, null)),
                        List.of(), "Carts", "Items"),
                async("cartChanged", "carts.changed", AsyncOperation.SEND, MessagingProtocol.KAFKA, "Cart")));
    }

    private JsonNode doc(GenerationResult result) {
        return SpecWriter.readJson(result.file("openapi.json").content());
    }

    @Test
    void writesOperationsParametersBodiesAndResponses() {
        JsonNode doc = doc(generator.generate(project()));
        assertThat(doc.get("openapi").asString()).isEqualTo("3.1.0");
        assertThat(doc.at("/info/title").asString()).isEqualTo("Shop");
        assertThat(doc.at("/info/version").asString()).isEqualTo("2.0.0");
        assertThat(doc.get("tags").findValuesAsString("name")).containsExactly("Carts", "Items");

        JsonNode path = doc.at("/paths/~1carts~1{cartId}");
        assertThat(path.propertyNames()).containsExactly("get", "post");
        JsonNode get = path.get("get");
        assertThat(get.get("operationId").asString()).isEqualTo("getCart");
        assertThat(get.at("/parameters/0/in").asString()).isEqualTo("path");
        assertThat(get.at("/parameters/0/required").asBoolean()).isTrue();
        assertThat(get.at("/parameters/0/schema/format").asString()).isEqualTo("uuid");
        assertThat(get.at("/parameters/1/in").asString()).isEqualTo("header");
        assertThat(get.get("responses").propertyNames()).as("codes sorted, default last").containsExactly("200", "404", "default");
        assertThat(get.at("/responses/404/description").asString()).isEqualTo("Not Found");
        assertThat(get.at("/responses/default/description").asString()).isEqualTo("Unexpected error");
        assertThat(get.at("/responses/200/content/application~1json/schema/$ref").asString()).isEqualTo("#/components/schemas/Cart");
        assertThat(get.at("/security/0/bearerAuth").isArray()).isTrue();
        assertThat(get.at("/security/1/apiKeyAuth").isArray()).isTrue();

        JsonNode post = path.get("post");
        assertThat(post.at("/requestBody/required").asBoolean()).isTrue();
        assertThat(post.at("/requestBody/content/application~1json/schema/$ref").asString()).isEqualTo("#/components/schemas/Item");
        assertThat(post.at("/responses/204/content").isMissingNode()).isTrue();
        assertThat(post.has("security")).isFalse();

        assertThat(doc.at("/components/securitySchemes").propertyNames()).containsExactly("bearerAuth", "apiKeyAuth");
        assertThat(doc.at("/components/securitySchemes/bearerAuth/bearerFormat").asString()).isEqualTo("JWT");
    }

    @Test
    void mapsPayloadsToJsonSchemaWithConstraints() {
        GenerationResult result = generator.generate(project());
        JsonNode schemas = doc(result).at("/components/schemas");
        assertThat(schemas.propertyNames()).as("only payloads REST endpoints reach, sorted").containsExactly("Cart", "Item", "Problem");

        JsonNode cartSchema = schemas.get("Cart");
        assertThat(cartSchema.get("required").findValuesAsString("")).isEmpty();
        assertThat(cartSchema.get("required").toString()).isEqualTo("[\"id\",\"items\"]");
        assertThat(cartSchema.at("/properties/items/type").asString()).isEqualTo("array");
        assertThat(cartSchema.at("/properties/items/items/$ref").asString()).isEqualTo("#/components/schemas/Item");
        assertThat(cartSchema.at("/properties/items/minItems").asInt()).isEqualTo(1);
        assertThat(cartSchema.at("/properties/createdAt/format").asString()).isEqualTo("date-time");

        JsonNode p = schemas.at("/Item/properties");
        assertThat(schemas.at("/Item/description").asString()).isEqualTo("One line");
        assertThat(schemas.at("/Item/examples/0/qty").asInt()).isEqualTo(2);
        assertThat(p.at("/sku/pattern").asString()).isEqualTo("[A-Z0-9-]+");
        assertThat(p.at("/sku/minLength").asInt()).isEqualTo(2);
        assertThat(p.at("/sku/examples/0").asString()).isEqualTo("A-1");
        assertThat(p.at("/qty/format").asString()).isEqualTo("int32");
        assertThat(p.at("/qty/minimum").toString()).as("whole numbers stay integers").isEqualTo("1");
        assertThat(p.at("/qty/examples/0").isNumber()).isTrue();
        assertThat(p.at("/price/minimum").asDouble()).isEqualTo(0.5);
        assertThat(p.at("/status/enum").toString()).isEqualTo("[\"NEW\",\"DONE\"]");
        assertThat(p.at("/email/format").asString()).isEqualTo("email");
        assertThat(p.at("/labels/items/pattern").asString()).as("value constraints go on the items").isEqualTo("[a-z]+");
        assertThat(p.at("/labels/maxItems").asInt()).isEqualTo(5);

        assertThat(result.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.severity()).isEqualTo(Problem.Severity.WARNING);
            assertThat(problem.message()).contains("'price' is not a valid DECIMAL");
            assertThat(problem.location()).isEqualTo("/components/schemas/Item/properties/price/examples");
        });
    }

    @Test
    void isDeterministicAndYamlMatchesJson() {
        GenerationResult a = generator.generate(project());
        GenerationResult b = generator.generate(project());
        assertThat(a.files()).isEqualTo(b.files());
        assertThat(SpecWriter.readYaml(a.file("openapi.yaml").content())).isEqualTo(doc(a));
        assertThat(a.file("openapi.yaml").content()).contains("\"200\":").doesNotContain("---");
    }

    @Test
    void generatedDocumentPassesEveryValidator() {
        GenerationResult result = generator.generate(project());
        assertThat(new SpecValidator().validateOpenApi(result.file("openapi.json").content(), result.file("openapi.yaml").content())).isEmpty();
    }

    @Test
    void noRestEndpointsMeansNoDocument() {
        GenerationResult result = generator.generate(Snapshots.project(List.of(cart),
                List.of(async("x", "a.b", AsyncOperation.SEND, MessagingProtocol.KAFKA, "Cart"))));
        assertThat(result.files()).isEmpty();
        assertThat(result.problems()).extracting(Problem::severity).containsExactly(Problem.Severity.INFO);
    }
}
