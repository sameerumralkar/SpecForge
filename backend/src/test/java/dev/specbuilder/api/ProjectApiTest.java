package dev.specbuilder.api;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectApiTest extends ApiTestSupport {

    @Test
    void createsReadsUpdatesAndListsAProject() {
        String name = "Billing " + UUID.randomUUID();
        Response created = post("/api/projects", map("name", name, "basePackage", "com.acme.billing", "version", "2.1.0-SNAPSHOT"))
                .expect(201);
        String id = created.id();
        track(id);
        assertThat(created.json().get("defaultImplementationStyle").asString()).isEqualTo("IMPLEMENTATION_CLASS");
        assertThat(created.json().get("payloadCount").asLong()).isZero();

        Response updated = put("/api/projects/" + id, map("name", name, "basePackage", "com.acme.billing", "version", "2.1.0",
                "description", "Invoices", "defaultImplementationStyle", "PROCESSING_ROUTE")).expect(200);
        assertThat(updated.json().get("defaultImplementationStyle").asString()).isEqualTo("PROCESSING_ROUTE");
        assertThat(get("/api/projects/" + id).expect(200).json().get("description").asString()).isEqualTo("Invoices");
        assertThat(texts(get("/api/projects").expect(200).json(), "id")).contains(id);
    }

    @Test
    void reportsEveryInvalidValueAtOnce() {
        Response r = post("/api/projects", map("name", " ", "basePackage", "Com.Acme", "version", "one point oh")).expect(400);
        assertThat(r.errorFields()).containsExactlyInAnyOrder("name", "basePackage", "version");
        assertThat(r.json().get("title").asString()).isEqualTo("Validation failed");
    }

    @Test
    void projectNamesAreUniqueIgnoringCase() {
        String id = newProject();
        String name = get("/api/projects/" + id).json().get("name").asString();
        Response r = post("/api/projects", project(name.toUpperCase(), false)).expect(400);
        assertThat(r.errorsFor("name")).singleElement().asString().contains("already called");
    }

    @Test
    void unknownProjectIs404() {
        assertThat(get("/api/projects/" + UUID.randomUUID()).expect(404).json().get("title").asString()).isEqualTo("Not found");
    }

    @Test
    void deletingAProjectRemovesEverythingInsideIt() {
        String p = newProject();
        String a = payload(p, "Address", field("city", "STRING"));
        String c = payload(p, "Customer", ref("home", a), ref("work", a));
        String e = post("/api/projects/" + p + "/endpoints", rest("createCustomer", "POST", "/customers", c, c)).expect(201).id();
        post("/api/projects/" + p + "/endpoints/" + e + "/rules",
                rule("TRANSFORM", "Copy", map("targetPayloadId", a))).expect(201);

        delete("/api/projects/" + p).expect(204);
        forget(p);
        get("/api/projects/" + p).expect(404);
        get("/api/projects/" + p + "/payloads/" + a).expect(404);
    }

    @Test
    void cannotTurnOffCircularReferencesWhileACycleExists() {
        String p = newProject(true);
        String node = payload(p, "TreeNode", field("label", "STRING"));
        put("/api/projects/" + p + "/payloads/" + node, payloadBody("TreeNode", field("label", "STRING"),
                listOf("children", node))).expect(200);

        Map<String, Object> body = project(get("/api/projects/" + p).json().get("name").asString(), false);
        Response r = put("/api/projects/" + p, body).expect(400);
        assertThat(r.errorsFor("allowCircularReferences")).singleElement().asString().contains("TreeNode → TreeNode");
    }

    @Test
    void metaListsEveryEnum() {
        Response r = get("/api/meta").expect(200);
        assertThat(texts(r.json().get("ruleTypes"), "type")).hasSize(8).contains("CONDITIONAL_ROUTE", "ERROR_HANDLING");
        assertThat(texts(r.json().get("fieldTypes"), null)).contains("DATE_TIME", "ARRAY");
        assertThat(r.json().get("implementationStyles").size()).isEqualTo(2);
        assertThat(List.of("protocols", "securitySchemes", "errorCategories")).allMatch(k -> r.json().has(k));
    }
}
