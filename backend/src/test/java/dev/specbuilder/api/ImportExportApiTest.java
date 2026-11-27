package dev.specbuilder.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class ImportExportApiTest extends ApiTestSupport {

    private String sample() {
        String id = post("/api/projects/samples/order-service", null).expect(201).id();
        track(id);
        return id;
    }

    private String importedId(Response r) {
        String id = r.json().at("/project/id").asString();
        track(id);
        return id;
    }

    // ------------------------------------------------------------------ project JSON

    @Test
    void exportedProjectsImportBackIdentically() {
        String p = sample();
        Response export = get("/api/projects/" + p + "/export").expect(200);
        JsonNode file = export.json();
        assertThat(file.get("format").asString()).isEqualTo("spec-builder/project");
        assertThat(file.at("/project/name").asString()).isEqualTo("Order Service");
        assertThat(file.toString()).doesNotContain("PayloadId").doesNotContain("\"id\":")
                .contains("\"targetPayload\":\"Order\"").contains("\"responsePayload\":\"Customer\"");

        Response imported = call(HttpMethod.POST, "/api/projects/import?name=Orders copy", file).expect(201);
        String copy = importedId(imported);
        assertThat(imported.json().at("/project/name").asString()).isEqualTo("Orders copy");
        assertThat(imported.json().at("/project/endpointCount").asInt()).isEqualTo(5);

        JsonNode again = get("/api/projects/" + copy + "/export").expect(200).json();
        ((ObjectNode) again.get("project")).put("name", "Order Service");
        assertThat(again).isEqualTo(file);
        assertThat(get("/api/projects/" + copy + "/code").json().get("ready").asBoolean()).isTrue();
    }

    @Test
    void importsGetAFreeNameAndRejectBadFilesWithoutLeavingAnythingBehind() {
        String p = sample();
        JsonNode file = get("/api/projects/" + p + "/export").json();
        Response second = post("/api/projects/import", file).expect(201);
        importedId(second);
        assertThat(second.json().at("/project/name").asString()).isEqualTo("Order Service 2");
        JsonNode secondFile = get("/api/projects/" + second.json().at("/project/id").asString() + "/export").json();
        Response third = post("/api/projects/import", secondFile).expect(201);
        importedId(third);
        assertThat(third.json().at("/project/name").asString()).as("numbered names count up").isEqualTo("Order Service 3");

        int before = get("/api/projects").json().size();
        ObjectNode broken = (ObjectNode) file.deepCopy();
        ((ObjectNode) broken.at("/payloads/1/fields/0")).put("refPayload", "Nope");
        Response r = post("/api/projects/import", broken).expect(400);
        assertThat(r.errorFields()).containsExactly("payloads[1].fields[0].refPayload");

        ObjectNode badName = (ObjectNode) file.deepCopy();
        ((ObjectNode) badName.at("/endpoints/0")).put("name", "Not Valid");
        assertThat(post("/api/projects/import", badName).expect(400).errorFields()).containsExactly("endpoints[0].name");
        assertThat(get("/api/projects").json().size()).as("failed imports create nothing").isEqualTo(before);

        assertThat(post("/api/projects/import", map("format", "something-else")).expect(400).errorFields()).containsExactly("format");
    }

    // ------------------------------------------------------------------ OpenAPI / AsyncAPI

    @Test
    void theSampleSpecsImportBackToTheSamePayloadsAndEndpoints() {
        String p = sample();
        JsonNode specs = get("/api/projects/" + p + "/specs").json();
        Response imported = post("/api/projects/import/spec", map("documents",
                List.of(specs.at("/openapi/yaml").asString(), specs.at("/asyncapi/yaml").asString()))).expect(201);
        String copy = importedId(imported);
        assertThat(texts(imported.json().get("warnings"), null)).isEmpty();
        assertThat(imported.json().at("/project/name").asString()).isEqualTo("Order Service 2");
        assertThat(imported.json().at("/project/basePackage").asString()).isEqualTo("com.example.orderservice");

        assertThat(comparable(get("/api/projects/" + copy + "/export").json()))
                .isEqualTo(comparable(get("/api/projects/" + p + "/export").json()));
    }

    /** What a spec can carry: the export without rules, styles, project settings, the field inventory and capabilities. */
    private static JsonNode comparable(JsonNode export) {
        ObjectNode copy = (ObjectNode) export.deepCopy();
        copy.remove("project");
        copy.remove("fieldInventory");
        copy.remove("capabilities");
        copy.remove("capabilityLevels");
        for (JsonNode payload : copy.get("payloads")) {
            if (payload.hasNonNull("example")) {
                ((ObjectNode) payload).put("example", JSON.readTree(payload.get("example").asString()).toString());
            }
        }
        for (JsonNode e : copy.get("endpoints")) {
            ((ObjectNode) e).remove("rules");
            ((ObjectNode) e).remove("implementationStyle");
        }
        return copy;
    }

    @Test
    void importsAHandWrittenOpenApiFileWithWarningsForWhatItCannotHold() {
        String yaml = """
                openapi: 3.0.3
                info: {title: Pet Store!, version: "v2 beta"}
                security: [{bearer: []}]
                paths:
                  /pets/{pet-id}:
                    parameters:
                      - {name: pet-id, in: path, required: true, schema: {type: string, format: uuid}}
                    get:
                      tags: [Pets]
                      parameters:
                        - $ref: '#/components/parameters/Verbose'
                        - {name: session, in: cookie, schema: {type: string}}
                      responses:
                        '200':
                          description: The pet
                          content: {application/json: {schema: {$ref: '#/components/schemas/Pet'}}}
                        '4XX': {description: Client error}
                  /pets:
                    get:
                      operationId: list-pets
                      responses:
                        '200':
                          description: All pets
                          content: {application/json: {schema: {type: array, items: {$ref: '#/components/schemas/Pet'}}}}
                    post:
                      requestBody:
                        content:
                          application/json:
                            schema:
                              type: object
                              required: [name]
                              properties:
                                name: {type: string, minLength: 1, maxLength: 40}
                                tags: {type: array, items: {type: string, pattern: '^[a-z]+$'}}
                      responses:
                        '201': {description: Created, content: {application/json: {schema: {$ref: '#/components/schemas/Pet'}}}}
                components:
                  securitySchemes:
                    bearer: {type: http, scheme: bearer}
                  parameters:
                    Verbose: {name: verbose, in: query, schema: {type: boolean}}
                  schemas:
                    Status: {type: string, enum: [AVAILABLE, SOLD]}
                    Pet:
                      type: object
                      required: [id]
                      properties:
                        id: {type: string, format: uuid}
                        first-name: {type: [string, "null"]}
                        status: {$ref: '#/components/schemas/Status'}
                        weight: {type: number, minimum: 0}
                        owner: {type: object, properties: {email: {type: string, format: email}}}
                        attributes: {type: object, additionalProperties: {type: string}}
                        kind: {oneOf: [{$ref: '#/components/schemas/Dog'}, {$ref: '#/components/schemas/Cat'}]}
                        parent: {$ref: '#/components/schemas/Pet'}
                    Dog: {type: object, properties: {barks: {type: boolean}}}
                    Cat: {type: object, properties: {meows: {type: boolean}}}
                """;
        Response r = post("/api/projects/import/spec", map("documents", List.of(yaml))).expect(201);
        String p = importedId(r);
        List<String> warnings = texts(r.json().get("warnings"), null);
        assertThat(warnings).anyMatch(w -> w.contains("cookie parameter 'session' is not supported"))
                .anyMatch(w -> w.contains("response '4XX' is skipped"))
                .anyMatch(w -> w.contains("Pet.attributes is a free-form object or map"))
                .anyMatch(w -> w.contains("Pet.kind is oneOf/anyOf; imported as its first option"))
                .anyMatch(w -> w.contains("Pet.first-name is imported as firstName"))
                .anyMatch(w -> w.contains("is an array; it is wrapped"))
                .anyMatch(w -> w.contains("version 'v2 beta' is not a Maven version"))
                .anyMatch(w -> w.contains("wrapped in payload ListPetsResponse200"))
                .anyMatch(w -> w.contains("circular references are allowed"));
        assertThat(r.json().at("/project/allowCircularReferences").asBoolean()).isTrue();
        assertThat(r.json().at("/project/basePackage").asString()).isEqualTo("com.example.petstore");

        JsonNode file = get("/api/projects/" + p + "/export").json();
        Map<String, JsonNode> payloads = byName(file.get("payloads"));
        assertThat(payloads.keySet()).containsExactlyInAnyOrder("Cat", "Dog", "ListPetsResponse200", "Pet", "PetOwner", "PostPetsRequest");
        JsonNode pet = byName(payloads.get("Pet").get("fields")).get("status");
        assertThat(pet.get("type").asString()).isEqualTo("STRING");
        assertThat(texts(pet.get("enumValues"), null)).containsExactly("AVAILABLE", "SOLD");
        Map<String, JsonNode> petFields = byName(payloads.get("Pet").get("fields"));
        assertThat(petFields.get("owner").get("refPayload").asString()).isEqualTo("PetOwner");
        assertThat(petFields.get("kind").get("refPayload").asString()).isEqualTo("Dog");
        assertThat(petFields.get("parent").get("refPayload").asString()).isEqualTo("Pet");
        assertThat(petFields.get("weight").get("type").asString()).isEqualTo("DECIMAL");
        assertThat(petFields.get("id").get("required").asBoolean()).isTrue();
        JsonNode tags = byName(payloads.get("PostPetsRequest").get("fields")).get("tags");
        assertThat(tags.get("itemType").asString()).isEqualTo("STRING");
        assertThat(tags.get("pattern").asString()).isEqualTo("^[a-z]+$");

        Map<String, JsonNode> endpoints = byName(file.get("endpoints"));
        assertThat(endpoints.keySet()).containsExactlyInAnyOrder("getPetsPetId", "listPets", "postPets");
        JsonNode get = endpoints.get("getPetsPetId");
        assertThat(get.get("path").asString()).isEqualTo("/pets/{petId}");
        assertThat(texts(get.get("parameters"), "name")).containsExactly("petId", "verbose");
        assertThat(texts(get.get("security"), null)).containsExactly("BEARER_JWT");
        assertThat(texts(get.get("tags"), null)).containsExactly("Pets");
        assertThat(endpoints.get("listPets").at("/responses/0/payload").asString()).isEqualTo("ListPetsResponse200");
        assertThat(endpoints.get("postPets").get("requestPayload").asString()).isEqualTo("PostPetsRequest");
    }

    @Test
    void importsAsyncApi2Documents() {
        String yaml = """
                asyncapi: 2.6.0
                info: {title: Notifications, version: 1.2.0}
                servers:
                  broker: {url: 'amqp://localhost', protocol: amqp}
                channels:
                  user/signed-up:
                    publish:
                      operationId: onUserSignedUp
                      message: {$ref: '#/components/messages/UserSignedUp'}
                    subscribe:
                      message:
                        name: WelcomeSent
                        payload: {type: object, properties: {userId: {type: string}}}
                components:
                  messages:
                    UserSignedUp:
                      headers: {type: object, required: [trace-id], properties: {trace-id: {type: string}}}
                      payload: {$ref: '#/components/schemas/User'}
                  schemas:
                    User: {type: object, properties: {email: {type: string, format: email}}}
                """;
        Response r = post("/api/projects/import/spec", map("documents", List.of(yaml), "name", "Notify", "basePackage", "org.notify"))
                .expect(201);
        String p = importedId(r);
        assertThat(r.json().at("/project/name").asString()).isEqualTo("Notify");
        Map<String, JsonNode> endpoints = byName(get("/api/projects/" + p + "/export").json().get("endpoints"));
        JsonNode receive = endpoints.get("onUserSignedUp");
        assertThat(receive.get("operation").asString()).isEqualTo("RECEIVE");
        assertThat(receive.get("protocol").asString()).isEqualTo("AMQP");
        assertThat(receive.get("channel").asString()).isEqualTo("user/signed-up");
        assertThat(receive.get("messagePayload").asString()).isEqualTo("User");
        assertThat(receive.at("/parameters/0/name").asString()).isEqualTo("trace-id");
        assertThat(receive.at("/parameters/0/required").asBoolean()).isTrue();
        JsonNode send = endpoints.get("sendUserSignedUp");
        assertThat(send.get("operation").asString()).isEqualTo("SEND");
        assertThat(send.get("messagePayload").asString()).isEqualTo("WelcomeSent");
    }

    @Test
    void rejectsWhatItCannotRead() {
        assertThat(post("/api/projects/import/spec", map("documents", List.of("swagger: '2.0'\ninfo: {title: x}"))).expect(400)
                .errorsFor("documents[0]")).singleElement().asString().contains("Swagger 2.0 is not supported");
        assertThat(post("/api/projects/import/spec", map("documents", List.of("{ not: [valid"))).expect(400)
                .errorsFor("documents[0]")).singleElement().asString().contains("neither valid YAML nor JSON");
        assertThat(post("/api/projects/import/spec", map("documents", List.of("hello: world"))).expect(400)
                .errorsFor("documents[0]")).singleElement().asString().contains("not an OpenAPI 3 or AsyncAPI");
        assertThat(post("/api/projects/import/spec", map("documents", List.of())).expect(400).errorFields()).containsExactly("documents");
    }

    private static Map<String, JsonNode> byName(JsonNode list) {
        Map<String, JsonNode> out = new LinkedHashMap<>();
        list.forEach(n -> out.put(n.get("name").asString(), n));
        return out;
    }
}
