package dev.specbuilder.validation;

import dev.specbuilder.domain.Payload;
import dev.specbuilder.domain.PayloadField;

import java.util.*;
import java.util.stream.Collectors;

public final class PayloadGraphs {

    private PayloadGraphs() {
    }

    /** Edges payload → payloads its fields refer to. */
    public static Map<UUID, Set<UUID>> edges(Collection<Payload> payloads) {
        Map<UUID, Set<UUID>> edges = new HashMap<>();
        for (Payload p : payloads) {
            edges.put(p.getId(), refs(p.getFields()));
        }
        return edges;
    }

    public static Set<UUID> refs(List<PayloadField> fields) {
        Set<UUID> refs = new HashSet<>();
        for (PayloadField f : fields) {
            if (f.getRefPayload() != null) {
                refs.add(f.getRefPayload().getId());
            }
        }
        return refs;
    }

    public static String describe(List<UUID> cycle, Map<UUID, String> names) {
        return cycle.stream().map(id -> names.getOrDefault(id, id.toString())).collect(Collectors.joining(" → "));
    }
}
