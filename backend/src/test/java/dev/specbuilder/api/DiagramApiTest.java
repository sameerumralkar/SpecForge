package dev.specbuilder.api;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class DiagramApiTest extends ApiTestSupport {

    private String sample() {
        String id = post("/api/projects/samples/order-service", null).expect(201).id();
        track(id);
        return id;
    }

    private JsonNode diagram(JsonNode list, String id) {
        for (JsonNode d : list.get("diagrams")) if (d.get("id").asString().equals(id)) return d;
        throw new AssertionError("no diagram " + id);
    }

    @Test
    void listsAClassDiagramAnOverviewAndOneSequencePerEndpoint() {
        String p = sample();
        Response r = get("/api/projects/" + p + "/diagrams").expect(200);
        assertThat(r.json().get("problems").size()).as(r.json().get("problems").toString()).isZero();
        assertThat(texts(r.json().get("diagrams"), "id")).containsExactly("overview", "classes", "classes-model",
                "classes-events", "classes-orders", "classes-payments", "sequence-createOrder",
                "sequence-getOrder", "sequence-handlePaymentCompleted", "sequence-listOrders", "sequence-orderCreated");
        assertThat(diagram(r.json(), "classes-model").get("title").asString()).isEqualTo("Class diagram: data model");

        JsonNode classes = diagram(r.json(), "classes");
        assertThat(classes.get("mermaid").asString()).startsWith("classDiagram")
                .contains("class OrdersController").contains("class HandlePaymentCompletedRoute").contains("class OrderEntityStore")
                .contains("OrdersServiceImpl ..|> OrdersService").contains("Order *-- \"0..*\" OrderItem : items")
                .contains("OrdersController ..> OrdersService").contains("OrderEntityStore ..> Order : converts");

        JsonNode payment = diagram(r.json(), "sequence-handlePaymentCompleted");
        assertThat(payment.get("endpointName").asString()).isEqualTo("handlePaymentCompleted");
        assertThat(payment.get("mermaid").asString())
                .contains("participant route2 as HandlePaymentCompletedRoute (Camel)")
                .contains("q1-)route2: PaymentCompletedEvent")
                .contains("alt status == 'SUCCEEDED'")
                .contains("break if a step fails with any error after 3 attempts")
                .contains("-)q4: original message (dead letter)");

        JsonNode overview = diagram(r.json(), "overview");
        assertThat(overview.get("plantuml").asString()).contains("component \"Order Service\" as svc")
                .contains("cloud \"customer-service\" as ext_customer_service").contains("queue \"orders.created (Kafka)\"");
    }

    @Test
    void rendersPlantUmlToSvgAndPngAndServesSources() throws Exception {
        String p = sample();
        for (JsonNode d : get("/api/projects/" + p + "/diagrams").json().get("diagrams")) {
            String id = d.get("id").asString();
            MvcResult svg = fetch("/api/projects/" + p + "/diagrams/" + id + ".svg?download=false");
            assertThat(svg.getResponse().getContentType()).isEqualTo("image/svg+xml");
            assertThat(svg.getResponse().getHeader("Content-Disposition")).isEqualTo("inline");
            String text = svg.getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(text).as(id).startsWith("<svg").doesNotContain("Syntax Error").doesNotContain("Graphviz");
        }
        MvcResult png = fetch("/api/projects/" + p + "/diagrams/sequence-createOrder.png");
        assertThat(png.getResponse().getHeader("Content-Disposition")).isEqualTo("attachment; filename=\"sequence-createOrder.png\"");
        byte[] bytes = png.getResponse().getContentAsByteArray();
        assertThat(bytes).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
        assertThat(fetch("/api/projects/" + p + "/diagrams/classes.mmd").getResponse().getContentAsString()).startsWith("classDiagram");
        assertThat(fetch("/api/projects/" + p + "/diagrams/classes.puml").getResponse().getContentAsString()).startsWith("@startuml");
        get("/api/projects/" + p + "/diagrams/nope.puml").expect(404);
        get("/api/projects/" + p + "/diagrams/classes.gif").expect(404);
    }

    @Test
    void zipsEveryDiagramInEveryFormat() throws Exception {
        String p = sample();
        byte[] zip = fetch("/api/projects/" + p + "/generate/diagrams.zip").getResponse().getContentAsByteArray();
        List<String> names = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) names.add(e.getName());
        }
        assertThat(names).hasSize(11 * 4).contains("diagrams/classes.mmd", "diagrams/classes.puml", "diagrams/classes.plantuml.svg",
                "diagrams/sequence-orderCreated.plantuml.png");
    }

    @Test
    void smallerClassDiagramsShowOneTagOrJustThePayloads() {
        String p = sample();
        JsonNode list = get("/api/projects/" + p + "/diagrams").json();
        String model = diagram(list, "classes-model").get("mermaid").asString();
        assertThat(model).contains("class Order {").contains("Order *-- \"0..*\" OrderItem : items").doesNotContain("Controller");

        String orders = diagram(list, "classes-orders").get("mermaid").asString();
        assertThat(orders).contains("class OrdersController").contains("class OrdersServiceImpl").contains("class OrderEntityStore")
                .contains("class OrderEntity {").contains("class CustomerServiceClient").contains("class CreateOrderRequest")
                .contains("class OrderPage").doesNotContain("HandlePaymentCompletedRoute").doesNotContain("PaymentCompletedEvent");

        String payments = diagram(list, "classes-payments").get("mermaid").asString();
        assertThat(payments).contains("class HandlePaymentCompletedRoute").contains("class PaymentCompletedEvent")
                .contains("class OrderEntityStore").doesNotContain("OrdersController").doesNotContain("CreateOrderRequest");
        assertThat(diagram(list, "classes-payments").get("plantuml").asString()).contains("title Order Service: Class diagram: Payments");
    }

    @Test
    void diagramsAreDeterministic() {
        String p = sample();
        assertThat(get("/api/projects/" + p + "/diagrams").json()).isEqualTo(get("/api/projects/" + p + "/diagrams").json());
    }

    private MvcResult fetch(String url) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(url)).andReturn();
    }
}
