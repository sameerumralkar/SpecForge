package dev.specbuilder.api;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GenerationApiTest extends ApiTestSupport {

    private String sample() {
        String id = post("/api/projects/samples/order-service", null).expect(201).id();
        track(id);
        return id;
    }

    @Test
    void downloadsTakeSnapshotsAndDiffsShowWhatChanged() throws Exception {
        String p = sample();
        String base = "/api/projects/" + p;
        assertThat(get(base + "/generations?kind=CODE").json().size()).isZero();

        mvc.perform(MockMvcRequestBuilders.get(base + "/generate/code.zip")).andReturn();
        mvc.perform(MockMvcRequestBuilders.get(base + "/generate/code.zip")).andReturn();
        JsonNode list = get(base + "/generations?kind=CODE").expect(200).json();
        assertThat(list.size()).as("an identical download does not add a snapshot").isEqualTo(1);
        assertThat(list.at("/0/reason").asString()).isEqualTo("DOWNLOAD");
        assertThat(list.at("/0/fileCount").asInt()).isEqualTo(40);
        String snapshot = list.at("/0/id").asString();

        JsonNode unchanged = get(base + "/generations/" + snapshot + "/diff").expect(200).json();
        assertThat(texts(unchanged.get("files"), "status")).containsOnly("UNCHANGED");

        // Change something: rename a payload field and add an endpoint.
        Map<String, String> payloads = new HashMap<>();
        get(base + "/payloads").json().forEach(x -> payloads.put(x.get("name").asString(), x.get("id").asString()));
        JsonNode error = get(base + "/payloads/" + payloads.get("OrderError")).json();
        put(base + "/payloads/" + payloads.get("OrderError"), map("name", "OrderError", "fields", List.of(
                field("code", "STRING", "required", true), field("text", "STRING", "required", true)))).expect(200);
        post(base + "/endpoints", rest("ping", "GET", "/ping", null, null)).expect(201);

        JsonNode diff = get(base + "/generations/" + snapshot + "/diff").expect(200).json();
        assertThat(diff.at("/baseline/id").asString()).isEqualTo(snapshot);
        Map<String, JsonNode> byPath = new HashMap<>();
        diff.get("files").forEach(f -> byPath.put(f.get("path").asString(), f));
        JsonNode dto = byPath.get("src/main/java/com/example/orders/dto/OrderError.java");
        assertThat(dto.get("status").asString()).isEqualTo("CHANGED");
        assertThat(dto.get("before").asString()).contains("private String message;");
        assertThat(dto.get("after").asString()).contains("private String text;").doesNotContain("private String message;");
        assertThat(byPath.get("pom.xml").get("status").asString()).isEqualTo("UNCHANGED");
        assertThat(byPath.get("pom.xml").get("after").isNull()).isTrue();
        assertThat(error.get("name").asString()).isEqualTo("OrderError");
    }

    @Test
    void snapshotsCanBeTakenOnPurposeAndAreKeptForEachKind() {
        String p = sample();
        String base = "/api/projects/" + p;
        Response first = post(base + "/generations", map("kind", "SPECS")).expect(201);
        assertThat(first.json().get("reason").asString()).isEqualTo("MANUAL");
        assertThat(first.json().get("fileCount").asInt()).isEqualTo(4);
        assertThat(post(base + "/generations", map("kind", "SPECS")).expect(201).id()).as("same output, same snapshot").isEqualTo(first.id());

        for (int i = 0; i < 12; i++) {
            post(base + "/endpoints", rest("ping" + i, "GET", "/ping" + i, null, null)).expect(201);
            post(base + "/generations", map("kind", "SPECS")).expect(201);
        }
        assertThat(get(base + "/generations?kind=SPECS").json().size()).as("only the last 10 are kept").isEqualTo(10);
        assertThat(get(base + "/generations?kind=CODE").json().size()).isZero();

        JsonNode latest = get(base + "/generations?kind=SPECS").json().get(0);
        post(base + "/endpoints", rest("pong", "GET", "/pong", null, null)).expect(201);
        JsonNode diff = get(base + "/generations/" + latest.get("id").asString() + "/diff").json();
        assertThat(diff.get("files").findValuesAsString("status")).contains("CHANGED");
        assertThat(texts(diff.get("files"), "path")).containsExactly("asyncapi.json", "asyncapi.yaml", "openapi.json", "openapi.yaml");

        assertThat(post(base + "/generations", map()).expect(400).errorFields()).containsExactly("kind");
        get(base + "/generations/" + java.util.UUID.randomUUID() + "/diff").expect(404);
    }
}
