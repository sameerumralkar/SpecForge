package dev.specbuilder.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleApiTest extends ApiTestSupport {

    private String p;
    private String request;
    private String order;
    private String customer;
    private String rules;

    @BeforeEach
    void setUp() {
        p = newProject();
        String item = payload(p, "Item", field("sku", "STRING"), field("price", "DECIMAL"));
        customer = payload(p, "Customer", field("name", "STRING"));
        request = payload(p, "PlaceOrder", field("customerId", "UUID"), listOf("items", item), field("note", "STRING"));
        order = payload(p, "Order", field("id", "UUID"), field("customerId", "UUID"), ref("customer", customer), listOf("items", item));
        String endpoint = post("/api/projects/" + p + "/endpoints", rest("placeOrder", "POST", "/orders", request, order)).expect(201).id();
        rules = "/api/projects/" + p + "/endpoints/" + endpoint + "/rules";
    }

    @Test
    void buildsAChainWithSummariesAndNoWarnings() {
        post(rules, rule("VALIDATE", "Check", map("checks", List.of(map("field", "items[].sku", "kind", "NOT_BLANK"))))).expect(201);
        post(rules, rule("TRANSFORM", "To order", map("targetPayloadId", order,
                "mappings", List.of(map("target", "id", "expression", "randomUUID()"))))).expect(201);
        post(rules, rule("ENRICH", "Get customer", map("serviceName", "customer-service", "url", "/customers/{customerId}",
                "responsePayloadId", customer, "targetField", "customer"))).expect(201);
        post(rules, rule("SPLIT_AGGREGATE", "Price items", map("collectionField", "items", "aggregation", "SUM", "sumField", "price"))).expect(201);
        Response routed = post(rules, map("type", "CONDITIONAL_ROUTE", "name", "Big orders", "condition", "size(items) > 0",
                "config", map("branches", List.of(map("name", "bigOrder", "condition", "sum(items[].price) > 1000"))))).expect(201);
        assertThat(routed.json().get("summary").asString()).startsWith("If size(items) > 0: route the Order: if sum(items[].price) > 1000 → bigOrder");
        post(rules, rule("PERSIST", "Save", map("entityName", "OrderEntity"))).expect(201);
        post(rules, rule("PUBLISH_EVENT", "Announce", map("destination", "orders.placed", "keyField", "id"))).expect(201);
        post(rules, rule("ERROR_HANDLING", "Retry customer", map("targetRule", "Get customer", "retry", map("maxAttempts", 2)))).expect(201);

        Response chain = get(rules).expect(200);
        assertThat(chain.json().get("warnings").size()).as(chain.json().get("warnings").toString()).isZero();
        assertThat(chain.json().get("inputPayloadName").asString()).isEqualTo("PlaceOrder");
        assertThat(texts(chain.json().get("summary"), null)).containsExactly(
                "1. Validate the PlaceOrder: items[].sku is not blank. Reject with 400 if any check fails.",
                "2. Map the PlaceOrder to Order (1 explicit mapping, other fields by name).",
                "3. Call customer-service (GET /customers/{customerId}, timeout 2000 ms) and put the Customer into customer.",
                "4. Split items and process each item, then add up price.",
                "5. If size(items) > 0: route the Order: if sum(items[].price) > 1000 → bigOrder; otherwise continue.",
                "6. Save the Order as a new OrderEntity.",
                "7. Publish the Order to Kafka topic orders.placed keyed by id.",
                "8. On any error in 'Get customer': retry up to 2 times (500 ms backoff ×2).");
        assertThat(chain.json().at("/rules/7/config/retry/backoffMs").asLong()).as("defaults are stored").isEqualTo(500);
    }

    @Test
    void warnsAboutFieldsThatDoNotLineUp() {
        post(rules, rule("VALIDATE", "Check", map("checks", List.of(
                map("field", "total", "kind", "REQUIRED"),
                map("field", "note", "kind", "MIN", "value", "1"))))).expect(201);
        post(rules, rule("ENRICH", "Get customer", map("serviceName", "crm", "url", "/c", "targetField", "customer"))).expect(201);
        post(rules, rule("SPLIT_AGGREGATE", "Split", map("collectionField", "customerId"))).expect(201);
        post(rules, rule("PERSIST", "Save", map("entityName", "Order"))).expect(201);
        post(rules, rule("ERROR_HANDLING", "Retry", map("targetRule", "Nope", "deadLetterDestination", "dlq"))).expect(201);

        List<String> warnings = texts(get(rules).json().get("warnings"), "message");
        assertThat(warnings).containsExactly(
                "'total' is not a field of PlaceOrder",
                "a MIN check does not fit note, which is STRING",
                "'customer' is not a field of PlaceOrder",
                "customerId is not an array, so there is nothing to split",
                "the entity name Order is also a payload name; pick another, e.g. OrderEntity",
                "there is no rule called 'Nope' on this endpoint",
                "a dead-letter destination only applies when a message is being processed; REST callers get the fallback instead");
    }

    @Test
    void rejectsInvalidRules() {
        Response r = post(rules, map("type", "ENRICH", "name", " ", "config", map("serviceName", "crm", "url", "/c", "retries", 3))).expect(400);
        assertThat(r.errorFields()).containsExactlyInAnyOrder("name", "config.retries");
        assertThat(post(rules, map("name", "x")).expect(400).errorFields()).containsExactly("type");
        assertThat(post(rules, rule("TRANSFORM", "x", map("targetPayloadId", java.util.UUID.randomUUID().toString()))).expect(400)
                .errorsFor("config")).singleElement().asString().contains("not in this project");
        assertThat(post(rules, rule("PERSIST", "x", map("entityName", "String"))).expect(400).errorFields()).containsExactly("config.entityName");
        assertThat(post(rules, rule("PERSIST", "x", "not an object")).expect(400).errorsFor("config")).containsExactly("must be a JSON object");

        post(rules, rule("PERSIST", "Save", map("entityName", "OrderEntity"))).expect(201);
        assertThat(post(rules, rule("PERSIST", "SAVE", map("entityName", "OrderEntity"))).expect(400).errorsFor("name"))
                .singleElement().asString().contains("already called 'Save'");
    }

    @Test
    void insertsReordersUpdatesAndDeletes() {
        String a = post(rules, rule("PERSIST", "A", map("entityName", "OrderEntity"))).expect(201).id();
        String c = post(rules, rule("PERSIST", "C", map("entityName", "OrderEntity"))).expect(201).id();
        Response b = post(rules, map("type", "PERSIST", "name", "B", "position", 1, "config", map("entityName", "OrderEntity"))).expect(201);
        assertThat(b.json().get("position").asInt()).isEqualTo(1);
        assertThat(texts(get(rules).json().get("rules"), "name")).containsExactly("A", "B", "C");

        Response reordered = put(rules + "/order", map("ruleIds", List.of(c, a, b.id()))).expect(200);
        assertThat(texts(reordered.json().get("rules"), "name")).containsExactly("C", "A", "B");
        assertThat(put(rules + "/order", map("ruleIds", List.of(c, a))).expect(400).errorFields()).containsExactly("ruleIds");

        Response changed = put(rules + "/" + a, rule("PUBLISH_EVENT", "A", map("destination", "t"))).expect(200);
        assertThat(changed.json().get("type").asString()).isEqualTo("PUBLISH_EVENT");
        assertThat(changed.json().get("typeLabel").asString()).isEqualTo("Publish event");

        delete(rules + "/" + c).expect(204);
        Response chain = get(rules + "").expect(200);
        assertThat(texts(chain.json().get("rules"), "name")).containsExactly("A", "B");
        assertThat(chain.json().at("/rules/1/position").asInt()).isEqualTo(1);
        get(rules + "/" + c).expect(404);
    }

    @Test
    void rejectsExpressionSyntaxErrorsWithThePosition() {
        Response r = post(rules, map("type", "CONDITIONAL_ROUTE", "name", "Route", "condition", "total = 5",
                "config", map("branches", List.of(map("name", "big", "condition", "items[].price > 5")))) ).expect(400);
        assertThat(r.errorsFor("condition")).containsExactly("at character 7: write '==' to compare");
        assertThat(r.errorsFor("config.branches[0].condition")).singleElement().asString()
                .contains("'items[].price' is a list of values: wrap it in sum()");

        Response mapping = post(rules, rule("TRANSFORM", "Map", map("targetPayloadId", order,
                "mappings", List.of(map("target", "id", "expression", "uuid()"))))).expect(400);
        assertThat(mapping.errorsFor("config.mappings[0].expression")).containsExactly("at character 1: unknown function 'uuid'");

        Response custom = post(rules, rule("VALIDATE", "Check", map("checks", List.of(
                map("field", "note", "kind", "CUSTOM", "value", "size(note, 2) > 1"))))).expect(400);
        assertThat(custom.errorsFor("config.checks[0].value")).singleElement().asString().contains("size() takes 1 argument, not 2");
    }

    @Test
    void warnsWhenExpressionsDoNotFitThePayload() {
        post(rules, map("type", "PERSIST", "name", "Save", "condition", "note > 5", "config", map("entityName", "OrderEntity"))).expect(201);
        post(rules, rule("CONDITIONAL_ROUTE", "Route", map("branches", List.of(map("name", "rush", "condition", "priority == 'HIGH'"))))).expect(201);
        post(rules, rule("TRANSFORM", "Map", map("targetPayloadId", order, "mappings", List.of(
                map("target", "id", "expression", "now()"),
                map("target", "customerId", "expression", "customerId"))))).expect(201);
        post(rules, rule("ENRICH", "Customer", map("serviceName", "crm", "url", "/customers/{customerId}/{region}"))).expect(201);

        assertThat(texts(get(rules).json().get("warnings"), "message")).containsExactly(
                "condition: '>' needs two numbers, two texts or two dates, not text and a number",
                "branch rush: 'priority' is not a field of PlaceOrder",
                "mapping for id: this gives a date-time, but a UUID is needed",
                "url placeholder {region}: 'region' is not a field of Order");
    }
}
