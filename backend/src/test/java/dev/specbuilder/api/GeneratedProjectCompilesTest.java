package dev.specbuilder.api;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Generates projects, unzips them and builds each with Maven: the generated code must compile
 * with no edits. Runs {@code mvn} from the PATH, or the command named by -Dcodegen.mvn. Needs
 * network access the first time (Maven downloads dependencies). Skip with -Dcodegen.compile.skip=true.
 */
@DisabledIfSystemProperty(named = "codegen.compile.skip", matches = "true")
class GeneratedProjectCompilesTest extends ApiTestSupport {

    @Test
    void theSampleCompiles() throws Exception {
        String p = sample();
        compile(p, "sample");
    }

    @Test
    void everyStyleProtocolAndRuleTypeCompiles() throws Exception {
        String p = sample();
        String base = "/api/projects/" + p;
        Map<String, String> payloads = ids(get(base + "/payloads").json());
        Map<String, String> endpoints = ids(get(base + "/endpoints").json());

        // REST endpoint as a Camel route, with more rule types.
        String routed = duplicate(p, endpoints.get("createOrder"), e -> e.put("implementationStyle", "PROCESSING_ROUTE"));
        addRule(p, routed, "SPLIT_AGGREGATE", "Collect items", null, map("collectionField", "items", "parallel", true));
        addRule(p, routed, "SPLIT_AGGREGATE", "Count items", null, map("collectionField", "items", "aggregation", "COUNT"));
        addRule(p, routed, "SPLIT_AGGREGATE", "Total price", null, map("collectionField", "items", "aggregation", "SUM", "sumField", "unitPrice"));
        addRule(p, routed, "ENRICH", "Notify audit", "status == 'PENDING'", map("serviceName", "audit-service", "httpMethod", "POST", "url", "/audit"));
        addRule(p, routed, "CONDITIONAL_ROUTE", "Big orders", "sum(items[].quantity) > 0", map("branches",
                List.of(map("name", "bigOrder", "condition", "total > 1000 and not isEmpty(items)", "description", "flag it"))));
        addRule(p, routed, "ERROR_HANDLING", "Default on failure", null, map("on", List.of("PERSISTENCE"),
                "fallback", map("type", "DEFAULT_RESPONSE")));

        // The same rules in implementation-class style.
        String impl = endpoints.get("createOrder");
        addRule(p, impl, "SPLIT_AGGREGATE", "Total price", null, map("collectionField", "items", "aggregation", "SUM", "sumField", "unitPrice"));
        addRule(p, impl, "SPLIT_AGGREGATE", "Each item", null, map("collectionField", "items", "aggregation", "NONE"));
        addRule(p, impl, "CONDITIONAL_ROUTE", "Big orders", "sum(items[].quantity) > 0", map("branches",
                List.of(map("name", "bigOrder", "condition", "total > 1000"), map("name", "smallOrder", "condition", "total <= 10"))));
        addRule(p, impl, "VALIDATE", "Custom check", null, map("checks", List.of(
                map("field", "status", "kind", "CUSTOM", "value", "oneOf(status, 'PENDING', 'PAID')"),
                map("field", "items[].sku", "kind", "MAX_LENGTH", "value", "20"))));
        addRule(p, impl, "ERROR_HANDLING", "Whole chain", null, map("on", List.of("ANY"),
                "retry", map("maxAttempts", 2), "fallback", map("type", "DEFAULT_RESPONSE")));

        // Async receive as an implementation class on JMS, and as a route on AMQP.
        duplicate(p, endpoints.get("handlePaymentCompleted"), e -> {
            e.put("implementationStyle", "IMPLEMENTATION_CLASS");
            e.put("protocol", "JMS");
            e.put("channel", "payments.jms");
        });
        duplicate(p, endpoints.get("handlePaymentCompleted"), e -> {
            e.put("protocol", "AMQP");
            e.put("channel", "payments.amqp");
        });
        // Async send as a route (Camel gateway) on JMS.
        String routedSend = duplicate(p, endpoints.get("orderCreated"), e -> {
            e.put("implementationStyle", "PROCESSING_ROUTE");
            e.put("protocol", "JMS");
        });
        addRule(p, routedSend, "VALIDATE", "Check event", null, map("checks", List.of(map("field", "orderId", "kind", "REQUIRED"))));

        // Deletes, in both styles.
        String delete = post(base + "/endpoints", map("kind", "REST", "name", "deleteOrder", "httpMethod", "DELETE",
                "path", "/orders/{orderId}", "tags", List.of("Orders"),
                "parameters", List.of(param("orderId", "PATH", "UUID")),
                "responses", List.of(map("statusCode", "204")))).expect(201).id();
        addRule(p, delete, "PERSIST", "Delete order", null, map("entityName", "OrderEntity", "operation", "DELETE", "idField", "orderId"));
        duplicate(p, delete, e -> e.put("implementationStyle", "PROCESSING_ROUTE"));

        for (JsonNode e : get(base + "/endpoints").json()) {
            JsonNode chain = get(base + "/endpoints/" + e.get("id").asString() + "/rules").json();
            assertThat(chain.get("warnings").size()).as(e.get("name") + " warnings: " + chain.get("warnings")).isZero();
        }
        assertThat(payloads).containsKey("Order");
        compile(p, "kitchen-sink");
    }

    /**
     * The SGQR example: a FIND_BY_ID into a field, plusSeconds, the two QR types, balance and limit checks
     * in an implementation class, and a Camel route.
     */
    @Test
    void theSgqrExampleCompiles() throws Exception {
        String example = Files.readString(Path.of("../examples/sgqr-paynow-payments.specbuilder.json"));
        Response imported = post("/api/projects/import", JSON.readTree(example)).expect(201);
        String p = imported.json().at("/project/id").asString();
        track(p);
        assertThat(imported.json().get("warnings").size()).isZero();
        for (JsonNode e : get("/api/projects/" + p + "/endpoints").json()) {
            JsonNode chain = get("/api/projects/" + p + "/endpoints/" + e.get("id").asString() + "/rules").json();
            assertThat(chain.get("warnings").size()).as(e.get("name") + " warnings: " + chain.get("warnings")).isZero();
        }
        JsonNode code = get("/api/projects/" + p + "/code").json();
        String service = null;
        String request = null;
        String catalog = null;
        for (JsonNode f : code.get("files")) {
            if (f.get("path").asString().endsWith("/service/PaymentsServiceImpl.java")) service = f.get("content").asString();
            if (f.get("path").asString().endsWith("/error/ErrorCatalog.java")) catalog = f.get("content").asString();
            if (f.get("path").asString().endsWith("/dto/PaymentRequest.java")) request = f.get("content").asString();
        }
        // Restructured around inventory objects: Money{value, currency} and Payer{accountId, name}.
        assertThat(request).contains("private Money amount;", "private Payer payer;").doesNotContain("payerAccountId");
        assertThat(get("/api/projects/" + p + "/payloads?role=OBJECT").json().toString()).contains("\"Money\"", "\"Payer\"", "\"Merchant\"");
        assertThat(service)
                .contains("QrCode loaded = qrCodeEntityStore.findById(", "current.setQr(loaded);")
                .contains("throw new RuleViolationException(422, \"SGQR-2011\", \"This QR has expired\");")
                .contains("throw new RuleViolationException(422, \"SGQR-3003\", \"Insufficient funds\");")
                .contains("getAvailableBalance()).map($v -> $v.getValue())");
        // Error codes: a check's own message wins over the code's; without one, the field's code gives the message.
        assertThat(service)
                .contains("throw new RuleViolationException(400, \"SGQR-1005\", \"Not a valid account number\");")
                .contains("throw new RuleViolationException(400, \"SGQR-1003\", \"The amount must be from 0.01 to 999,999.99\");")
                .contains("throw new RuleViolationException(503, \"SGQR-5002\", \"Cannot check your balance right now, please try again\");")
                .contains("Fails with SGQR-4001.");
        assertThat(catalog).contains("SGQR_1003(\"SGQR-1003\", 400, \"The amount must be from 0.01 to 999,999.99\")",
                "SGQR_6004(\"SGQR-6004\", 500,");
        assertThat(get("/api/projects/" + p + "/error-codes").json().size()).isEqualTo(35);
        compile(p, "sgqr");
    }

    // ------------------------------------------------------------------ helpers

    private String sample() {
        String id = post("/api/projects/samples/order-service", null).expect(201).id();
        track(id);
        return id;
    }

    private static Map<String, String> ids(JsonNode list) {
        Map<String, String> ids = new java.util.HashMap<>();
        list.forEach(n -> ids.put(n.get("name").asString(), n.get("id").asString()));
        return ids;
    }

    /** Duplicates an endpoint, applies changes to the copy and saves it. Returns the copy's id. */
    private String duplicate(String p, String endpointId, java.util.function.Consumer<ObjectNode> change) {
        String base = "/api/projects/" + p + "/endpoints/";
        JsonNode copy = post(base + endpointId + "/duplicate", null).expect(201).json();
        ObjectNode body = (ObjectNode) copy;
        change.accept(body);
        put(base + copy.get("id").asString(), body).expect(200);
        return copy.get("id").asString();
    }

    private void addRule(String p, String endpointId, String type, String name, String condition, Object config) {
        Map<String, Object> rule = rule(type, name, config);
        rule.put("condition", condition);
        post("/api/projects/" + p + "/endpoints/" + endpointId + "/rules", rule).expect(201);
    }

    private void compile(String projectId, String label) throws Exception {
        MvcResult result = mvc.perform(MockMvcRequestBuilders.get("/api/projects/" + projectId + "/generate/code.zip")).andReturn();
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(200);
        Path dir = Files.createTempDirectory("codegen-" + label);
        Path zip = dir.resolve("code.zip");
        Files.write(zip, result.getResponse().getContentAsByteArray());
        Path project = unzip(zip, dir);

        Process mvn = new ProcessBuilder(System.getProperty("codegen.mvn", "mvn"), "-q", "-B", "-ntp", "compile", "test-compile")
                .directory(project.toFile())
                .redirectErrorStream(true)
                .redirectOutput(dir.resolve("build.log").toFile())
                .start();
        boolean finished = mvn.waitFor(15, TimeUnit.MINUTES);
        String log = Files.readString(dir.resolve("build.log"), StandardCharsets.UTF_8);
        assertThat(finished).as("Maven did not finish in time").isTrue();
        assertThat(mvn.exitValue()).as("generated project (" + project + ") failed to compile:\n" + tail(log)).isZero();
    }

    /** Unzips, keeping the executable bit; returns the single top folder. */
    private static Path unzip(Path zip, Path into) throws IOException {
        Path top = null;
        try (SeekableByteChannel channel = Files.newByteChannel(zip);
             ZipFile file = ZipFile.builder().setSeekableByteChannel(channel).get()) {
            Enumeration<ZipArchiveEntry> entries = file.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                Path target = into.resolve(entry.getName()).normalize();
                assertThat(target.startsWith(into)).as("zip entry stays inside the folder").isTrue();
                Files.createDirectories(target.getParent());
                try (InputStream in = file.getInputStream(entry)) {
                    Files.copy(in, target);
                }
                if ((entry.getUnixMode() & 0100) != 0) {
                    Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rwxr-xr-x"));
                }
                if (top == null) {
                    top = into.resolve(entry.getName().substring(0, entry.getName().indexOf('/')));
                }
            }
        }
        assertThat(new File(top.toFile(), "pom.xml")).as("the project has a pom.xml").exists();
        assertThat(new File(top.toFile(), "mvnw")).as("no Maven wrapper is generated").doesNotExist();
        return top;
    }

    private static String tail(String log) {
        List<String> lines = log.lines().toList();
        return String.join("\n", lines.subList(Math.max(0, lines.size() - 60), lines.size()));
    }
}
