package dev.specbuilder.api;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Inventory objects vs structures: defaults, the inventory filter, export/import. */
class PayloadRoleApiTest extends ApiTestSupport {

    @Test
    void newPayloadsAreStructuresUnlessSaidOtherwiseAndUpdatesKeepTheRole() {
        String p = project();
        String base = "/api/projects/" + p + "/payloads";
        JsonNode body = post(base, map("name", "OrderRequest", "fields", List.of())).expect(201).json();
        assertThat(body.get("role").asString()).isEqualTo("STRUCTURE");
        String money = post(base, map("name", "Money", "role", "OBJECT", "fields", List.of(
                map("name", "value", "type", "DECIMAL", "required", true)))).expect(201).id();

        // An update without a role keeps it; with one, changes it.
        assertThat(put(base + "/" + money, map("name", "Money", "description", "An amount", "fields", List.of()))
                .expect(200).json().get("role").asString()).isEqualTo("OBJECT");
        assertThat(put(base + "/" + money, map("name", "Money", "role", "STRUCTURE", "fields", List.of()))
                .expect(200).json().get("role").asString()).isEqualTo("STRUCTURE");
        put(base + "/" + money, map("name", "Money", "role", "OBJECT", "fields", List.of())).expect(200);

        assertThat(names(get(base + "?role=OBJECT").expect(200).json())).containsExactly("Money");
        assertThat(names(get(base + "?role=STRUCTURE").expect(200).json())).containsExactly("OrderRequest");
        assertThat(names(get(base).expect(200).json())).containsExactly("Money", "OrderRequest");
        assertThat(post(base + "/" + money + "/duplicate", null).expect(201).json().get("role").asString()).isEqualTo("OBJECT");
        assertThat(get("/api/meta").json().get("payloadRoles").toString()).contains("OBJECT", "STRUCTURE");
    }

    @Test
    void theSampleKeepsItsBuildingBlocksInTheInventory() {
        String p = post("/api/projects/samples/order-service", null).expect(201).id();
        track(p);
        assertThat(names(get("/api/projects/" + p + "/payloads?role=OBJECT").json()))
                .containsExactly("Address", "Customer", "Order", "OrderItem");
    }

    @Test
    void exportsCarryTheRoleAndOlderExportsGetOneFromHowPayloadsAreUsed() {
        String p = project();
        String base = "/api/projects/" + p + "/payloads";
        String money = post(base, map("name", "Money", "role", "OBJECT", "fields", List.of(
                map("name", "value", "type", "DECIMAL", "required", true)))).expect(201).id();
        post(base, map("name", "Unused", "role", "OBJECT", "fields", List.of())).expect(201);
        post(base, map("name", "PaymentRequest", "fields", List.of(
                map("name", "amount", "type", "OBJECT", "refPayloadId", money)))).expect(201);

        JsonNode export = get("/api/projects/" + p + "/export").expect(200).json();
        assertThat(roles(export)).isEqualTo(Map.of("Money", "OBJECT", "Unused", "OBJECT", "PaymentRequest", "STRUCTURE"));

        JsonNode copy = post("/api/projects/import?name=Copy", export).expect(201).json();
        track(copy.at("/project/id").asString());
        assertThat(names(get("/api/projects/" + copy.at("/project/id").asString() + "/payloads?role=OBJECT").json()))
                .containsExactly("Money", "Unused");

        // An export from before roles: what other payloads use as a field type becomes an object.
        ((ObjectNode) export.at("/project")).put("name", "Old");
        export.get("payloads").forEach(pd -> ((ObjectNode) pd).remove("role"));
        JsonNode old = post("/api/projects/import", export).expect(201).json();
        track(old.at("/project/id").asString());
        assertThat(names(get("/api/projects/" + old.at("/project/id").asString() + "/payloads?role=OBJECT").json()))
                .containsExactly("Money");
    }

    private String project() {
        String id = post("/api/projects", map("name", "Roles " + System.nanoTime(), "basePackage", "com.example.roles",
                "version", "1.0.0", "defaultImplementationStyle", "IMPLEMENTATION_CLASS")).expect(201).id();
        track(id);
        return id;
    }

    private static List<String> names(JsonNode list) {
        List<String> names = new java.util.ArrayList<>();
        list.forEach(n -> names.add(n.get("name").asString()));
        return names;
    }

    private static Map<String, String> roles(JsonNode export) {
        Map<String, String> roles = new java.util.TreeMap<>();
        export.get("payloads").forEach(pd -> roles.put(pd.get("name").asString(), pd.get("role").asString()));
        return roles;
    }
}
