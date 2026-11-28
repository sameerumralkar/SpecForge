package dev.specbuilder.api;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The four reports: projects, endpoints per project, objects, and fields with the objects they hold. */
class ReportApiTest extends ApiTestSupport {

    /** Money (object) inside Order (structure) twice, an item list, two endpoints, one of them mapped. */
    private String shop() {
        String p = newProject();
        String api = "/api/projects/" + p;
        String money = post(api + "/payloads", map("name", "Money", "role", "OBJECT", "fields", List.of(
                field("value", "DECIMAL", "required", true, "minimum", 0),
                field("currency", "STRING", "required", true, "minLength", 3, "maxLength", 3, "pattern", "[A-Z]{3}")))).expect(201).id();
        String item = post(api + "/payloads", map("name", "Item", "role", "OBJECT", "fields", List.of(
                field("sku", "STRING", "required", true), ref("price", money)))).expect(201).id();
        String order = payload(p, "Order", field("id", "UUID", "required", true), ref("total", money), listOf("items", item));
        post(api + "/field-definitions", map("name", "currency", "type", "STRING")).expect(201);
        String create = post(api + "/endpoints", rest("createOrder", "POST", "/orders", order, order)).expect(201).id();
        post(api + "/endpoints", async("orderCreated", "orders.created", "SEND", order)).expect(201);
        String cap = post(api + "/capabilities", map("name", "Ordering")).expect(201).id();
        put(api + "/capabilities/" + cap + "/apis", map("ids", List.of(create))).expect(200);
        post(api + "/error-codes", map("code", "ORD-1", "message", "Bad order", "category", "VALIDATION")).expect(201);
        return p;
    }

    @Test
    void projectsReportCountsWhatEachProjectHolds() {
        String p = shop();
        JsonNode report = get("/api/reports/projects?projectId=" + p).expect(200).json();
        JsonNode row = report.get("projects").get(0);
        assertThat(report.at("/totals/projects").asInt()).isEqualTo(1);
        assertThat(row.get("endpoints").asInt()).isEqualTo(2);
        assertThat(row.get("restEndpoints").asInt()).isEqualTo(1);
        assertThat(row.get("asyncEndpoints").asInt()).isEqualTo(1);
        assertThat(row.get("objects").asInt()).isEqualTo(2);
        assertThat(row.get("structures").asInt()).isEqualTo(1);
        assertThat(row.get("fields").asInt()).isEqualTo(7);
        assertThat(row.get("fieldDefinitions").asInt()).isEqualTo(1);
        assertThat(row.get("errorCodes").asInt()).isEqualTo(1);
        assertThat(row.get("capabilities").asInt()).isEqualTo(1);
        assertThat(row.get("mappedEndpoints").asInt()).isEqualTo(1);
        assertThat(row.get("basePackage").asString()).isEqualTo("com.example.test");

        // Without a project, every project is in it, and the totals add up.
        String other = newProject();
        JsonNode all = get("/api/reports/projects").json();
        assertThat(all.get("projects").findValuesAsString("id")).contains(p, other);
        int endpoints = 0;
        for (JsonNode r : all.get("projects")) endpoints += r.get("endpoints").asInt();
        assertThat(all.at("/totals/endpoints").asInt()).isEqualTo(endpoints);

        get("/api/reports/projects?projectId=00000000-0000-0000-0000-000000000000").expect(404);
    }

    @Test
    void endpointsReportCountsPerProjectAndListsEachEndpoint() {
        String p = shop();
        JsonNode report = get("/api/reports/endpoints?projectId=" + p).expect(200).json();
        assertThat(report.get("total").asInt()).isEqualTo(2);
        JsonNode count = report.get("perProject").get(0);
        assertThat(List.of(count.get("total").asInt(), count.get("rest").asInt(), count.get("async").asInt(), count.get("mapped").asInt()))
                .containsExactly(2, 1, 1, 1);

        JsonNode create = report.get("endpoints").get(0);
        assertThat(create.get("name").asString()).isEqualTo("createOrder");
        assertThat(create.get("method").asString()).isEqualTo("POST");
        assertThat(create.get("path").asString()).isEqualTo("/orders");
        assertThat(create.get("requestPayload").asString()).isEqualTo("Order");
        assertThat(texts(create.get("responses"), null)).containsExactly("200 Order");
        assertThat(texts(create.get("capabilities"), null)).containsExactly("L1 Ordering");

        JsonNode event = report.get("endpoints").get(1);
        assertThat(event.get("kind").asString()).isEqualTo("ASYNC");
        assertThat(event.get("channel").asString()).isEqualTo("orders.created");
        assertThat(event.get("protocol").asString()).isEqualTo("KAFKA");
        assertThat(event.get("messagePayload").asString()).isEqualTo("Order");
        assertThat(event.get("method").isNull()).isTrue();
    }

    @Test
    void objectsReportShowsFieldsLinkedObjectsAndUses() {
        String p = shop();
        JsonNode report = get("/api/reports/objects?projectId=" + p).expect(200).json();
        assertThat(List.of(report.get("total").asInt(), report.get("objects").asInt(), report.get("structures").asInt())).containsExactly(3, 2, 1);
        JsonNode rows = report.get("payloads");
        assertThat(texts(rows, "name")).containsExactly("Item", "Money", "Order");

        JsonNode money = rows.get(1);
        assertThat(money.get("role").asString()).isEqualTo("OBJECT");
        assertThat(money.get("fields").asInt()).isEqualTo(2);
        assertThat(money.get("usedBy").asInt()).isEqualTo(2);
        assertThat(texts(money.get("usages"), null)).containsExactly("Item (field price)", "Order (field total)");

        JsonNode order = rows.get(2);
        assertThat(order.get("objectFields").asInt()).isEqualTo(2);
        assertThat(texts(order.get("linkedObjects"), null)).containsExactly("Item", "Money");
        assertThat(texts(order.get("usages"), null)).contains("createOrder (request body)", "orderCreated (message on orders.created)");
    }

    @Test
    void fieldsReportListsEveryFieldAndTheObjectsTheyHold() {
        String p = shop();
        JsonNode report = get("/api/reports/fields?projectId=" + p).expect(200).json();
        assertThat(List.of(report.get("total").asInt(), report.get("linked").asInt(), report.get("linkedObjects").asInt())).containsExactly(7, 3, 2);

        JsonNode perObject = report.get("perObject");
        assertThat(texts(perObject, "objectName")).containsExactly("Item", "Money");
        assertThat(perObject.get(1).get("fields").asInt()).isEqualTo(2);
        assertThat(texts(perObject.get(1).get("heldBy"), null)).containsExactly("Item.price", "Order.total");

        JsonNode items = null;
        JsonNode currency = null;
        for (JsonNode f : report.get("fields")) {
            if (f.get("name").asString().equals("items")) items = f;
            if (f.get("name").asString().equals("currency")) currency = f;
        }
        assertThat(items.get("type").asString()).isEqualTo("ARRAY");
        assertThat(items.get("itemType").asString()).isEqualTo("OBJECT");
        assertThat(items.get("linkedObject").asString()).isEqualTo("Item");
        assertThat(items.get("payloadName").asString()).isEqualTo("Order");
        assertThat(currency.get("constraints").asString()).isEqualTo("length 3..3; pattern [A-Z]{3}");
        assertThat(currency.get("inFieldInventory").asBoolean()).isTrue();
        assertThat(currency.get("linkedObject").isNull()).isTrue();
    }

    @Test
    void theSampleProjectReports() {
        String p = post("/api/projects/samples/order-service", null).expect(201).id();
        track(p);
        for (String report : List.of("projects", "endpoints", "objects", "fields")) {
            get("/api/reports/" + report + "?projectId=" + p).expect(200);
        }
        assertThat(get("/api/reports/endpoints?projectId=" + p).json().get("total").asInt()).isPositive();
    }

    @Test
    void capabilityMapLinksCapabilitiesEndpointsRulesObjectsAndFields() {
        String p = newProject();
        String api = "/api/projects/" + p;
        String money = post(api + "/payloads", map("name", "Money", "role", "OBJECT", "fields", List.of(field("value", "DECIMAL")))).expect(201).id();
        String order = payload(p, "Order", field("id", "UUID", "required", true), ref("total", money));
        String receipt = payload(p, "Receipt", field("id", "UUID"));
        String create = post(api + "/endpoints", rest("createOrder", "POST", "/orders", order, order)).expect(201).id();
        post(api + "/endpoints/" + create + "/rules", rule("TRANSFORM", "To receipt", map("targetPayloadId", receipt))).expect(201);
        String l1 = post(api + "/capabilities", map("name", "Commerce")).expect(201).id();
        String l2 = post(api + "/capabilities", map("name", "Ordering", "parentId", l1)).expect(201).id();
        put(api + "/capabilities/" + l2 + "/apis", map("ids", List.of(create))).expect(200);

        JsonNode m = get("/api/reports/map?projectId=" + p).expect(200).json();
        assertThat(m.get("levelNames").size()).isEqualTo(8);
        assertThat(texts(m.get("capabilities"), "name")).containsExactly("Commerce", "Ordering");
        assertThat(m.get("capabilities").get(1).get("parentId").asString()).isEqualTo(l1);
        assertThat(texts(m.get("capabilities").get(1).get("endpointIds"), null)).containsExactly(create);

        JsonNode ep = m.get("endpoints").get(0);
        assertThat(texts(ep.get("uses"), "how")).containsExactly("request body", "response 200", "rule 'To receipt'");
        assertThat(ep.at("/rules/0/typeLabel").asString()).isEqualTo("Transform / map");
        assertThat(texts(ep.at("/rules/0/payloadIds"), null)).containsExactly(receipt);

        JsonNode orderRow = m.get("payloads").get(1);
        assertThat(orderRow.get("name").asString()).isEqualTo("Order");
        assertThat(orderRow.at("/fields/1/refPayloadId").asString()).isEqualTo(money);

        get("/api/reports/map").expect(400);
    }
}
