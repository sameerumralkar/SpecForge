package dev.specbuilder.api;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

class SampleProjectApiTest extends ApiTestSupport {

    @Test
    void theOrderServiceSampleIsCompleteAndClean() {
        Response first = post("/api/projects/samples/order-service", null).expect(201);
        track(first.id());
        Response second = post("/api/projects/samples/order-service", null).expect(201);
        track(second.id());
        assertThat(second.json().get("name").asString()).isEqualTo(first.json().get("name").asString() + " 2");

        String p = first.id();
        assertThat(first.json().get("payloadCount").asInt()).isEqualTo(9);
        assertThat(first.json().get("endpointCount").asInt()).isEqualTo(5);

        int ruleCount = 0;
        for (JsonNode e : get("/api/projects/" + p + "/endpoints").expect(200).json()) {
            JsonNode chain = get("/api/projects/" + p + "/endpoints/" + e.get("id").asString() + "/rules").expect(200).json();
            assertThat(chain.get("warnings").size()).as(e.get("name") + ": " + chain.get("warnings")).isZero();
            ruleCount += chain.get("rules").size();
        }
        assertThat(ruleCount).isEqualTo(10);
    }
}
