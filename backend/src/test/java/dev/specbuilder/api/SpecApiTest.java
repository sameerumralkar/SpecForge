package dev.specbuilder.api;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class SpecApiTest extends ApiTestSupport {

    private String sample() {
        String id = post("/api/projects/samples/order-service", null).expect(201).id();
        track(id);
        return id;
    }

    @Test
    void theSampleProducesTwoValidSpecs() {
        String p = sample();
        Response specs = get("/api/projects/" + p + "/specs").expect(200);
        for (String kind : List.of("openapi", "asyncapi")) {
            JsonNode spec = specs.json().get(kind);
            assertThat(spec.get("present").asBoolean()).isTrue();
            assertThat(spec.get("valid").asBoolean()).as(kind + " problems: " + spec.get("problems")).isTrue();
            assertThat(spec.get("problems").findValuesAsString("severity")).doesNotContain("ERROR", "WARNING");
            assertThat(spec.get("yaml").asString()).startsWith(kind.equals("openapi") ? "openapi: 3.1.0" : "asyncapi: 3.0.0");
        }
        JsonNode openapi = JSON.readTree(specs.json().at("/openapi/json").asString());
        assertThat(openapi.get("paths").propertyNames()).containsExactly("/orders", "/orders/{orderId}");
        assertThat(openapi.at("/components/schemas").propertyNames())
                .containsExactly("Address", "CreateOrderRequest", "Customer", "Order", "OrderError", "OrderItem", "OrderPage");
    }

    @Test
    void specsFollowEditsAndOmitWhatTheProjectLacks() {
        String p = newProject();
        String payload = payload(p, "Thing", field("id", "UUID", "required", true));
        post("/api/projects/" + p + "/endpoints", rest("getThing", "GET", "/things", null, payload)).expect(201);

        Response specs = get("/api/projects/" + p + "/specs").expect(200);
        assertThat(specs.json().at("/asyncapi/present").asBoolean()).isFalse();
        assertThat(specs.json().at("/asyncapi/problems/0/message").asString()).contains("no async endpoints");
        get("/api/projects/" + p + "/specs/asyncapi.yaml").expect(404);

        put("/api/projects/" + p + "/payloads/" + payload, payloadBody("Widget", field("id", "UUID"))).expect(200);
        assertThat(get("/api/projects/" + p + "/specs").json().at("/openapi/yaml").asString())
                .contains("#/components/schemas/Widget").doesNotContain("schemas/Thing");
    }

    @Test
    void downloadsSingleFilesAndADeterministicZip() throws Exception {
        String p = sample();
        MvcResult file = mvc.perform(MockMvcRequestBuilders.get("/api/projects/" + p + "/specs/openapi.yaml")).andReturn();
        assertThat(file.getResponse().getStatus()).isEqualTo(200);
        assertThat(file.getResponse().getHeader("Content-Disposition")).isEqualTo("attachment; filename=\"openapi.yaml\"");
        assertThat(file.getResponse().getContentAsString()).startsWith("openapi: 3.1.0");
        get("/api/projects/" + p + "/specs/secrets.txt").expect(404);

        MvcResult zip1 = mvc.perform(MockMvcRequestBuilders.get("/api/projects/" + p + "/generate/specs.zip")).andReturn();
        MvcResult zip2 = mvc.perform(MockMvcRequestBuilders.get("/api/projects/" + p + "/generate/specs.zip")).andReturn();
        assertThat(zip1.getResponse().getHeader("Content-Disposition")).isEqualTo("attachment; filename=\"order-service-specs.zip\"");
        byte[] bytes = zip1.getResponse().getContentAsByteArray();
        assertThat(bytes).isEqualTo(zip2.getResponse().getContentAsByteArray());
        List<String> names = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                names.add(e.getName());
            }
        }
        assertThat(names).containsExactly("asyncapi.json", "asyncapi.yaml", "openapi.json", "openapi.yaml");
    }
}
