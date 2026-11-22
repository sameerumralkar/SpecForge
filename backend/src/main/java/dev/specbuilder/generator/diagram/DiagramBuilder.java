package dev.specbuilder.generator.diagram;

import dev.specbuilder.domain.*;
import dev.specbuilder.domain.rule.*;
import dev.specbuilder.generator.ProjectSnapshot;
import dev.specbuilder.generator.ProjectSnapshot.*;
import dev.specbuilder.generator.code.CodeModel;
import dev.specbuilder.generator.diagram.DiagramModel.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds the class, sequence and overview diagrams of a project. Names come from the code model,
 * so the diagrams describe the code that Phase 4 generates.
 */
final class DiagramBuilder {

    private static final Set<String> HIDDEN_DEPENDENCIES = Set.of("Validator", "JsonMapper");

    private final ProjectSnapshot project;
    private final CodeModel code;

    DiagramBuilder(ProjectSnapshot project, CodeModel code) {
        this.project = project;
        this.code = code;
    }

    // ================================================================== class diagram

    /** Only the classes named in {@code only}, and the relations between them. */
    ClassDiagram classDiagram(Set<String> only) {
        ClassDiagram all = classDiagram();
        List<DiagramModel.Package> packages = all.packages().stream()
                .map(p -> new DiagramModel.Package(p.name(), p.classes().stream().filter(c -> only.contains(c.name())).toList()))
                .filter(p -> !p.classes().isEmpty()).toList();
        List<Relation> relations = all.relations().stream().filter(r -> only.contains(r.from()) && only.contains(r.to())).toList();
        return new ClassDiagram(packages, relations);
    }

    /** The payload classes. */
    Set<String> modelClasses() {
        return code.dtos().stream().map(CodeModel.Dto::className).collect(Collectors.toCollection(TreeSet::new));
    }

    /** The groups (tags) that have endpoints, by class-name prefix. */
    List<String> groupNames() {
        return code.groups().stream().map(CodeModel.Group::name).toList();
    }

    /**
     * The classes of one tag: its controller, service, listener and gateway; the routes of its
     * endpoints; what those use (stores with their repositories and entities, clients, mappers);
     * and the payloads its endpoints take and return.
     */
    Set<String> groupClasses(String group) {
        Set<String> names = new TreeSet<>();
        code.groups().stream().filter(g -> g.name().equals(group)).forEach(g -> {
            if (g.hasController()) names.add(g.controllerClass());
            if (g.hasService()) { names.add(g.serviceClass()); names.add(g.serviceImplClass()); }
            if (g.hasListener()) names.add(g.listenerClass());
            if (g.hasGateway()) names.add(g.gatewayClass());
        });
        List<EndpointSnapshot> endpoints = project.endpoints().stream()
                .filter(e -> dev.specbuilder.generator.code.Names.pascal(e.tags().isEmpty() ? "Api" : e.tags().getFirst()).equals(group)).toList();
        Set<String> endpointNames = endpoints.stream().map(EndpointSnapshot::name).collect(Collectors.toSet());
        code.routes().stream().filter(r -> endpointNames.contains(r.endpointName())).forEach(r -> names.add(r.className()));

        ClassDiagram all = classDiagram();
        boolean grew = true;
        while (grew) {
            grew = false;
            for (Relation r : all.relations()) {
                if (r.kind() != RelationKind.COMPOSITION && names.contains(r.from()) && names.add(r.to())) {
                    grew = true;
                }
            }
        }
        List<String> roots = new ArrayList<>();
        for (EndpointSnapshot e : endpoints) {
            roots.add(e.isRest() ? e.requestPayload() : e.messagePayload());
            e.responses().forEach(x -> roots.add(x.payload()));
        }
        names.addAll(project.reachableFrom(roots));
        return names;
    }

    ClassDiagram classDiagram() {
        Set<String> boxes = new HashSet<>();
        List<DiagramModel.Package> packages = new ArrayList<>();
        List<Relation> relations = new ArrayList<>();

        packages.add(new DiagramModel.Package("dto", code.dtos().stream().map(d -> new ClassBox(d.className(), null, false,
                d.fields().stream().map(f -> new Member(Visibility.PRIVATE, f.name(), f.plainType(), false, null)).toList())).toList()));

        List<ClassBox> web = new ArrayList<>();
        List<ClassBox> service = new ArrayList<>();
        List<ClassBox> messaging = new ArrayList<>();
        List<ClassBox> routes = new ArrayList<>();
        for (CodeModel.Group g : code.groups()) {
            if (g.hasController()) {
                web.add(new ClassBox(g.controllerClass(), "Controller", false, g.restEndpoints().stream()
                        .map(e -> new Member(Visibility.PUBLIC, e.name(), e.returnType(), true, types(e.serviceSignature()))).toList()));
                if (g.hasService()) relations.add(dependency(g.controllerClass(), g.serviceClass(), null));
                g.restEndpoints().stream().filter(CodeModel.Endpoint::isRoute)
                        .forEach(e -> relations.add(dependency(g.controllerClass(), e.route().className(), "direct:" + e.name())));
            }
            if (g.hasService()) {
                service.add(new ClassBox(g.serviceClass(), null, true, g.implEndpoints().stream()
                        .map(e -> new Member(Visibility.PUBLIC, e.name(), voidToNull(e.impl().chainReturnType()), true, types(e.serviceSignature()))).toList()));
                List<Member> members = new ArrayList<>();
                for (CodeModel.Endpoint e : g.implEndpoints()) {
                    members.add(new Member(Visibility.PUBLIC, e.name(), voidToNull(e.impl().chainReturnType()), true, types(e.serviceSignature())));
                    e.impl().steps().forEach(s -> members.add(new Member(Visibility.PRIVATE, s.methodName(), s.outType().equals("Object") ? null : s.outType(), true, "")));
                }
                service.add(new ClassBox(g.serviceImplClass(), "Service", false, members));
                relations.add(new Relation(g.serviceImplClass(), g.serviceClass(), RelationKind.REALIZATION, null, null));
                g.implDependencies().stream().filter(d -> !HIDDEN_DEPENDENCIES.contains(d.type()))
                        .forEach(d -> relations.add(dependency(g.serviceImplClass(), d.type(), null)));
            }
            if (g.hasListener()) {
                messaging.add(new ClassBox(g.listenerClass(), "Listener", false, g.listenerEndpoints().stream()
                        .map(e -> new Member(Visibility.PUBLIC, e.name(), null, true, "String")).toList()));
                relations.add(dependency(g.listenerClass(), g.serviceClass(), null));
            }
            if (g.hasGateway()) {
                routes.add(new ClassBox(g.gatewayClass(), "Gateway", false, g.gatewayEndpoints().stream()
                        .map(e -> new Member(Visibility.PUBLIC, e.name(), null, true, types(e.serviceSignature()))).toList()));
                g.gatewayEndpoints().forEach(e -> relations.add(dependency(g.gatewayClass(), e.route().className(), "direct:" + e.name())));
            }
        }
        for (CodeModel.Route r : code.routes()) {
            List<Member> members = new ArrayList<>();
            members.add(new Member(Visibility.PUBLIC, "configure", null, true, ""));
            r.steps().forEach(s -> members.add(new Member(Visibility.PRIVATE, s.methodName(), null, true, "")));
            routes.add(new ClassBox(r.className(), "Route", false, members));
            r.dependencies().stream().filter(d -> !HIDDEN_DEPENDENCIES.contains(d.type()))
                    .forEach(d -> relations.add(dependency(r.className(), d.type(), null)));
        }
        if (code.features().messaging()) {
            List<Member> sends = new ArrayList<>();
            if (code.features().springKafka()) sends.add(new Member(Visibility.PUBLIC, "sendKafka", null, true, "String, String, Object"));
            if (code.features().springJms()) sends.add(new Member(Visibility.PUBLIC, "sendJms", null, true, "String, Object"));
            if (code.features().springAmqp()) sends.add(new Member(Visibility.PUBLIC, "sendAmqp", null, true, "String, Object"));
            messaging.add(new ClassBox("MessagingGateway", "Gateway", false, sends));
        }
        packages.add(new DiagramModel.Package("web", web));
        packages.add(new DiagramModel.Package("service", service));
        packages.add(new DiagramModel.Package("route", routes));
        packages.add(new DiagramModel.Package("messaging", messaging));

        List<ClassBox> persistence = new ArrayList<>();
        for (CodeModel.Entity e : code.entities()) {
            List<Member> fields = new ArrayList<>();
            fields.add(new Member(Visibility.PRIVATE, e.id().name(), e.id().javaType(), false, null));
            e.columns().forEach(c -> fields.add(new Member(Visibility.PRIVATE, c.name(), c.javaType(), false, null)));
            persistence.add(new ClassBox(e.className(), "Entity", false, fields));
            persistence.add(new ClassBox(e.repositoryClass(), null, true, List.of()));
            List<Member> storeMethods = new ArrayList<>();
            if (e.hasSource()) {
                storeMethods.add(new Member(Visibility.PUBLIC, "save", e.sourceType(), true, e.sourceType()));
                storeMethods.add(new Member(Visibility.PUBLIC, "findById", e.sourceType(), true, e.id().javaType()));
                storeMethods.add(new Member(Visibility.PUBLIC, "update", e.sourceType(), true, e.id().javaType() + ", " + e.sourceType()));
            }
            storeMethods.add(new Member(Visibility.PUBLIC, "delete", null, true, e.id().javaType()));
            persistence.add(new ClassBox(e.storeClass(), "Store", false, storeMethods));
            relations.add(dependency(e.repositoryClass(), e.className(), "manages"));
            relations.add(dependency(e.storeClass(), e.repositoryClass(), null));
            if (e.hasSource()) relations.add(dependency(e.storeClass(), e.sourceType(), "converts"));
        }
        packages.add(new DiagramModel.Package("persistence", persistence));
        packages.add(new DiagramModel.Package("client", code.clients().stream().map(c -> new ClassBox(c.className(), "Client", false,
                c.methods().stream().map(m -> new Member(Visibility.PUBLIC, m.name(), m.responseType(), true, "")).toList())).toList()));
        if (!code.mapperMethods().isEmpty()) {
            packages.add(new DiagramModel.Package("mapper", List.of(new ClassBox("DtoMapper", null, true, code.mapperMethods().stream()
                    .map(m -> new Member(Visibility.PUBLIC, m.name(), m.targetType(), true, m.sourceType())).toList()))));
        }

        // Payload composition, from the fields that hold other payloads.
        for (PayloadSnapshot p : project.payloads()) {
            for (FieldSnapshot f : p.fields()) {
                if (f.refPayload() == null) continue;
                String multiplicity = f.type() == FieldType.ARRAY ? "0..*" : f.required() ? "1" : "0..1";
                relations.add(new Relation(p.name(), f.refPayload(), RelationKind.COMPOSITION, f.name(), multiplicity));
            }
        }

        packages.forEach(p -> p.classes().forEach(c -> boxes.add(c.name())));
        List<Relation> shown = relations.stream()
                .filter(r -> boxes.contains(r.from()) && boxes.contains(r.to()))
                .distinct().toList();
        return new ClassDiagram(packages, shown);
    }

    private static Relation dependency(String from, String to, String label) {
        return new Relation(from, to, RelationKind.DEPENDENCY, label, null);
    }

    private static String voidToNull(String type) {
        return type == null || type.equals("void") || type.equals("Void") ? null : type;
    }

    /** "CreateOrderRequest createOrderRequest, UUID orderId" → "CreateOrderRequest, UUID". */
    private static String types(String signature) {
        if (signature == null || signature.isBlank()) return "";
        return Arrays.stream(signature.split(",\\s*")).map(p -> p.substring(0, p.lastIndexOf(' ')).trim()).collect(Collectors.joining(", "));
    }

    // ================================================================== sequence diagrams

    /** Collects participants on first use, in the order they appear. */
    private static final class Cast {
        final Map<String, Participant> participants = new LinkedHashMap<>();
        final Map<String, String> idByKey = new HashMap<>();
        int counter;

        String add(String key, String label, ParticipantType type, String prefix) {
            return idByKey.computeIfAbsent(key, k -> {
                String id = prefix + (++counter);
                participants.put(id, new Participant(id, label, type));
                return id;
            });
        }
    }

    Sequence sequence(EndpointSnapshot e) {
        Cast cast = new Cast();
        List<Item> items = new ArrayList<>();
        boolean route = e.implementationStyle() == ImplementationStyle.PROCESSING_ROUTE;
        CodeModel.Group group = groupOf(e.name());
        String routeLabel = code.routes().stream().filter(x -> x.endpointName().equals(e.name()))
                .map(x -> x.className() + " (Camel)").findFirst().orElse(e.name() + " route");
        String self;
        String client = null;
        String controller = null;

        if (e.isRest()) {
            client = cast.add("client", "Client", ParticipantType.ACTOR, "client");
            controller = cast.add("controller", group == null ? "Controller" : group.controllerClass(), ParticipantType.PARTICIPANT, "web");
            self = route ? cast.add("self", routeLabel, ParticipantType.PARTICIPANT, "route")
                    : cast.add("self", group == null ? "Service" : group.serviceImplClass(), ParticipantType.PARTICIPANT, "svc");
            items.add(call(client, controller, e.httpMethod() + " " + e.path() + (e.requestPayload() == null ? "" : " (" + e.requestPayload() + ")")));
            items.add(call(controller, self, route ? "direct:" + e.name() : e.name() + "(" + inputs(e) + ")"));
        } else if (e.operation() == AsyncOperation.RECEIVE) {
            String in = queue(cast, e.channel(), e.protocol());
            if (route) {
                self = cast.add("self", routeLabel, ParticipantType.PARTICIPANT, "route");
                items.add(async(in, self, e.messagePayload()));
            } else {
                String listener = cast.add("listener", group.listenerClass(), ParticipantType.PARTICIPANT, "lst");
                self = cast.add("self", group.serviceImplClass(), ParticipantType.PARTICIPANT, "svc");
                items.add(async(in, listener, e.messagePayload()));
                items.add(call(listener, self, e.name() + "(" + inputs(e) + ")"));
            }
        } else {
            String caller = cast.add("caller", "Caller", ParticipantType.ACTOR, "caller");
            if (route) {
                String gateway = cast.add("gateway", group.gatewayClass(), ParticipantType.PARTICIPANT, "gw");
                self = cast.add("self", routeLabel, ParticipantType.PARTICIPANT, "route");
                items.add(call(caller, gateway, e.name() + "(" + e.messagePayload() + ")"));
                items.add(call(gateway, self, "direct:" + e.name()));
            } else {
                self = cast.add("self", group.serviceImplClass(), ParticipantType.PARTICIPANT, "svc");
                items.add(call(caller, self, e.name() + "(" + e.messagePayload() + ")"));
            }
        }

        Context ctx = new Context(e, cast, self, client, route);
        Map<String, List<ErrorHandlingConfig>> targeted = new HashMap<>();
        List<ErrorHandlingConfig> chainLevel = new ArrayList<>();
        for (RuleSnapshot r : e.rules()) {
            if (r.config() instanceof ErrorHandlingConfig h) {
                if (h.targetRule() == null) chainLevel.add(h);
                else targeted.computeIfAbsent(h.targetRule().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(h);
            }
        }
        String current = e.isRest() ? e.requestPayload() : e.messagePayload();
        for (RuleSnapshot r : e.rules()) {
            if (r.config() instanceof ErrorHandlingConfig) continue;
            List<Item> step = step(ctx, r, current);
            for (ErrorHandlingConfig h : targeted.getOrDefault(r.name().toLowerCase(Locale.ROOT), List.of())) {
                if (h.retry() != null) {
                    step = List.of(new Block(BlockType.LOOP, List.of(new Section("up to " + h.retry().maxAttempts() + " attempts", step))));
                }
                step = new ArrayList<>(step);
                step.add(new Block(BlockType.BREAK, List.of(new Section(failure(h), fallback(ctx, h)))));
            }
            if (r.condition() != null) {
                step = List.of(new Block(BlockType.OPT, List.of(new Section("only if " + r.condition(), step))));
            }
            items.addAll(step);
            current = after(r, current);
        }
        for (ErrorHandlingConfig h : chainLevel) {
            items.add(new Block(BlockType.BREAK, List.of(new Section("if a step fails with " + failure(h), fallback(ctx, h)))));
        }

        if (e.isRest()) {
            String returned = e.responses().stream().filter(x -> x.statusCode().startsWith("2"))
                    .min(Comparator.comparing(ResponseSnapshot::statusCode)).orElse(null) instanceof ResponseSnapshot ok
                    ? ok.statusCode() + (ok.payload() == null ? "" : " " + ok.payload()) : "200";
            items.add(reply(self, controller, returned.replaceFirst("^\\d+ ?", "").isEmpty() ? "done" : returned.replaceFirst("^\\d+ ", "")));
            items.add(reply(controller, client, returned));
        } else if (e.operation() == AsyncOperation.SEND) {
            items.add(async(self, queue(cast, e.channel(), e.protocol()), e.messagePayload()));
        }
        return new Sequence(List.copyOf(cast.participants.values()), items);
    }

    private record Context(EndpointSnapshot e, Cast cast, String self, String client, boolean route) {
    }

    private List<Item> step(Context ctx, RuleSnapshot r, String current) {
        String self = ctx.self();
        List<Item> items = new ArrayList<>();
        switch (r.config()) {
            case ValidateConfig v -> {
                items.add(call(self, self, r.name() + ": " + v.checks().size() + " check" + (v.checks().size() == 1 ? "" : "s")));
                items.add(new Block(BlockType.BREAK, List.of(new Section("a check fails", List.of(errorExit(ctx, v.failureStatus() + " validation failed"))))));
            }
            case TransformConfig t -> items.add(call(self, self, r.name() + ": map " + (current == null ? "" : current + " ") + "to " + payloadName(t.targetPayloadId())));
            case EnrichConfig en -> {
                String ext = ctx.cast().add("ext:" + en.serviceName(), en.serviceName(), ParticipantType.EXTERNAL, "ext");
                items.add(call(self, ext, en.httpMethod() + " " + en.url()));
                items.add(reply(ext, self, en.responsePayloadId() == null ? "response" : payloadName(en.responsePayloadId())));
                if (en.targetField() != null && !en.targetField().isBlank()) {
                    items.add(new Note(self, "put it into " + en.targetField()));
                }
            }
            case ConditionalRouteConfig c -> {
                List<Section> sections = new ArrayList<>();
                for (ConditionalRouteConfig.Branch b : c.branches()) {
                    sections.add(new Section(b.condition(), List.of(call(self, self, b.name()
                            + (b.description() == null || b.description().isBlank() ? "" : ": " + b.description())))));
                }
                sections.add(new Section("otherwise", List.of(new Note(self, c.otherwise() == null || c.otherwise().isBlank() ? "continue" : c.otherwise()))));
                items.add(new Block(BlockType.ALT, sections));
            }
            case PersistConfig p -> {
                String db = ctx.cast().add("db", "Database", ParticipantType.DATABASE, "db");
                String id = p.idField() == null || p.idField().isBlank() ? "" : " by " + p.idField();
                switch (p.operation()) {
                    case SAVE -> {
                        items.add(call(self, db, "save " + p.entityName()));
                        items.add(reply(db, self, p.entityName()));
                    }
                    case FIND_BY_ID -> {
                        items.add(call(self, db, "find " + p.entityName() + id));
                        items.add(reply(db, self, p.entityName()));
                        items.add(new Block(BlockType.BREAK, List.of(new Section("not found", List.of(errorExit(ctx, "404 not found"))))));
                    }
                    case UPDATE -> {
                        items.add(call(self, db, "load " + p.entityName() + id));
                        items.add(call(self, db, "save the changes"));
                    }
                    case DELETE -> items.add(call(self, db, "delete " + p.entityName() + id));
                }
            }
            case PublishEventConfig p -> items.add(async(self, queue(ctx.cast(), p.destination(), p.protocol()),
                    p.eventPayloadId() == null ? (current == null ? "event" : current) : payloadName(p.eventPayloadId())));
            case SplitAggregateConfig s -> {
                items.add(new Block(BlockType.LOOP, List.of(new Section("each item of " + s.collectionField()
                        + (s.parallel() ? " (in parallel)" : ""), List.of(call(self, self, "process the item"))))));
                String agg = switch (s.aggregation()) {
                    case COLLECT_LIST -> "collect the results";
                    case SUM -> "add up " + s.sumField();
                    case COUNT -> "count the items";
                    case NONE -> null;
                };
                if (agg != null) items.add(new Note(self, r.name() + ": " + agg));
            }
            case ErrorHandlingConfig h -> { }
        }
        return items;
    }

    /** A request that fails: a reply to the REST client, or a note for messages. */
    private Item errorExit(Context ctx, String text) {
        return ctx.client() != null ? reply(ctx.self(), ctx.client(), text) : new Note(ctx.self(), "message fails: " + text);
    }

    private List<Item> fallback(Context ctx, ErrorHandlingConfig h) {
        List<Item> items = new ArrayList<>();
        if (h.deadLetterDestination() != null && ctx.e().kind() == EndpointKind.ASYNC) {
            items.add(async(ctx.self(), queue(ctx.cast(), h.deadLetterDestination(), ctx.e().protocol()), "original message (dead letter)"));
        }
        if (h.fallback() != null && h.fallback().type() == ErrorHandlingConfig.FallbackType.ERROR_RESPONSE) {
            items.add(errorExit(ctx, (h.fallback().httpStatus() == null ? 503 : h.fallback().httpStatus())
                    + (h.fallback().message() == null ? "" : " " + h.fallback().message())));
        } else if (h.fallback() != null && h.fallback().type() == ErrorHandlingConfig.FallbackType.DEFAULT_RESPONSE) {
            items.add(new Note(ctx.self(), "continue with a default result"));
        }
        if (items.isEmpty()) {
            items.add(new Note(ctx.self(), "the error is passed on"));
        }
        return items;
    }

    private static String failure(ErrorHandlingConfig h) {
        String what = h.on().contains(ErrorHandlingConfig.ErrorCategory.ANY) ? "any error"
                : h.on().stream().map(c -> c.name().toLowerCase(Locale.ROOT).replace('_', ' ')).collect(Collectors.joining(" or "));
        return what + (h.retry() == null ? "" : " after " + h.retry().maxAttempts() + " attempts");
    }

    private String after(RuleSnapshot r, String current) {
        if (r.config() instanceof TransformConfig t) return payloadName(t.targetPayloadId());
        return current;
    }

    private static String queue(Cast cast, String destination, MessagingProtocol protocol) {
        return cast.add("queue:" + destination, destination + " (" + protocolName(protocol) + ")", ParticipantType.QUEUE, "q");
    }

    static String protocolName(MessagingProtocol protocol) {
        return switch (protocol) {
            case KAFKA -> "Kafka";
            case JMS -> "JMS";
            case AMQP -> "AMQP";
        };
    }

    private static String inputs(EndpointSnapshot e) {
        List<String> parts = new ArrayList<>();
        String body = e.isRest() ? e.requestPayload() : e.messagePayload();
        if (body != null) parts.add(body);
        e.parameters().forEach(p -> parts.add(p.name()));
        return String.join(", ", parts);
    }

    private CodeModel.Group groupOf(String endpoint) {
        for (CodeModel.Group g : code.groups()) {
            if (g.restEndpoints().stream().anyMatch(x -> x.name().equals(endpoint))
                    || g.implEndpoints().stream().anyMatch(x -> x.name().equals(endpoint))
                    || g.gatewayEndpoints().stream().anyMatch(x -> x.name().equals(endpoint))) {
                return g;
            }
        }
        return null;
    }

    private String payloadName(UUID id) {
        return project.payloads().stream().filter(p -> p.id().equals(id)).map(PayloadSnapshot::name).findFirst().orElse("payload");
    }

    private static Message call(String from, String to, String text) {
        return new Message(from, to, text, MessageType.CALL);
    }

    private static Message reply(String from, String to, String text) {
        return new Message(from, to, text, MessageType.REPLY);
    }

    private static Message async(String from, String to, String text) {
        return new Message(from, to, text, MessageType.ASYNC);
    }

    // ================================================================== overview

    Overview overview() {
        Map<String, Node> nodes = new LinkedHashMap<>();
        Map<String, Edge> edges = new LinkedHashMap<>();
        nodes.put("svc", new Node("svc", project.name(), NodeType.SERVICE));

        List<String> routes = project.restEndpoints().stream().map(e -> e.httpMethod() + " " + e.path()).sorted().distinct().toList();
        if (!routes.isEmpty()) {
            nodes.put("clients", new Node("clients", "REST clients", NodeType.CLIENTS));
            String label = routes.size() <= 4 ? String.join(", ", routes) : String.join(", ", routes.subList(0, 3)) + " +" + (routes.size() - 3) + " more";
            edges.put("clients", new Edge("clients", "svc", label));
        }
        Map<String, String> queueIds = new TreeMap<>();
        java.util.function.BiFunction<String, MessagingProtocol, String> queue = (destination, protocol) -> queueIds.computeIfAbsent(destination, d -> {
            String id = "q" + (queueIds.size() + 1);
            nodes.put(id, new Node(id, d + " (" + protocolName(protocol) + ")", NodeType.QUEUE));
            return id;
        });
        for (EndpointSnapshot e : project.endpoints()) {
            if (!e.isRest()) {
                String q = queue.apply(e.channel(), e.protocol());
                if (e.operation() == AsyncOperation.RECEIVE) edges.putIfAbsent(q + ">svc", new Edge(q, "svc", "receive " + e.messagePayload()));
                else edges.putIfAbsent("svc>" + q, new Edge("svc", q, "send " + e.messagePayload()));
            }
            for (RuleSnapshot r : e.rules()) {
                switch (r.config()) {
                    case EnrichConfig en -> {
                        String id = "ext_" + Names.snake(en.serviceName());
                        nodes.putIfAbsent(id, new Node(id, en.serviceName(), NodeType.EXTERNAL));
                        edges.putIfAbsent("svc>" + id, new Edge("svc", id, en.httpMethod() + " " + en.url()));
                    }
                    case PersistConfig p -> {
                        nodes.putIfAbsent("db", new Node("db", "Database", NodeType.DATABASE));
                        edges.putIfAbsent("svc>db", new Edge("svc", "db", null));
                    }
                    case PublishEventConfig p -> {
                        String q = queue.apply(p.destination(), p.protocol());
                        edges.putIfAbsent("svc>" + q, new Edge("svc", q, "publish"));
                    }
                    case ErrorHandlingConfig h when h.deadLetterDestination() != null && !e.isRest() -> {
                        String q = queue.apply(h.deadLetterDestination(), e.protocol());
                        edges.putIfAbsent("svc>" + q, new Edge("svc", q, "dead letters"));
                    }
                    default -> { }
                }
            }
        }
        // Label database edges with the entities stored.
        String entities = code.entities().stream().map(CodeModel.Entity::className).collect(Collectors.joining(", "));
        if (edges.containsKey("svc>db")) edges.put("svc>db", new Edge("svc", "db", entities.isEmpty() ? null : entities));
        return new Overview(List.copyOf(nodes.values()), List.copyOf(edges.values()));
    }

    /** Local copy of the naming rules, to keep this package independent of the code generator's internals. */
    private static final class Names {
        static String snake(String s) {
            return s.replaceAll("[^A-Za-z0-9]+", "_").toLowerCase(Locale.ROOT);
        }
    }
}
