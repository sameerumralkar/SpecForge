package dev.specbuilder.generator;

import dev.specbuilder.domain.*;
import dev.specbuilder.domain.rule.RuleConfig;
import dev.specbuilder.domain.rule.RuleType;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * An immutable, ordered copy of a project for the generators. Payloads are sorted by name and
 * endpoints by name, so generators that walk it in order produce the same output every time.
 */
public record ProjectSnapshot(
        UUID id,
        String name,
        String basePackage,
        String version,
        String description,
        ImplementationStyle defaultImplementationStyle,
        List<PayloadSnapshot> payloads,
        List<EndpointSnapshot> endpoints) {

    public record PayloadSnapshot(UUID id, String name, String description, String example, List<FieldSnapshot> fields) {
    }

    /** {@code refPayload} is the referenced payload's name, for OBJECT fields and arrays of OBJECT. */
    public record FieldSnapshot(
            String name, FieldType type, FieldType itemType, String refPayload, boolean required,
            String format, String description, String example,
            Integer minLength, Integer maxLength, Double minimum, Double maximum, String pattern, List<String> enumValues) {

        /** The type of the value, or of each item for arrays. */
        public FieldType valueType() {
            return type == FieldType.ARRAY ? itemType : type;
        }
    }

    public record ParameterSnapshot(String name, ParameterLocation location, FieldType type, boolean required, String description) {
    }

    public record ResponseSnapshot(String statusCode, String description, String payload) {
    }

    public record RuleSnapshot(RuleType type, String name, String condition, RuleConfig config) {
    }

    public record EndpointSnapshot(
            UUID id, EndpointKind kind, String name, String summary, String description, List<String> tags,
            ImplementationStyle implementationStyle, List<ParameterSnapshot> parameters,
            HttpMethod httpMethod, String path, String requestPayload, List<ResponseSnapshot> responses, List<SecurityScheme> security,
            String channel, AsyncOperation operation, MessagingProtocol protocol, String messagePayload,
            List<RuleSnapshot> rules) {

        public boolean isRest() {
            return kind == EndpointKind.REST;
        }
    }

    public Optional<PayloadSnapshot> payload(String name) {
        return payloads.stream().filter(p -> p.name().equals(name)).findFirst();
    }

    public Map<String, PayloadSnapshot> payloadsByName() {
        return payloads.stream().collect(Collectors.toMap(PayloadSnapshot::name, Function.identity(), (a, b) -> a, TreeMap::new));
    }

    public List<EndpointSnapshot> restEndpoints() {
        return endpoints.stream().filter(EndpointSnapshot::isRest).toList();
    }

    public List<EndpointSnapshot> asyncEndpoints() {
        return endpoints.stream().filter(e -> !e.isRest()).toList();
    }

    /** The given payloads plus everything their fields refer to, transitively, sorted by name. */
    public SortedSet<String> reachableFrom(Collection<String> roots) {
        Map<String, PayloadSnapshot> byName = payloadsByName();
        SortedSet<String> seen = new TreeSet<>();
        Deque<String> todo = new ArrayDeque<>(roots.stream().filter(Objects::nonNull).sorted().toList());
        while (!todo.isEmpty()) {
            String name = todo.pop();
            PayloadSnapshot p = byName.get(name);
            if (p == null || !seen.add(name)) {
                continue;
            }
            p.fields().stream().map(FieldSnapshot::refPayload).filter(Objects::nonNull).sorted().forEach(todo::push);
        }
        return seen;
    }
}
