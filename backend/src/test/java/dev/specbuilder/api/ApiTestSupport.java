package dev.specbuilder.api;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack API tests on an in-memory H2 database. Deliberately not @Transactional: responses are
 * serialized after the service transaction ends, exactly as in production.
 */
@SpringBootTest(properties = {
        "specbuilder.seed-sample=false",
        "spring.datasource.url=jdbc:h2:mem:specbuilder-test;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
abstract class ApiTestSupport {

    protected static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    protected MockMvc mvc;

    private final List<String> createdProjects = new ArrayList<>();

    protected record Response(int status, JsonNode json) {

        Response expect(int expected) {
            assertThat(status).as("HTTP status, body: %s", json).isEqualTo(expected);
            return this;
        }

        String id() {
            return json.get("id").asString();
        }

        /** The messages of every validation error on {@code field}. */
        List<String> errorsFor(String field) {
            List<String> messages = new ArrayList<>();
            json.path("errors").forEach(e -> {
                if (e.get("field").asString().equals(field)) {
                    messages.add(e.get("message").asString());
                }
            });
            return messages;
        }

        List<String> errorFields() {
            List<String> fields = new ArrayList<>();
            json.path("errors").forEach(e -> fields.add(e.get("field").asString()));
            return fields;
        }
    }

    @AfterEach
    void deleteProjects() {
        createdProjects.forEach(id -> call(HttpMethod.DELETE, "/api/projects/" + id, null));
        createdProjects.clear();
    }

    protected Response call(HttpMethod method, String url, Object body) {
        try {
            MockHttpServletRequestBuilder request = MockMvcRequestBuilders.request(method, url).accept(MediaType.APPLICATION_JSON);
            if (body != null) {
                request.contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body));
            }
            MvcResult result = mvc.perform(request).andReturn();
            String content = result.getResponse().getContentAsString();
            return new Response(result.getResponse().getStatus(), content.isEmpty() ? JSON.nullNode() : JSON.readTree(content));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected Response get(String url) {
        return call(HttpMethod.GET, url, null);
    }

    protected Response post(String url, Object body) {
        return call(HttpMethod.POST, url, body);
    }

    protected Response put(String url, Object body) {
        return call(HttpMethod.PUT, url, body);
    }

    protected Response delete(String url) {
        return call(HttpMethod.DELETE, url, null);
    }

    // ---- fixtures ----

    protected String newProject() {
        return newProject(false);
    }

    protected String newProject(boolean allowCircularReferences) {
        String id = post("/api/projects", project("Test " + UUID.randomUUID(), allowCircularReferences)).expect(201).id();
        createdProjects.add(id);
        return id;
    }

    protected void forget(String projectId) {
        createdProjects.remove(projectId);
    }

    protected void track(String projectId) {
        createdProjects.add(projectId);
    }

    protected static Map<String, Object> project(String name, boolean allowCircular) {
        return map("name", name, "basePackage", "com.example.test", "version", "1.0.0",
                "defaultImplementationStyle", "IMPLEMENTATION_CLASS", "allowCircularReferences", allowCircular);
    }

    protected String payload(String projectId, String name, Object... fields) {
        return post("/api/projects/" + projectId + "/payloads", payloadBody(name, fields)).expect(201).id();
    }

    protected static Map<String, Object> payloadBody(String name, Object... fields) {
        return map("name", name, "fields", List.of(fields));
    }

    protected static Map<String, Object> field(String name, String type, Object... more) {
        Map<String, Object> f = map("name", name, "type", type);
        for (int i = 0; i < more.length; i += 2) {
            f.put((String) more[i], more[i + 1]);
        }
        return f;
    }

    protected static Map<String, Object> ref(String name, String payloadId) {
        return field(name, "OBJECT", "refPayloadId", payloadId);
    }

    protected static Map<String, Object> listOf(String name, String payloadId) {
        return field(name, "ARRAY", "itemType", "OBJECT", "refPayloadId", payloadId);
    }

    protected static Map<String, Object> rest(String name, String method, String path, String bodyId, String responseId, Object... params) {
        Map<String, Object> e = map("kind", "REST", "name", name, "httpMethod", method, "path", path,
                "parameters", List.of(params),
                "responses", List.of(map("statusCode", "200", "description", "OK", "payloadId", responseId)));
        e.put("requestPayloadId", bodyId);
        return e;
    }

    protected static Map<String, Object> async(String name, String channel, String operation, String messageId) {
        return map("kind", "ASYNC", "name", name, "channel", channel, "operation", operation,
                "protocol", "KAFKA", "messagePayloadId", messageId);
    }

    protected static Map<String, Object> param(String name, String location, String type) {
        return map("name", name, "location", location, "type", type);
    }

    protected static Map<String, Object> rule(String type, String name, Object config) {
        return map("type", type, "name", name, "config", config);
    }

    /** A mutable map that, unlike Map.of, allows null values. */
    protected static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    protected static List<String> texts(JsonNode array, String property) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(property == null ? n.asString() : n.get(property).asString()));
        return out;
    }
}
