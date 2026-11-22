package dev.specbuilder.generator;

import dev.specbuilder.domain.*;
import dev.specbuilder.generator.ProjectSnapshot.ParameterSnapshot;
import dev.specbuilder.generator.spec.AsyncApiGenerator;
import dev.specbuilder.generator.spec.SpecValidator;
import dev.specbuilder.generator.spec.SpecWriter;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.List;

import static dev.specbuilder.generator.Snapshots.*;
import static org.assertj.core.api.Assertions.assertThat;

class AsyncApiGeneratorTest {

    private final AsyncApiGenerator generator = new AsyncApiGenerator();
    private final ParameterSnapshot trace = new ParameterSnapshot("traceId", ParameterLocation.HEADER, FieldType.STRING, true, "Trace");

    private ProjectSnapshot project() {
        return Snapshots.project(
                List.of(payload("Address", field("city", FieldType.STRING, true)),
                        payload("OrderPlaced", field("orderId", FieldType.UUID, true), ref("shipTo", "Address")),
                        payload("Refund", field("amount", FieldType.DECIMAL, true))),
                List.of(async("publishOrderPlaced", "orders.placed", AsyncOperation.SEND, MessagingProtocol.KAFKA, "OrderPlaced"),
                        async("onOrderPlaced", "orders.placed", AsyncOperation.RECEIVE, MessagingProtocol.KAFKA, "OrderPlaced", trace),
                        async("onRefund", "refunds/v1", AsyncOperation.RECEIVE, MessagingProtocol.JMS, "Refund"),
                        rest("ping", HttpMethod.GET, "/ping", null, List.of(), List.of(), List.of())));
    }

    private JsonNode doc() {
        return SpecWriter.readJson(generator.generate(project()).file("asyncapi.json").content());
    }

    @Test
    void writesServersChannelsOperationsAndMessages() {
        JsonNode doc = doc();
        assertThat(doc.get("asyncapi").asString()).isEqualTo("3.0.0");
        assertThat(doc.get("servers").propertyNames()).containsExactly("kafka", "jms");
        assertThat(doc.at("/servers/jms/protocol").asString()).isEqualTo("jms");

        assertThat(doc.get("channels").propertyNames()).containsExactly("ordersPlaced", "refundsV1");
        assertThat(doc.at("/channels/ordersPlaced/address").asString()).isEqualTo("orders.placed");
        assertThat(doc.at("/channels/refundsV1/servers/0/$ref").asString()).isEqualTo("#/servers/jms");

        assertThat(doc.get("operations").propertyNames()).containsExactly("onOrderPlaced", "onRefund", "publishOrderPlaced");
        assertThat(doc.at("/operations/onRefund/action").asString()).isEqualTo("receive");
        assertThat(doc.at("/operations/publishOrderPlaced/action").asString()).isEqualTo("send");
        assertThat(doc.at("/operations/onRefund/channel/$ref").asString()).isEqualTo("#/channels/refundsV1");
        assertThat(doc.at("/operations/onRefund/messages/0/$ref").asString()).isEqualTo("#/channels/refundsV1/messages/Refund");
    }

    @Test
    void sameMessageWithDifferentHeadersGetsItsOwnName() {
        JsonNode doc = doc();
        assertThat(doc.at("/channels/ordersPlaced/messages").propertyNames())
                .containsExactly("OrderPlaced", "OrderPlacedPublishOrderPlaced");
        assertThat(doc.at("/components/messages/OrderPlaced/headers/required/0").asString()).isEqualTo("traceId");
        assertThat(doc.at("/components/messages/OrderPlacedPublishOrderPlaced/headers").isMissingNode()).isTrue();
        assertThat(doc.at("/components/messages/OrderPlaced/payload/$ref").asString()).isEqualTo("#/components/schemas/OrderPlaced");
        assertThat(doc.at("/components/schemas").propertyNames()).containsExactly("Address", "OrderPlaced", "Refund");
    }

    @Test
    void isDeterministicValidAndYamlMatchesJson() {
        GenerationResult a = generator.generate(project());
        assertThat(a.files()).isEqualTo(generator.generate(project()).files());
        assertThat(SpecWriter.readYaml(a.file("asyncapi.yaml").content())).isEqualTo(SpecWriter.readJson(a.file("asyncapi.json").content()));
        assertThat(new SpecValidator().validateAsyncApi(a.file("asyncapi.json").content())).isEmpty();
    }
}
