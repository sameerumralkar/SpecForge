package dev.specbuilder.api;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EndpointApiTest extends ApiTestSupport {

    @Test
    void createsARestEndpointAndFallsBackToTheProjectStyle() {
        String p = newProject();
        String order = payload(p, "Order", field("id", "UUID"));
        Map<String, Object> body = rest("getOrder", "GET", "/orders/{orderId}", null, order, param("orderId", "PATH", "UUID"),
                param("X-Trace", "HEADER", "STRING"), param("expand", "QUERY", "BOOLEAN"));
        body.put("tags", List.of("Orders", "orders", " "));
        body.put("security", List.of("BEARER_JWT", "BEARER_JWT"));
        Response r = post("/api/projects/" + p + "/endpoints", body).expect(201);

        assertThat(r.json().get("effectiveImplementationStyle").asString()).isEqualTo("IMPLEMENTATION_CLASS");
        assertThat(r.json().get("implementationStyle").isNull()).isTrue();
        assertThat(texts(r.json().get("tags"), null)).containsExactly("Orders");
        assertThat(texts(r.json().get("security"), null)).containsExactly("BEARER_JWT");
        assertThat(r.json().at("/parameters/0/required").asBoolean()).as("path parameters are always required").isTrue();
        assertThat(r.json().at("/responses/0/payloadName").asString()).isEqualTo("Order");

        body.put("implementationStyle", "PROCESSING_ROUTE");
        assertThat(put("/api/projects/" + p + "/endpoints/" + r.id(), body).expect(200).json()
                .get("effectiveImplementationStyle").asString()).isEqualTo("PROCESSING_ROUTE");
    }

    @Test
    void methodAndPathTogetherMustBeUniqueWhateverTheParameterIsCalled() {
        String p = newProject();
        String order = payload(p, "Order", field("id", "UUID"));
        post("/api/projects/" + p + "/endpoints", rest("getOrder", "GET", "/orders/{id}", null, order, param("id", "PATH", "UUID"))).expect(201);

        Response clash = post("/api/projects/" + p + "/endpoints",
                rest("fetchOrder", "GET", "/orders/{orderId}", null, order, param("orderId", "PATH", "UUID"))).expect(400);
        assertThat(clash.errorsFor("path")).singleElement().asString().contains("already defined by endpoint 'getOrder'");

        post("/api/projects/" + p + "/endpoints",
                rest("deleteOrder", "DELETE", "/orders/{orderId}", null, order, param("orderId", "PATH", "UUID"))).expect(201);
        assertThat(post("/api/projects/" + p + "/endpoints", rest("GetOrder", "PUT", "/x", null, order)).expect(400).errorFields())
                .containsExactly("name");
        assertThat(post("/api/projects/" + p + "/endpoints", rest("getorder", "PUT", "/x", null, order)).expect(400)
                .errorsFor("name")).singleElement().asString().contains("already called 'getOrder'");
    }

    @Test
    void pathParametersMustMatchThePath() {
        String p = newProject();
        String order = payload(p, "Order", field("id", "UUID"));
        Response r = post("/api/projects/" + p + "/endpoints", rest("getItem", "GET", "/orders/{orderId}/items/{itemId}", null, order,
                param("orderId", "PATH", "UUID"), param("sku", "PATH", "STRING"))).expect(400);
        assertThat(r.errorsFor("parameters")).containsExactly("the path uses {itemId}: add a PATH parameter called itemId");
        assertThat(r.errorsFor("parameters[1].name")).containsExactly("the path has no {sku}");
    }

    @Test
    void validatesRestSpecificRules() {
        String p = newProject();
        String order = payload(p, "Order", field("id", "UUID"));
        Map<String, Object> body = rest("listOrders", "GET", "orders/", order, order, param("bad name", "HEADER", "STRING"),
                param("filter", "QUERY", "OBJECT"));
        body.put("responses", List.of(map("statusCode", "200"), map("statusCode", "200"), map("statusCode", "600")));
        Response r = post("/api/projects/" + p + "/endpoints", body).expect(400);
        assertThat(r.errorFields()).containsExactlyInAnyOrder("path", "path", "requestPayloadId", "parameters[0].name",
                "parameters[1].type", "responses[1].statusCode", "responses[2].statusCode");

        Map<String, Object> noResponses = rest("ping", "GET", "/ping", null, null);
        noResponses.put("responses", List.of());
        assertThat(post("/api/projects/" + p + "/endpoints", noResponses).expect(400).errorFields()).containsExactly("responses");
    }

    @Test
    void createsAnAsyncEndpointAndChecksItsFields() {
        String p = newProject();
        String event = payload(p, "OrderPlaced", field("orderId", "UUID"));
        Map<String, Object> body = async("onOrderPlaced", "orders.placed", "RECEIVE", event);
        body.put("parameters", List.of(param("correlation-id", "HEADER", "STRING")));
        Response r = post("/api/projects/" + p + "/endpoints", body).expect(201);
        assertThat(r.json().get("messagePayloadName").asString()).isEqualTo("OrderPlaced");
        assertThat(r.json().get("httpMethod").isNull()).isTrue();

        Map<String, Object> bad = map("kind", "ASYNC", "name", "broken", "channel", "bad channel!",
                "parameters", List.of(param("page", "QUERY", "INTEGER")));
        assertThat(post("/api/projects/" + p + "/endpoints", bad).expect(400).errorFields())
                .containsExactlyInAnyOrder("channel", "operation", "protocol", "messagePayloadId", "parameters[0].location");
    }

    @Test
    void switchingKindClearsTheOtherKindsFields() {
        String p = newProject();
        String order = payload(p, "Order", field("id", "UUID"));
        String id = post("/api/projects/" + p + "/endpoints", rest("createOrder", "POST", "/orders", order, order)).expect(201).id();
        Map<String, Object> asAsync = async("createOrder", "orders.create", "RECEIVE", order);
        asAsync.put("httpMethod", "POST");
        asAsync.put("path", "/orders");
        Response r = put("/api/projects/" + p + "/endpoints/" + id, asAsync).expect(200);
        assertThat(r.json().get("path").isNull()).isTrue();
        assertThat(r.json().get("requestPayloadId").isNull()).isTrue();
        assertThat(r.json().get("responses").size()).isZero();
        // The route is free again.
        post("/api/projects/" + p + "/endpoints", rest("createOrder2", "POST", "/orders", order, order)).expect(201);
    }

    @Test
    void duplicatingCopiesRulesAndKeepsTheRouteUnique() {
        String p = newProject();
        String order = payload(p, "Order", field("id", "UUID"));
        String id = post("/api/projects/" + p + "/endpoints", rest("createOrder", "POST", "/orders", order, order)).expect(201).id();
        post("/api/projects/" + p + "/endpoints/" + id + "/rules", rule("PERSIST", "Save", map("entityName", "OrderEntity"))).expect(201);

        Response copy = post("/api/projects/" + p + "/endpoints/" + id + "/duplicate", null).expect(201);
        assertThat(copy.json().get("name").asString()).isEqualTo("createOrderCopy");
        assertThat(copy.json().get("path").asString()).isEqualTo("/orders/copy");
        assertThat(copy.json().get("ruleCount").asInt()).isEqualTo(1);
        Response second = post("/api/projects/" + p + "/endpoints/" + id + "/duplicate", null).expect(201);
        assertThat(second.json().get("path").asString()).isEqualTo("/orders/copy2");
        assertThat(get("/api/projects/" + p + "/endpoints").json().size()).isEqualTo(3);
    }

    @Test
    void deletesAnEndpointWithItsRules() {
        String p = newProject();
        String order = payload(p, "Order", field("id", "UUID"));
        String id = post("/api/projects/" + p + "/endpoints", rest("createOrder", "POST", "/orders", order, order)).expect(201).id();
        post("/api/projects/" + p + "/endpoints/" + id + "/rules", rule("PERSIST", "Save", map("entityName", "OrderEntity"))).expect(201);
        delete("/api/projects/" + p + "/endpoints/" + id).expect(204);
        get("/api/projects/" + p + "/endpoints/" + id).expect(404);
        delete("/api/projects/" + p + "/payloads/" + order).expect(204);
    }
}
