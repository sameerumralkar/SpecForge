package dev.specbuilder.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PayloadApiTest extends ApiTestSupport {

    @Test
    void createsAPayloadWithConstraintsAndReadsItBack() {
        String p = newProject();
        String address = payload(p, "Address", field("city", "STRING", "required", true));
        Response r = post("/api/projects/" + p + "/payloads", map("name", "Customer", "description", "A customer",
                "example", "{\"email\":\"a@b.c\"}", "fields", List.of(
                        field("email", "STRING", "required", true, "format", "email", "maxLength", 200, "pattern", ".+@.+"),
                        field("tier", "STRING", "enumValues", List.of("GOLD", "SILVER")),
                        field("age", "INTEGER", "minimum", 0, "maximum", 150),
                        ref("address", address),
                        field("nicknames", "ARRAY", "itemType", "STRING", "minLength", 0)))).expect(201);

        Response read = get("/api/projects/" + p + "/payloads/" + r.id()).expect(200);
        assertThat(texts(read.json().get("fields"), "name")).containsExactly("email", "tier", "age", "address", "nicknames");
        assertThat(read.json().at("/fields/3/refPayloadName").asString()).isEqualTo("Address");
        assertThat(read.json().at("/fields/1/enumValues/1").asString()).isEqualTo("SILVER");
        assertThat(read.json().at("/fields/0/required").asBoolean()).isTrue();
        assertThat(texts(get("/api/projects/" + p + "/payloads").json(), "name")).containsExactly("Address", "Customer");
    }

    @Test
    void validatesNamesTypesAndConstraintsPerField() {
        String p = newProject();
        Response r = post("/api/projects/" + p + "/payloads", map("name", "order", "example", "{not json", "fields", List.of(
                field("class", "STRING"),
                field("customer", "OBJECT"),
                field("lines", "ARRAY"),
                field("count", "INTEGER", "maxLength", 3),
                field("code", "STRING", "pattern", "[a-"),
                field("Code", "STRING"),
                field("price", "DECIMAL", "minimum", 10, "maximum", 1),
                field("matrix", "ARRAY", "itemType", "ARRAY")))).expect(400);
        assertThat(r.errorFields()).containsExactlyInAnyOrder(
                "name", "example",
                "fields[0].name", "fields[1].refPayloadId", "fields[2].itemType", "fields[3].minLength",
                "fields[4].pattern", "fields[5].name", "fields[6].maximum", "fields[7].itemType");
    }

    @Test
    void payloadNamesAreUniqueIgnoringCaseAndCannotShadowJavaClasses() {
        String p = newProject();
        payload(p, "Order");
        assertThat(post("/api/projects/" + p + "/payloads", payloadBody("ORDER")).expect(400).errorsFor("name"))
                .singleElement().asString().contains("already called 'Order'");
        assertThat(post("/api/projects/" + p + "/payloads", payloadBody("String")).expect(400).errorsFor("name"))
                .singleElement().asString().contains("clashes");
    }

    @Test
    void renamingAPayloadShowsTheNewNameEverywhere() {
        String p = newProject();
        String item = payload(p, "Item", field("sku", "STRING"));
        String order = payload(p, "Order", listOf("items", item));
        String endpoint = post("/api/projects/" + p + "/endpoints", rest("createItem", "POST", "/items", item, item)).expect(201).id();
        post("/api/projects/" + p + "/endpoints/" + endpoint + "/rules", rule("TRANSFORM", "To order", map("targetPayloadId", order))).expect(201);

        put("/api/projects/" + p + "/payloads/" + item, payloadBody("LineItem", field("sku", "STRING"))).expect(200);

        assertThat(get("/api/projects/" + p + "/payloads/" + order).json().at("/fields/0/refPayloadName").asString()).isEqualTo("LineItem");
        Response e = get("/api/projects/" + p + "/endpoints/" + endpoint);
        assertThat(e.json().get("requestPayloadName").asString()).isEqualTo("LineItem");
        assertThat(e.json().at("/responses/0/payloadName").asString()).isEqualTo("LineItem");
        assertThat(get("/api/projects/" + p + "/endpoints/" + endpoint + "/rules").json().at("/summary/0").asString())
                .isEqualTo("1. Map the LineItem to Order (matching fields by name).");
    }

    @Test
    void deletingAPayloadInUseIsBlockedWithTheListOfUses() {
        String p = newProject();
        String money = payload(p, "Money", field("amount", "DECIMAL"));
        String order = payload(p, "Order", ref("total", money));
        String e1 = post("/api/projects/" + p + "/endpoints", rest("pay", "POST", "/pay", money, order)).expect(201).id();
        post("/api/projects/" + p + "/endpoints", async("moneyMoved", "money.moved", "SEND", money)).expect(201);
        post("/api/projects/" + p + "/endpoints/" + e1 + "/rules",
                rule("PUBLISH_EVENT", "Announce", map("destination", "x", "eventPayloadId", money))).expect(201);

        assertThat(texts(get("/api/projects/" + p + "/payloads/" + money + "/usages").expect(200).json(), "kind"))
                .containsExactly("PAYLOAD_FIELD", "REQUEST_BODY", "MESSAGE", "RULE");

        Response r = delete("/api/projects/" + p + "/payloads/" + money).expect(409);
        assertThat(r.json().get("title").asString()).isEqualTo("Payload in use");
        assertThat(r.json().get("usages").size()).isEqualTo(4);
        assertThat(texts(r.json().get("usages"), "detail")).contains("field total", "rule 'Announce' (Publish event)");

        Response orderUse = delete("/api/projects/" + p + "/payloads/" + order).expect(409);
        assertThat(texts(orderUse.json().get("usages"), "detail")).containsExactly("response 200");
    }

    @Test
    void deletingAnUnusedPayloadWorks() {
        String p = newProject();
        String self = payload(p, "Lonely", field("x", "STRING"));
        delete("/api/projects/" + p + "/payloads/" + self).expect(204);
        get("/api/projects/" + p + "/payloads/" + self).expect(404);
    }

    @Test
    void circularReferencesAreRejectedUnlessTheProjectAllowsThem() {
        String p = newProject();
        String a = payload(p, "Alpha", field("x", "STRING"));
        String b = payload(p, "Beta", ref("alpha", a));
        Response r = put("/api/projects/" + p + "/payloads/" + a, payloadBody("Alpha", ref("beta", b))).expect(400);
        assertThat(r.errorsFor("fields")).singleElement().asString().contains("Alpha → Beta → Alpha");
        assertThat(put("/api/projects/" + p + "/payloads/" + a, payloadBody("Alpha", ref("self", a))).expect(400)
                .errorsFor("fields")).singleElement().asString().contains("Alpha → Alpha");

        String open = newProject(true);
        String node = payload(open, "Node", field("x", "STRING"));
        put("/api/projects/" + open + "/payloads/" + node, payloadBody("Node", listOf("children", node))).expect(200);
    }

    @Test
    void duplicatingCopiesFieldsAndPicksAFreeName() {
        String p = newProject(true);
        String node = payload(p, "Node", field("label", "STRING", "required", true));
        put("/api/projects/" + p + "/payloads/" + node, payloadBody("Node", field("label", "STRING"), ref("parent", node))).expect(200);

        Response copy = post("/api/projects/" + p + "/payloads/" + node + "/duplicate", null).expect(201);
        assertThat(copy.json().get("name").asString()).isEqualTo("NodeCopy");
        assertThat(copy.json().at("/fields/1/refPayloadId").asString()).as("self-reference points at the copy").isEqualTo(copy.id());
        assertThat(post("/api/projects/" + p + "/payloads/" + node + "/duplicate", null).expect(201).json().get("name").asString())
                .isEqualTo("NodeCopy2");
    }

    @Test
    void payloadsOfOtherProjectsAreInvisible() {
        String p1 = newProject();
        String p2 = newProject();
        String foreign = payload(p2, "Foreign", field("x", "STRING"));
        get("/api/projects/" + p1 + "/payloads/" + foreign).expect(404);
        assertThat(post("/api/projects/" + p1 + "/payloads", payloadBody("Local", ref("f", foreign))).expect(400)
                .errorFields()).containsExactly("fields[0].refPayloadId");
    }
}
