package dev.specbuilder.api;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CodeApiTest extends ApiTestSupport {

    private String sample() {
        String id = post("/api/projects/samples/order-service", null).expect(201).id();
        track(id);
        return id;
    }

    @Test
    void previewsTheGeneratedProject() {
        String p = sample();
        Response code = get("/api/projects/" + p + "/code").expect(200);
        assertThat(code.json().get("ready").asBoolean()).as(code.json().get("problems").toString()).isTrue();
        List<String> paths = texts(code.json().get("files"), "path");
        assertThat(paths).doesNotContain("mvnw", "mvnw.cmd", ".mvn/wrapper/maven-wrapper.properties").contains("pom.xml", "README.md", "src/main/resources/application.yml",
                "src/main/java/com/example/orders/OrderServiceApplication.java",
                "src/main/java/com/example/orders/dto/CreateOrderRequest.java",
                "src/main/java/com/example/orders/web/OrdersController.java",
                "src/main/java/com/example/orders/service/OrdersServiceImpl.java",
                "src/main/java/com/example/orders/route/HandlePaymentCompletedRoute.java",
                "src/main/java/com/example/orders/persistence/OrderEntity.java",
                "src/main/java/com/example/orders/client/CustomerServiceClient.java",
                "src/main/java/com/example/orders/mapper/DtoMapper.java",
                "src/test/java/com/example/orders/support/ExpressionsTest.java");
        assertThat(paths).isSorted();
        assertThat(code.json().at("/endpointFiles/handlePaymentCompleted").asString()).endsWith("route/HandlePaymentCompletedRoute.java");
        assertThat(code.json().at("/endpointFiles/createOrder").asString()).endsWith("service/OrdersServiceImpl.java");
        JsonNode impl = file(code.json(), "src/main/java/com/example/orders/service/OrdersServiceImpl.java");
        assertThat(impl.get("language").asString()).isEqualTo("java");
        assertThat(impl.get("content").asString())
                .contains("Order order = mapToOrder(createOrderRequest);")
                .contains("customerServiceResilience(() -> enrichCustomer($in1)")
                .contains("messaging.sendKafka(\"orders.created\"");
    }

    @Test
    void blocksTheDownloadWhileRulesHaveWarnings() throws Exception {
        String p = sample();
        String endpoint = texts(get("/api/projects/" + p + "/endpoints").json(), "id").getFirst();
        post("/api/projects/" + p + "/endpoints/" + endpoint + "/rules",
                rule("VALIDATE", "Broken", map("checks", List.of(map("field", "nope", "kind", "REQUIRED"))))).expect(201);

        Response code = get("/api/projects/" + p + "/code").expect(200);
        assertThat(code.json().get("ready").asBoolean()).isFalse();
        assertThat(texts(code.json().get("problems"), "source")).contains("rules");

        MvcResult zip = mvc.perform(MockMvcRequestBuilders.get("/api/projects/" + p + "/generate/code.zip")).andReturn();
        assertThat(zip.getResponse().getStatus()).isEqualTo(409);
        assertThat(zip.getResponse().getContentAsString()).contains("Code not ready").contains("'nope' is not a field");
    }

    @Test
    void routesGuardOnlyTheStepAnErrorHandlerTargets() {
        String p = sample();
        JsonNode create = null;
        for (JsonNode e : get("/api/projects/" + p + "/endpoints").json()) {
            if (e.get("name").asString().equals("createOrder")) create = e;
        }
        ((tools.jackson.databind.node.ObjectNode) create).put("implementationStyle", "PROCESSING_ROUTE");
        put("/api/projects/" + p + "/endpoints/" + create.get("id").asString(), create).expect(200);

        Response code = get("/api/projects/" + p + "/code").expect(200);
        assertThat(code.json().get("ready").asBoolean()).as(code.json().get("problems").toString()).isTrue();
        String route = file(code.json(), "src/main/java/com/example/orders/route/CreateOrderRoute.java").get("content").asString();
        assertThat(route)
                .contains(".doTry()\n                .to(\"direct:createOrder-enrichCustomer-guarded\")")
                .contains(".doCatch(DownstreamUnavailableException.class, DownstreamTimeoutException.class)")
                .contains(".setHeader(Exchange.HTTP_RESPONSE_CODE, constant(503))")
                .contains("from(\"direct:createOrder-enrichCustomer-guarded\")")
                .contains(".maximumRedeliveries(2)")
                .doesNotContainPattern("(?<![A-Za-z])onException\\(");

        String sequence = null;
        for (JsonNode d : get("/api/projects/" + p + "/diagrams").json().get("diagrams")) {
            if (d.get("id").asString().equals("sequence-createOrder")) sequence = d.get("mermaid").asString();
        }
        assertThat(sequence).contains("loop up to 3 attempts").contains("break downstream unavailable or timeout after 3 attempts")
                .doesNotContain("if a step fails");
    }

    @Test
    void theZipIsTheSameEveryTime() throws Exception {
        String p = sample();
        byte[] a = mvc.perform(MockMvcRequestBuilders.get("/api/projects/" + p + "/generate/code.zip")).andReturn().getResponse().getContentAsByteArray();
        byte[] b = mvc.perform(MockMvcRequestBuilders.get("/api/projects/" + p + "/generate/code.zip")).andReturn().getResponse().getContentAsByteArray();
        assertThat(a).isNotEmpty().isEqualTo(b);
    }

    private static JsonNode file(JsonNode code, String path) {
        for (JsonNode f : code.get("files")) {
            if (f.get("path").asString().equals(path)) return f;
        }
        throw new AssertionError("no file " + path);
    }
}
