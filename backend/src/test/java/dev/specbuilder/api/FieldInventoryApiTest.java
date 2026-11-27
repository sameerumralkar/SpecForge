package dev.specbuilder.api;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The field inventory: reusable plain fields, their checks, error codes, and export and import. */
class FieldInventoryApiTest extends ApiTestSupport {

    @Test
    void createsValidatesUpdatesAndDeletesDefinitions() {
        String p = newProject();
        String base = "/api/projects/" + p + "/field-definitions";
        String code = post("/api/projects/" + p + "/error-codes", map("code", "CUR-001", "message", "Unknown currency", "category", "VALIDATION")).id();

        JsonNode created = post(base, map("name", "currency", "type", "STRING", "required", true, "minLength", 3, "maxLength", 3,
                "pattern", "[A-Z]{3}", "errorCodeIds", List.of(code))).expect(201).json();
        String id = created.get("id").asString();
        assertThat(created.at("/field/maxLength").asInt()).isEqualTo(3);
        assertThat(created.at("/field/errorCodeIds/0").asString()).isEqualTo(code);
        assertThat(created.get("usedIn").size()).isZero();

        Response bad = post(base, map("name", "Currency", "type", "INTEGER", "pattern", "[")).expect(400);
        assertThat(bad.errorFields()).containsExactlyInAnyOrder("name", "pattern");
        assertThat(post(base, map("name", "address", "type", "OBJECT")).expect(400).errorsFor("type"))
                .anyMatch(m -> m.contains("object inventory"));
        assertThat(post(base, map("name", "x", "type", "STRING", "errorCodeIds", List.of("00000000-0000-0000-0000-000000000000")))
                .expect(400).errorFields()).containsExactly("errorCodeIds");

        // "usedIn" lists payload fields with the same name.
        payload(p, "Money", field("value", "DECIMAL"), field("currency", "STRING"));
        assertThat(get(base + "/" + id).json().get("usedIn").get(0).asString()).isEqualTo("Money.currency");

        // An update without errorCodeIds keeps them; deleting the code takes it off.
        JsonNode updated = put(base + "/" + id, map("name", "currencyCode", "type", "STRING", "required", false)).expect(200).json();
        assertThat(updated.at("/field/name").asString()).isEqualTo("currencyCode");
        assertThat(updated.at("/field/errorCodeIds/0").asString()).isEqualTo(code);
        delete("/api/projects/" + p + "/error-codes/" + code).expect(204);
        assertThat(get(base + "/" + id).json().at("/field/errorCodeIds").size()).isZero();

        delete(base + "/" + id).expect(204);
        assertThat(get(base).json().size()).isZero();
        get(base + "/" + id).expect(404);
    }

    @Test
    void theSampleHasAFieldInventoryThatExportsAndImports() {
        String p = post("/api/projects/samples/order-service", null).expect(201).id();
        track(p);
        JsonNode inventory = get("/api/projects/" + p + "/field-definitions").json();
        assertThat(inventory.size()).isEqualTo(8);
        assertThat(inventory.findValuesAsString("name")).containsExactly(
                "amount", "country", "createdAt", "customerId", "email", "id", "quantity", "sku");
        assertThat(texts(inventory.get(7).get("usedIn"), null)).containsExactly("OrderItem.sku");

        JsonNode export = get("/api/projects/" + p + "/export").json();
        assertThat(export.get("fieldInventory").size()).isEqualTo(8);
        ((ObjectNode) export.get("fieldInventory").get(0)).set("errorCodes", JSON.valueToTree(List.of("ORD-001")));
        String copy = post("/api/projects/import?name=Fields copy", export).expect(201).json().at("/project/id").asString();
        track(copy);
        JsonNode copied = get("/api/projects/" + copy + "/field-definitions").json();
        assertThat(copied.size()).isEqualTo(8);
        String ord001 = get("/api/projects/" + copy + "/error-codes").json().get(0).get("id").asString();
        assertThat(copied.at("/0/field/errorCodeIds/0").asString()).isEqualTo(ord001);

        ((ObjectNode) export.get("fieldInventory").get(1)).put("type", "OBJECT");
        assertThat(post("/api/projects/import?name=Bad", export).expect(400).errorFields()).containsExactly("fieldInventory[1].type");
    }
}
