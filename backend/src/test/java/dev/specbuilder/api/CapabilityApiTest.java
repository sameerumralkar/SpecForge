package dev.specbuilder.api;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The functional capability model: eight levels, moves, the API inventory at each level, links, export. */
class CapabilityApiTest extends ApiTestSupport {

    @Test
    void buildsATreeOfUpToEightLevelsWithItsNames() {
        String p = newProject();
        String base = "/api/projects/" + p + "/capabilities";
        assertThat(texts(get("/api/projects/" + p + "/capability-levels").json().get("levels"), null))
                .startsWith("Business domain", "Capability area", "Capability").hasSize(8);

        String parent = null;
        List<String> ids = new ArrayList<>();
        for (int level = 1; level <= 8; level++) {
            JsonNode c = post(base, map("name", "Level " + level, "code", "C" + level, "parentId", parent)).expect(201).json();
            assertThat(c.get("level").asInt()).isEqualTo(level);
            parent = c.get("id").asString();
            ids.add(parent);
        }
        JsonNode l8 = get(base + "/" + ids.get(7)).json();
        assertThat(l8.get("levelName").asString()).isEqualTo("Task");
        assertThat(texts(l8.get("path"), null)).containsExactly("Level 1", "Level 2", "Level 3", "Level 4", "Level 5", "Level 6", "Level 7", "Level 8");
        assertThat(post(base, map("name", "Too deep", "parentId", ids.get(7))).expect(400).errorsFor("parentId"))
                .anyMatch(m -> m.contains("only 8 levels"));

        Response bad = post(base, map("name", " ", "code", "C1", "parentId", "00000000-0000-0000-0000-000000000000")).expect(400);
        assertThat(bad.errorFields()).containsExactlyInAnyOrder("name", "code", "parentId");
        assertThat(post(base, map("name", "level 1")).expect(400).errorsFor("name")).anyMatch(m -> m.contains("already called 'Level 1'"));

        // Level names: eight, or none for the defaults.
        String levels = "/api/projects/" + p + "/capability-levels";
        assertThat(put(levels, map("levels", List.of("Domain", "Area"))).expect(400).errorFields()).containsExactly("levels");
        List<String> names = List.of("Domain", "Area", "Capability", "Sub", "Function", "Process", "Activity", "Step");
        put(levels, map("levels", names)).expect(200);
        assertThat(get(base + "/" + ids.get(7)).json().get("levelName").asString()).isEqualTo("Step");
        put(levels, map("levels", List.of())).expect(200);
        assertThat(get(base + "/" + ids.get(1)).json().get("levelName").asString()).isEqualTo("Capability area");
    }

    @Test
    void movesKeepTheTreeConsistent() {
        String p = newProject();
        String base = "/api/projects/" + p + "/capabilities";
        String a = post(base, map("name", "A")).id();
        String b = post(base, map("name", "B")).id();
        String a1 = post(base, map("name", "A1", "parentId", a)).id();
        String a11 = post(base, map("name", "A11", "parentId", a1)).id();

        // Under itself or under its own child: refused.
        assertThat(put(base + "/" + a, map("name", "A", "parentId", a11)).expect(400).errorFields()).containsExactly("parentId");

        // Move A1 (with A11) under B: levels follow.
        put(base + "/" + a1, map("name", "A1", "parentId", b)).expect(200);
        assertThat(get(base + "/" + a11).json().get("level").asInt()).isEqualTo(3);
        assertThat(texts(get(base + "/" + a11).json().get("path"), null)).containsExactly("B", "A1", "A11");

        // Promote to L1, placed first.
        put(base + "/" + a1, map("name", "A1", "parentId", null, "position", 0)).expect(200);
        assertThat(texts(get(base).json(), "name")).containsExactly("A1", "A11", "A", "B");
        assertThat(get(base + "/" + a11).json().get("level").asInt()).isEqualTo(2);

        // Reorder siblings.
        put(base + "/" + b, map("name", "B", "position", 0)).expect(200);
        assertThat(texts(get(base).json(), "name")).containsExactly("B", "A1", "A11", "A");

        // Deleting a capability deletes what is below it.
        delete(base + "/" + a1).expect(204);
        assertThat(texts(get(base).json(), "name")).containsExactly("B", "A");
        get(base + "/" + a11).expect(404);
    }

    @Test
    void theApiInventoryAtEachLevelRollsUpWhatIsLinkedBelow() {
        String p = newProject();
        String api = "/api/projects/" + p;
        String base = api + "/capabilities";
        String money = payload(p, "Money", field("value", "DECIMAL"));
        String pay = post(api + "/endpoints", rest("pay", "POST", "/pay", money, money)).expect(201).id();
        String refund = post(api + "/endpoints", rest("refund", "POST", "/refunds", money, money)).expect(201).id();
        String status = post(api + "/endpoints", rest("status", "GET", "/status", null, money)).expect(201).id();

        String domain = post(base, map("name", "Payments")).id();
        String area = post(base, map("name", "Card payments", "parentId", domain)).id();
        String capture = post(base, map("name", "Capture", "parentId", area)).id();
        String refunds = post(base, map("name", "Refunds", "parentId", area)).id();

        put(base + "/" + capture + "/apis", map("ids", List.of(pay))).expect(200);
        put(base + "/" + refunds + "/apis", map("ids", List.of(refund, pay))).expect(200);
        put(base + "/" + domain + "/apis", map("ids", List.of(status))).expect(200);
        assertThat(put(base + "/" + area + "/apis", map("ids", List.of(money))).expect(400).errorFields()).containsExactly("ids[0]");

        // At L1: its own API plus everything below, each once, with where it is linked.
        JsonNode inventory = get(base + "/" + domain + "/apis").expect(200).json();
        assertThat(texts(inventory, "name")).containsExactly("pay", "refund", "status");
        assertThat(inventory.get(2).get("direct").asBoolean()).isTrue();
        assertThat(texts(inventory.get(0).get("via"), "name")).containsExactly("Capture", "Refunds");
        assertThat(texts(get(base).json(), "apiCount")).containsExactly("3", "2", "1", "2");
        assertThat(texts(get(base + "/" + area + "/apis").json(), "name")).containsExactly("pay", "refund");

        // From the API's side.
        assertThat(texts(get(api + "/endpoints/" + pay + "/capabilities").json(), "name")).containsExactly("Capture", "Refunds");
        put(api + "/endpoints/" + pay + "/capabilities", map("ids", List.of(area))).expect(200);
        assertThat(texts(get(api + "/endpoints/" + pay + "/capabilities").json(), "name")).containsExactly("Card payments");
        assertThat(get(base + "/" + refunds).json().get("endpointIds").size()).isEqualTo(1);

        // A duplicated API keeps its capabilities; a deleted one leaves them.
        String copy = post(api + "/endpoints/" + pay + "/duplicate", null).expect(201).id();
        assertThat(texts(get(api + "/endpoints/" + copy + "/capabilities").json(), "name")).containsExactly("Card payments");
        delete(api + "/endpoints/" + copy).expect(204);
        delete(api + "/endpoints/" + refund).expect(204);
        assertThat(texts(get(base + "/" + domain + "/apis").json(), "name")).containsExactly("pay", "status");
    }

    @Test
    void theSampleHasACapabilityMapThatExportsAndImports() {
        String p = post("/api/projects/samples/order-service", null).expect(201).id();
        track(p);
        JsonNode all = get("/api/projects/" + p + "/capabilities").json();
        assertThat(texts(all, "name")).containsExactly("Commerce", "Order management", "Order capture", "Place order", "Order inquiry",
                "Payment handling", "Payment settlement", "Refunds");
        assertThat(all.get(0).get("apiCount").asInt()).isEqualTo(5);
        assertThat(all.get(7).get("apiCount").asInt()).isZero();

        JsonNode export = get("/api/projects/" + p + "/export").json();
        JsonNode commerce = export.get("capabilities").get(0);
        assertThat(commerce.get("code").asString()).isEqualTo("COM");
        assertThat(texts(commerce.at("/children/0/children/0/children/0/apis"), null)).containsExactly("createOrder", "orderCreated");
        assertThat(export.path("capabilityLevels").isNull()).isTrue();

        String copy = post("/api/projects/import?name=Capabilities copy", export).expect(201).json().at("/project/id").asString();
        track(copy);
        JsonNode copied = get("/api/projects/" + copy + "/capabilities").json();
        assertThat(texts(copied, "name")).isEqualTo(texts(all, "name"));
        assertThat(texts(copied, "apiCount")).isEqualTo(texts(all, "apiCount"));

        ((ObjectNode) commerce).set("apis", JSON.valueToTree(List.of("noSuchApi")));
        assertThat(post("/api/projects/import?name=Bad", export).expect(400).errorFields()).containsExactly("capabilities[0].apis[0]");
    }
}
