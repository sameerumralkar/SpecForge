package dev.specbuilder.api;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The error-code inventory: codes, links to fields, rules and checks, and what export, specs and code do with them. */
class ErrorCodeApiTest extends ApiTestSupport {

    @Test
    void createsValidatesUpdatesAndDeletesCodes() {
        String p = newProject();
        String base = "/api/projects/" + p + "/error-codes";
        JsonNode created = post(base, map("code", "PAY-001", "message", "Amount must be positive", "category", "VALIDATION"))
                .expect(201).json();
        assertThat(created.get("status").asInt()).isEqualTo(400);
        assertThat(created.get("httpStatus").isNull()).isTrue();
        assertThat(created.get("links").size()).isZero();

        Response bad = post(base, map("code", "pay 1", "message", "", "httpStatus", 302)).expect(400);
        assertThat(bad.errorFields()).containsExactlyInAnyOrder("code", "message", "httpStatus", "category");
        assertThat(post(base, map("code", "PAY-001", "message", "Dup", "category", "BUSINESS")).expect(400).errorsFor("code"))
                .anyMatch(m -> m.contains("already has PAY-001"));

        JsonNode updated = put(base + "/" + created.get("id").asString(),
                map("code", "PAY-002", "message", "Too much", "category", "BUSINESS", "httpStatus", 409)).expect(200).json();
        assertThat(updated.get("status").asInt()).isEqualTo(409);
        assertThat(texts(get(base).json(), "code")).containsExactly("PAY-002");

        JsonNode meta = get("/api/meta").json().get("errorCodeCategories");
        assertThat(meta.toString()).contains("\"category\":\"BUSINESS\",\"defaultStatus\":422");

        delete(base + "/" + created.get("id").asString()).expect(204);
        assertThat(get(base).json().size()).isZero();
    }

    @Test
    void linksCodesToFieldsAndRulesFromEitherSide() {
        String p = newProject();
        String api = "/api/projects/" + p;
        String code = post(api + "/error-codes", map("code", "PAY-001", "message", "Amount must be positive", "category", "VALIDATION")).id();
        String other = post(api + "/error-codes", map("code", "PAY-002", "message", "Payments service down", "category", "DOWNSTREAM")).id();
        String money = payload(p, "Money", field("value", "DECIMAL"), field("currency", "STRING"));
        String endpoint = post(api + "/endpoints", rest("pay", "POST", "/pay", money, money)).expect(201).id();
        String rules = api + "/endpoints/" + endpoint + "/rules";
        String validate = post(rules, rule("VALIDATE", "Check amount", map("checks", List.of(map("field", "value", "kind", "MIN", "value", "0.01")))))
                .expect(201).id();

        // From the code's side.
        JsonNode linked = put(api + "/error-codes/" + code + "/links", map("links", List.of(
                map("kind", "FIELD", "payloadId", money, "field", "value"),
                map("kind", "RULE", "ruleId", validate)))).expect(200).json();
        assertThat(texts(linked.get("links"), "detail")).containsExactly("Money.value", "pay › Check amount");
        assertThat(get(api + "/payloads/" + money).json().at("/fields/0/errorCodeIds/0").asString()).isEqualTo(code);
        assertThat(get(rules + "/" + validate).json().at("/errorCodeIds/0").asString()).isEqualTo(code);

        Response bad = put(api + "/error-codes/" + code + "/links", map("links", List.of(
                map("kind", "FIELD", "payloadId", money, "field", "nope"), map("kind", "RULE", "ruleId", money)))).expect(400);
        assertThat(bad.errorFields()).containsExactly("links[0].field", "links[1].ruleId");

        // From the field's and the rule's side. A payload update without errorCodeIds keeps the field's codes.
        JsonNode payload = get(api + "/payloads/" + money).json();
        ((ObjectNode) payload.get("fields").get(0)).remove("errorCodeIds");
        ((ObjectNode) payload.get("fields").get(1)).set("errorCodeIds", JSON.valueToTree(List.of(other)));
        put(api + "/payloads/" + money, payload).expect(200);
        assertThat(texts(get(api + "/error-codes/" + other).json().get("links"), "detail")).containsExactly("Money.currency");
        assertThat(texts(get(api + "/error-codes/" + code).json().get("links"), "detail")).containsExactly("Money.value", "pay › Check amount");
        ((ObjectNode) payload.get("fields").get(1)).set("errorCodeIds", JSON.valueToTree(List.of("00000000-0000-0000-0000-000000000000")));
        assertThat(put(api + "/payloads/" + money, payload).expect(400).errorFields()).containsExactly("fields[1].errorCodeIds");

        // A check that picks its own code shows up as a CHECK link; deleting the code clears every link.
        JsonNode chainRule = get(rules + "/" + validate).json();
        Map<String, Object> update = map("type", "VALIDATE", "name", "Check amount", "errorCodeIds", List.of(code), "config",
                map("checks", List.of(map("field", "value", "kind", "MIN", "value", "0.01", "errorCodeId", other))));
        put(rules + "/" + validate, update).expect(200);
        assertThat(texts(get(api + "/error-codes/" + other).json().get("links"), "kind")).containsExactly("FIELD", "CHECK");
        assertThat(chainRule.get("errorCodeIds").size()).isEqualTo(1);

        delete(api + "/error-codes/" + other).expect(204);
        assertThat(get(api + "/payloads/" + money).json().at("/fields/1/errorCodeIds").size()).isZero();
        assertThat(get(rules + "/" + validate).json().at("/config/checks/0").path("errorCodeId").isString()).isFalse();

        // Unlinking from the code's side.
        put(api + "/error-codes/" + code + "/links", map("links", List.of())).expect(200);
        assertThat(get(rules + "/" + validate).json().get("errorCodeIds").size()).isZero();
        assertThat(get(api + "/payloads/" + money).json().at("/fields/0/errorCodeIds").size()).isZero();
    }

    @Test
    void theSampleCarriesCodesIntoExportsSpecsAndCode() {
        String p = post("/api/projects/samples/order-service", null).expect(201).id();
        track(p);
        String api = "/api/projects/" + p;
        JsonNode codes = get(api + "/error-codes").json();
        assertThat(texts(codes, "code")).containsExactly("ORD-001", "ORD-002", "ORD-003", "ORD-004", "ORD-010", "ORD-011", "ORD-020", "ORD-021");
        assertThat(texts(codes.get(4).get("links"), "detail")).containsExactly("createOrder › Enrich customer", "createOrder › Customer service resilience");

        // Export and import keep codes and links, by code.
        JsonNode export = get(api + "/export").json();
        assertThat(export.get("errorCodes").size()).isEqualTo(8);
        String copy = post("/api/projects/import?name=Codes copy", export).expect(201).json().at("/project/id").asString();
        track(copy);
        JsonNode copied = get("/api/projects/" + copy + "/error-codes").json();
        assertThat(texts(copied, "code")).isEqualTo(texts(codes, "code"));
        assertThat(texts(copied.get(4).get("links"), "detail")).isEqualTo(texts(codes.get(4).get("links"), "detail"));
        ((ObjectNode) export.get("payloads").get(0).get("fields").get(0)).set("errorCodes", JSON.valueToTree(List.of("NOPE-1")));
        Response bad = post("/api/projects/import?name=Bad", export).expect(400);
        assertThat(bad.errorFields()).containsExactly("payloads[0].fields[0].errorCodes[0]");

        // OpenAPI lists the catalog, the codes on properties and the codes each operation can answer with.
        String yaml = get(api + "/specs").json().at("/openapi/yaml").asString();
        assertThat(yaml).contains("x-error-codes:\n  - code: ORD-001\n    status: 400\n    category: VALIDATION");
        JsonNode openapi = JSON.readTree(get(api + "/specs").json().at("/openapi/json").asString());
        assertThat(texts(openapi.at("/paths/~1orders/post/x-error-codes"), "code"))
                .containsExactly("ORD-010", "ORD-020", "ORD-021", "ORD-001", "ORD-002", "ORD-004", "ORD-003");
        assertThat(openapi.at("/components/schemas/OrderItem/properties/quantity/x-error-codes/0").asString()).isEqualTo("ORD-003");

        // Generated code: the catalog, codes on the checks and on the fallback, and the "code" property in problems.
        Map<String, String> files = new java.util.HashMap<>();
        get(api + "/code").json().get("files").forEach(f -> files.put(f.get("path").asString().replaceAll(".*/", ""), f.get("content").asString()));
        assertThat(files.get("ErrorCatalog.java")).contains("ORD_010(\"ORD-010\", 503, \"Customer service unavailable\")");
        assertThat(files.get("OrdersServiceImpl.java"))
                .contains("throw new RuleViolationException(400, \"ORD-001\", \"customerId is required\");")
                .contains("throw new RuleViolationException(400, \"ORD-003\", \"Each item's quantity must be from 1 to 1000\");")
                .contains("throw new RuleViolationException(503, \"ORD-010\", \"Customer service unavailable\");");
        assertThat(files.get("ApiExceptionHandler.java")).contains("problem.setProperty(\"code\", e.getCode());");
    }

    @Test
    void specsWithErrorCodesImportThemBack() {
        String p = post("/api/projects/samples/order-service", null).expect(201).id();
        track(p);
        JsonNode specs = get("/api/projects/" + p + "/specs").json();
        Response imported = post("/api/projects/import/spec", map("documents",
                List.of(specs.at("/openapi/yaml").asString(), specs.at("/asyncapi/yaml").asString()))).expect(201);
        String copy = imported.json().at("/project/id").asString();
        track(copy);
        JsonNode codes = get("/api/projects/" + copy + "/error-codes").json();
        assertThat(codes.size()).isEqualTo(8);
        List<String> fieldLinks = new ArrayList<>();
        codes.forEach(c -> c.get("links").forEach(l -> fieldLinks.add(c.get("code").asString() + " " + l.get("detail").asString())));
        assertThat(fieldLinks).containsExactly("ORD-001 CreateOrderRequest.customerId", "ORD-002 CreateOrderRequest.items",
                "ORD-003 OrderItem.quantity", "ORD-004 OrderItem.sku");
    }
}
