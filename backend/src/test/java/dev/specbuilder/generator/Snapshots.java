package dev.specbuilder.generator;

import dev.specbuilder.domain.*;
import dev.specbuilder.generator.ProjectSnapshot.*;

import java.util.List;
import java.util.UUID;

/** Small hand-built snapshots for generator tests. */
public final class Snapshots {

    private Snapshots() {
    }

    public static FieldSnapshot field(String name, FieldType type, boolean required) {
        return new FieldSnapshot(name, type, null, null, required, null, null, null, null, null, null, null, null, List.of());
    }

    public static FieldSnapshot ref(String name, String payload) {
        return new FieldSnapshot(name, FieldType.OBJECT, null, payload, false, null, null, null, null, null, null, null, null, List.of());
    }

    public static FieldSnapshot listOf(String name, String payload) {
        return new FieldSnapshot(name, FieldType.ARRAY, FieldType.OBJECT, payload, true, null, null, null, 1, 50, null, null, null, List.of());
    }

    public static PayloadSnapshot payload(String name, FieldSnapshot... fields) {
        return new PayloadSnapshot(UUID.nameUUIDFromBytes(name.getBytes()), name, null, null, List.of(fields));
    }

    public static EndpointSnapshot rest(String name, HttpMethod method, String path, String body, List<ResponseSnapshot> responses,
                                        List<ParameterSnapshot> params, List<SecurityScheme> security, String... tags) {
        return new EndpointSnapshot(UUID.nameUUIDFromBytes(name.getBytes()), EndpointKind.REST, name, null, null, List.of(tags),
                ImplementationStyle.IMPLEMENTATION_CLASS, params, method, path, body, responses, security,
                null, null, null, null, List.of());
    }

    public static EndpointSnapshot async(String name, String channel, AsyncOperation op, MessagingProtocol protocol, String message,
                                         ParameterSnapshot... headers) {
        return new EndpointSnapshot(UUID.nameUUIDFromBytes(name.getBytes()), EndpointKind.ASYNC, name, null, null, List.of(),
                ImplementationStyle.IMPLEMENTATION_CLASS, List.of(headers), null, null, null, List.of(), List.of(),
                channel, op, protocol, message, List.of());
    }

    public static ProjectSnapshot project(List<PayloadSnapshot> payloads, List<EndpointSnapshot> endpoints) {
        return new ProjectSnapshot(UUID.nameUUIDFromBytes("p".getBytes()), "Shop", "com.example.shop", "2.0.0", "A shop",
                ImplementationStyle.IMPLEMENTATION_CLASS, payloads, endpoints);
    }
}
