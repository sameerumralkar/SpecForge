package dev.specbuilder.generator.spec;

import dev.specbuilder.domain.MessagingProtocol;
import dev.specbuilder.generator.*;
import dev.specbuilder.generator.ProjectSnapshot.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static dev.specbuilder.generator.spec.Tree.obj;
import static dev.specbuilder.generator.spec.Tree.put;

/** AsyncAPI 3.0 for the project's async endpoints: asyncapi.yaml and asyncapi.json. */
@Component
public class AsyncApiGenerator implements Generator {

    public static final String ID = "asyncapi";
    private static final String SCHEMAS = "#/components/schemas/";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public GenerationResult generate(ProjectSnapshot project) {
        List<Problem> problems = new ArrayList<>();
        List<EndpointSnapshot> async = project.asyncEndpoints().stream().sorted(Comparator.comparing(EndpointSnapshot::name)).toList();
        if (async.isEmpty()) {
            problems.add(Problem.info(ID, "The project has no async endpoints, so there is no AsyncAPI document."));
            return new GenerationResult(List.of(), problems);
        }
        JsonSchemas schemas = new JsonSchemas(SCHEMAS, problems, ID);

        Map<String, Object> doc = obj();
        doc.put("asyncapi", "3.0.0");
        Map<String, Object> info = obj();
        info.put("title", project.name());
        info.put("version", project.version());
        put(info, "description", project.description());
        SortedSet<String> tags = new TreeSet<>();
        async.forEach(e -> tags.addAll(e.tags()));
        put(info, "tags", tags.stream().map(t -> Map.<String, Object>of("name", t)).toList());
        doc.put("info", info);
        doc.put("defaultContentType", "application/json");

        SortedSet<MessagingProtocol> protocols = async.stream().map(EndpointSnapshot::protocol)
                .collect(Collectors.toCollection(TreeSet::new));
        Map<String, Object> servers = obj();
        for (MessagingProtocol p : protocols) {
            Map<String, Object> server = obj();
            server.put("host", switch (p) {
                case KAFKA -> "localhost:9092";
                case JMS -> "localhost:61616";
                case AMQP -> "localhost:5672";
            });
            server.put("protocol", serverProtocol(p));
            server.put("description", "Local " + p + " broker. Replace with the real host per environment.");
            servers.put(serverId(p), server);
        }
        doc.put("servers", servers);
        problems.add(Problem.info(ID, "Servers use localhost placeholders; set the real broker hosts per environment."));

        Map<String, String> messageKeys = messageKeys(async);
        Map<String, String> channelIds = channelIds(async);

        // Channels, in address order; each lists the messages its operations carry.
        Map<String, Object> channels = obj();
        Map<String, List<EndpointSnapshot>> byChannel = async.stream()
                .collect(Collectors.groupingBy(EndpointSnapshot::channel, TreeMap::new, Collectors.toList()));
        byChannel.forEach((address, endpoints) -> {
            Map<String, Object> channel = obj();
            channel.put("address", address);
            Map<String, Object> messages = obj();
            endpoints.stream().map(e -> messageKeys.get(e.name())).distinct().sorted()
                    .forEach(key -> messages.put(key, Tree.ref("#/components/messages/" + key)));
            channel.put("messages", messages);
            channel.put("servers", endpoints.stream().map(EndpointSnapshot::protocol).distinct().sorted()
                    .map(p -> Tree.ref("#/servers/" + serverId(p))).toList());
            channels.put(channelIds.get(address), channel);
        });
        doc.put("channels", channels);

        Map<String, Object> operations = obj();
        for (EndpointSnapshot e : async) {
            String channelId = channelIds.get(e.channel());
            Map<String, Object> op = obj();
            op.put("action", e.operation().name().toLowerCase(Locale.ROOT));
            op.put("channel", Tree.ref("#/channels/" + channelId));
            put(op, "summary", e.summary());
            put(op, "description", e.description());
            put(op, "tags", e.tags().stream().map(t -> Map.<String, Object>of("name", t)).toList());
            op.put("messages", List.of(Tree.ref("#/channels/" + channelId + "/messages/" + messageKeys.get(e.name()))));
            operations.put(e.name(), op);
        }
        doc.put("operations", operations);

        Map<String, Object> components = obj();
        Map<String, Object> messages = obj();
        Map<String, EndpointSnapshot> firstByKey = new TreeMap<>();
        async.forEach(e -> firstByKey.putIfAbsent(messageKeys.get(e.name()), e));
        firstByKey.forEach((key, e) -> messages.put(key, message(key, e)));
        components.put("messages", messages);
        Map<String, Object> componentSchemas = obj();
        Map<String, PayloadSnapshot> byName = project.payloadsByName();
        for (String name : project.reachableFrom(async.stream().map(EndpointSnapshot::messagePayload).toList())) {
            componentSchemas.put(name, schemas.payload(byName.get(name), "/components/schemas/" + Tree.escape(name)));
        }
        components.put("schemas", componentSchemas);
        doc.put("components", components);
        put(doc, "x-error-codes", project.errorCodes().stream().map(OpenApiGenerator::errorCode).toList());

        return new GenerationResult(List.of(
                new GeneratedFile("asyncapi.yaml", SpecWriter.yaml(doc), "application/yaml"),
                new GeneratedFile("asyncapi.json", SpecWriter.json(doc), "application/json")), problems);
    }

    private static Map<String, Object> message(String key, EndpointSnapshot e) {
        Map<String, Object> m = obj();
        m.put("name", key);
        m.put("title", e.messagePayload());
        m.put("contentType", "application/json");
        if (!e.parameters().isEmpty()) {
            Map<String, Object> headers = obj();
            headers.put("type", "object");
            Map<String, Object> properties = obj();
            List<String> required = new ArrayList<>();
            for (ParameterSnapshot h : e.parameters()) {
                Map<String, Object> schema = JsonSchemas.scalar(h.type());
                put(schema, "description", h.description());
                properties.put(h.name(), schema);
                if (h.required()) {
                    required.add(h.name());
                }
            }
            headers.put("properties", properties);
            put(headers, "required", required);
            m.put("headers", headers);
        }
        m.put("payload", Tree.ref(SCHEMAS + e.messagePayload()));
        return m;
    }

    /**
     * One message per payload. If endpoints send the same payload with different headers, the
     * later ones get their own message named after the endpoint.
     */
    static Map<String, String> messageKeys(List<EndpointSnapshot> async) {
        Map<String, String> keys = new HashMap<>();
        Map<String, String> headersOfKey = new HashMap<>();
        for (EndpointSnapshot e : async) {
            String headers = e.parameters().toString();
            String key = e.messagePayload();
            if (headersOfKey.containsKey(key) && !headersOfKey.get(key).equals(headers)) {
                key = e.messagePayload() + Character.toUpperCase(e.name().charAt(0)) + e.name().substring(1);
            }
            headersOfKey.putIfAbsent(key, headers);
            keys.put(e.name(), key);
        }
        return keys;
    }

    /** "orders.created" → "ordersCreated", unique across the document. */
    static Map<String, String> channelIds(List<EndpointSnapshot> async) {
        Map<String, String> ids = new TreeMap<>();
        Set<String> taken = new HashSet<>();
        async.stream().map(EndpointSnapshot::channel).distinct().sorted().forEach(address -> {
            StringBuilder sb = new StringBuilder();
            boolean upper = false;
            for (char c : address.toCharArray()) {
                if (Character.isLetterOrDigit(c)) {
                    sb.append(upper ? Character.toUpperCase(c) : sb.isEmpty() ? Character.toLowerCase(c) : c);
                    upper = false;
                } else {
                    upper = !sb.isEmpty();
                }
            }
            String base = sb.isEmpty() || Character.isDigit(sb.charAt(0)) ? "channel" + sb : sb.toString();
            String id = base;
            for (int n = 2; !taken.add(id); n++) {
                id = base + n;
            }
            ids.put(address, id);
        });
        return ids;
    }

    private static String serverId(MessagingProtocol p) {
        return p.name().toLowerCase(Locale.ROOT);
    }

    private static String serverProtocol(MessagingProtocol p) {
        return switch (p) {
            case KAFKA -> "kafka";
            case JMS -> "jms";
            case AMQP -> "amqp";
        };
    }
}
