package dev.specbuilder.domain.rule;

import java.util.UUID;
import java.util.function.Function;

/**
 * What a summary needs from outside the rule: payload names, and a name for the data flowing
 * into this step (e.g. "the CreateOrderRequest").
 */
public record SummaryContext(Function<UUID, String> payloadNames, String currentPayloadName) {

    public String payload(UUID id) {
        String name = id == null ? null : payloadNames.apply(id);
        return name == null ? "an unknown payload" : name;
    }

    /** "a Customer" / "an Order". */
    public static String withArticle(String noun) {
        return ("AEIOUaeiou".indexOf(noun.charAt(0)) >= 0 ? "an " : "a ") + noun;
    }

    public String current() {
        return currentPayloadName == null ? "the input" : "the " + currentPayloadName;
    }
}
