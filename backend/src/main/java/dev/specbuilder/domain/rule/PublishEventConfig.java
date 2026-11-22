package dev.specbuilder.domain.rule;

import dev.specbuilder.domain.MessagingProtocol;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

/** Sends an event. Without an event payload the current payload is sent as it is. */
public record PublishEventConfig(
        MessagingProtocol protocol,
        @NotBlank @Size(max = 249) @Pattern(regexp = "[A-Za-z0-9._/-]+", message = "use letters, digits, '.', '_', '-' or '/'")
        String destination,
        UUID eventPayloadId,
        String keyField) implements RuleConfig {

    public PublishEventConfig {
        protocol = protocol == null ? MessagingProtocol.KAFKA : protocol;
    }

    @Override
    public Set<UUID> payloadRefs() {
        return eventPayloadId == null ? Set.of() : Set.of(eventPayloadId);
    }

    @Override
    public String summary(SummaryContext ctx) {
        String what = eventPayloadId == null ? ctx.current() : SummaryContext.withArticle(ctx.payload(eventPayloadId));
        String where = switch (protocol) {
            case KAFKA -> "Kafka topic ";
            case JMS -> "JMS destination ";
            case AMQP -> "AMQP exchange ";
        };
        String key = RuleConfig.blank(keyField) ? "" : " keyed by " + keyField;
        return "Publish " + what + " to " + where + destination + key + ".";
    }
}
