package dev.specbuilder.generator.code;

import dev.specbuilder.domain.*;
import dev.specbuilder.domain.rule.*;
import dev.specbuilder.generator.Problem;
import dev.specbuilder.generator.ProjectSnapshot;
import dev.specbuilder.generator.ProjectSnapshot.*;
import dev.specbuilder.generator.code.CodeModel.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Builds the {@link CodeModel} for one project. Everything that would make the generated project
 * fail to compile is reported as an ERROR problem instead.
 */
final class CodeModelBuilder {

    static final String SOURCE = "codegen";

    private final ProjectSnapshot project;
    private final Map<String, PayloadSnapshot> payloads;
    private final Set<String> reservedNames;
    private final JavaExpressions java;
    private final List<Problem> problems = new ArrayList<>();
    private final List<String> todos = new ArrayList<>();

    private final Map<String, MapperMethod> mapperMethods = new TreeMap<>();
    private final Map<String, EntityInfo> entityInfos = new TreeMap<>();
    private final Map<String, ClientInfo> clientInfos = new TreeMap<>();
    private final Names.Registry mapperNames = new Names.Registry();
    private final Names.Registry clientMethodNames = new Names.Registry();

    // Feature flags, set while building.
    private boolean retry;
    private boolean springKafka;
    private boolean springJms;
    private boolean springAmqp;
    private boolean camelKafka;
    private boolean camelJms;
    private boolean camelAmqp;

    CodeModelBuilder(ProjectSnapshot project, Set<String> reservedNames) {
        this.project = project;
        this.payloads = project.payloadsByName();
        this.reservedNames = reservedNames;
        this.java = new JavaExpressions(payloads);
    }

    List<Problem> problems() {
        return problems;
    }

    CodeModel build() {
        String base = project.basePackage();
        CodeModel.Project info = new CodeModel.Project(project.name(), project.description(), base, Names.kebab(project.name()),
                project.version(), base, base.replace('.', '/'), Names.pascal(project.name()) + "Application");

        for (PayloadSnapshot p : project.payloads()) {
            if (reservedNames.contains(p.name())) {
                error("payload " + p.name(), "'" + p.name() + "' clashes with a class the generated code imports; rename the payload");
            }
        }
        List<Dto> dtos = project.payloads().stream().map(this::dto).toList();
        planEntities();

        // Endpoints, grouped by their first tag.
        Map<String, List<CodeModel.Endpoint>> byGroup = new TreeMap<>();
        List<Route> routes = new ArrayList<>();
        Map<String, List<Handler>> handlersByGroup = new TreeMap<>();
        Map<String, Names.Registry> methodNamesByGroup = new HashMap<>();
        for (EndpointSnapshot e : project.endpoints()) {
            String group = Names.pascal(e.tags().isEmpty() ? "Api" : e.tags().getFirst());
            Names.Registry methods = methodNamesByGroup.computeIfAbsent(group, g -> {
                Names.Registry r = new Names.Registry();
                project.endpoints().forEach(x -> r.reserve(x.name()));
                return r;
            });
            CodeModel.Endpoint endpoint = endpoint(e, methods, handlersByGroup.computeIfAbsent(group, g -> new ArrayList<>()));
            byGroup.computeIfAbsent(group, g -> new ArrayList<>()).add(endpoint);
            if (endpoint.route() != null) {
                routes.add(endpoint.route());
            }
        }
        List<Group> groups = new ArrayList<>();
        byGroup.forEach((name, endpoints) -> groups.add(group(name, endpoints, handlersByGroup.get(name))));

        List<CodeModel.Entity> entities = entityInfos.values().stream().map(EntityInfo::toModel).toList();
        List<Client> clients = clientInfos.values().stream().map(ClientInfo::toModel).toList();
        checkClassNames(dtos, groups, routes, entities, clients, info);

        boolean camel = !routes.isEmpty() || groups.stream().anyMatch(Group::usesCamel);
        Features features = new Features(!entities.isEmpty(), !mapperMethods.isEmpty(), retry, !clients.isEmpty(), camel,
                springKafka, springJms, springAmqp, camelKafka, camelJms, camelAmqp,
                springKafka || springJms || springAmqp,
                springKafka || camelKafka, springJms || camelJms, springAmqp || camelAmqp);
        return new CodeModel(info, features, dtos, groups, routes, entities, clients, List.copyOf(mapperMethods.values()),
                todos.stream().distinct().toList());
    }

    // ================================================================== DTOs

    private Dto dto(PayloadSnapshot p) {
        List<DtoField> fields = p.fields().stream().map(f -> new DtoField(f.name(), javaType(f), plainType(f), Names.getter(f.name()),
                Names.setter(f.name()), annotations(f), f.description())).toList();
        return new Dto(p.name(), p.description(), fields);
    }

    static String scalarType(FieldType t) {
        return switch (t) {
            case STRING -> "String";
            case INTEGER -> "Integer";
            case LONG -> "Long";
            case NUMBER -> "Double";
            case DECIMAL -> "BigDecimal";
            case BOOLEAN -> "Boolean";
            case DATE -> "LocalDate";
            case DATE_TIME -> "OffsetDateTime";
            case UUID -> "UUID";
            case OBJECT, ARRAY -> "Object";
        };
    }

    private static String plainType(FieldSnapshot f) {
        return switch (f.type()) {
            case OBJECT -> f.refPayload();
            case ARRAY -> "List<" + (f.itemType() == FieldType.OBJECT ? f.refPayload() : scalarType(f.itemType())) + ">";
            default -> scalarType(f.type());
        };
    }

    private String javaType(FieldSnapshot f) {
        return switch (f.type()) {
            case OBJECT -> f.refPayload();
            case ARRAY -> "List<" + itemTypeWithAnnotations(f) + ">";
            default -> scalarType(f.type());
        };
    }

    /** Element type, with value constraints as type-use annotations, e.g. List<@Pattern(...) String>. */
    private String itemTypeWithAnnotations(FieldSnapshot f) {
        if (f.itemType() == FieldType.OBJECT) {
            return "@Valid " + f.refPayload();
        }
        List<String> anns = valueAnnotations(f, f.itemType());
        return (anns.isEmpty() ? "" : String.join(" ", anns) + " ") + scalarType(f.itemType());
    }

    private List<String> annotations(FieldSnapshot f) {
        List<String> anns = new ArrayList<>();
        if (f.required()) {
            anns.add("@NotNull");
        }
        if (f.type() == FieldType.OBJECT) {
            anns.add("@Valid");
        }
        if ((f.type() == FieldType.STRING || f.type() == FieldType.ARRAY) && (f.minLength() != null || f.maxLength() != null)) {
            List<String> args = new ArrayList<>();
            if (f.minLength() != null) args.add("min = " + f.minLength());
            if (f.maxLength() != null) args.add("max = " + f.maxLength());
            anns.add("@Size(" + String.join(", ", args) + ")");
        }
        if (f.type() != FieldType.ARRAY) {
            anns.addAll(valueAnnotations(f, f.type()));
        }
        return anns;
    }

    private static List<String> valueAnnotations(FieldSnapshot f, FieldType type) {
        List<String> anns = new ArrayList<>();
        if (type == FieldType.STRING) {
            if (f.pattern() != null) {
                anns.add("@Pattern(regexp = " + Names.literal(f.pattern()) + ")");
            }
            if (!f.enumValues().isEmpty()) {
                String alternatives = f.enumValues().stream().map(v -> v.replaceAll("[\\\\^$.|?*+()\\[\\]{}]", "\\\\$0"))
                        .collect(Collectors.joining("|"));
                anns.add("@Pattern(regexp = " + Names.literal(alternatives) + ", message = "
                        + Names.literal("must be one of " + String.join(", ", f.enumValues())) + ")");
            }
            if ("email".equalsIgnoreCase(f.format())) {
                anns.add("@Email");
            }
        }
        if (type == FieldType.INTEGER || type == FieldType.LONG) {
            if (f.minimum() != null) anns.add("@Min(" + (long) Math.ceil(f.minimum()) + "L)");
            if (f.maximum() != null) anns.add("@Max(" + (long) Math.floor(f.maximum()) + "L)");
        }
        if (type == FieldType.NUMBER || type == FieldType.DECIMAL) {
            if (f.minimum() != null) anns.add("@DecimalMin(" + Names.literal(plain(f.minimum())) + ")");
            if (f.maximum() != null) anns.add("@DecimalMax(" + Names.literal(plain(f.maximum())) + ")");
        }
        return anns;
    }

    private static String plain(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    // ================================================================== entities

    /** Works out what each PERSIST entity stores: the payload its SAVE/UPDATE rules save, else the response of a lookup. */
    private void planEntities() {
        for (EndpointSnapshot e : project.endpoints()) {
            String current = inputType(e);
            for (RuleSnapshot r : e.rules()) {
                if (r.config() instanceof PersistConfig p) {
                    EntityInfo info = entityInfos.computeIfAbsent(p.entityName(), EntityInfo::new);
                    if ((p.operation() == PersistConfig.Operation.SAVE) && current != null) {
                        if (info.source == null) {
                            info.source = current;
                        } else if (!info.source.equals(current)) {
                            error(e.name() + " / " + r.name(), p.entityName() + " is saved from both " + info.source + " and " + current
                                    + "; use one payload per entity, or a different entity name");
                        }
                    }
                }
                current = typeAfter(r, current);
            }
        }
        for (EndpointSnapshot e : project.endpoints()) {
            for (RuleSnapshot r : e.rules()) {
                if (r.config() instanceof PersistConfig p && p.operation() == PersistConfig.Operation.FIND_BY_ID && !intoField(p)) {
                    EntityInfo info = entityInfos.get(p.entityName());
                    if (info.source == null) {
                        info.source = successPayload(e);
                    }
                }
            }
        }
        for (EntityInfo info : entityInfos.values()) {
            info.complete();
        }
    }

    /** The payload type after a rule runs, as far as entity planning needs it. */
    private String typeAfter(RuleSnapshot r, String current) {
        return switch (r.config()) {
            case TransformConfig t -> payloadName(t.targetPayloadId());
            case PersistConfig p when p.operation() == PersistConfig.Operation.FIND_BY_ID && !intoField(p) ->
                    entityInfos.containsKey(p.entityName()) ? entityInfos.get(p.entityName()).source : null;
            default -> current;
        };
    }

    private static boolean intoField(PersistConfig p) {
        return p.targetField() != null && !p.targetField().isBlank();
    }

    private final class EntityInfo {
        final String className;
        String source;
        Column id;
        String idStrategy = "NONE";
        boolean idFromDto;
        final List<Column> columns = new ArrayList<>();
        final List<String> skipped = new ArrayList<>();

        EntityInfo(String className) {
            this.className = className;
        }

        void complete() {
            PayloadSnapshot p = source == null ? null : payloads.get(source);
            FieldSnapshot idField = p == null ? null : p.fields().stream()
                    .filter(f -> f.name().equals("id") && f.type() != FieldType.OBJECT && f.type() != FieldType.ARRAY).findFirst().orElse(null);
            if (idField != null) {
                id = column(idField.name(), scalarType(idField.type()));
                idFromDto = true;
                idStrategy = switch (idField.type()) {
                    case UUID -> "UUID_IF_MISSING";
                    case INTEGER, LONG -> "IDENTITY";
                    default -> "NONE";
                };
            } else {
                id = column("id", "UUID");
                idStrategy = "GENERATED_UUID";
            }
            if (p == null) {
                todos.add(className + ": nothing saves this entity, so it only has an id. Add its columns.");
                return;
            }
            for (FieldSnapshot f : p.fields()) {
                if (f == idField) continue;
                if (f.type() == FieldType.OBJECT || f.type() == FieldType.ARRAY) {
                    skipped.add(f.name());
                } else {
                    columns.add(column(f.name(), scalarType(f.type())));
                }
            }
            if (!skipped.isEmpty()) {
                todos.add(className + ": nested fields " + String.join(", ", skipped) + " are not stored yet (add relations or JSON columns).");
            }
        }

        CodeModel.Entity toModel() {
            return new CodeModel.Entity(className, Names.snake(className), className + "Repository", className + "Store",
                    Names.camel(className + "Store"), source, id, idStrategy, idFromDto, List.copyOf(columns), List.copyOf(skipped));
        }
    }

    private static Column column(String name, String type) {
        return new Column(name, type, Names.getter(name), Names.setter(name));
    }

    // ================================================================== endpoints

    private CodeModel.Endpoint endpoint(EndpointSnapshot e, Names.Registry groupMethods, List<Handler> groupHandlers) {
        boolean route = e.implementationStyle() == ImplementationStyle.PROCESSING_ROUTE;
        Names.Registry locals = new Names.Registry("current", "result", "event", "exchange", "json", "camel");
        List<Param> params = new ArrayList<>();
        for (ParameterSnapshot p : e.parameters()) {
            String javaName = locals.claim(Names.camel(p.name()));
            params.add(param(e, p, javaName));
        }
        String comment = e.summary() != null ? e.summary() : e.name();

        if (e.isRest()) {
            ResponseSnapshot success = e.responses().stream()
                    .filter(r -> r.statusCode().startsWith("2")).min(Comparator.comparing(ResponseSnapshot::statusCode)).orElse(null);
            int status = success == null ? 200 : Integer.parseInt(success.statusCode());
            String returnType = success == null || success.payload() == null ? "Void" : success.payload();
            String bodyType = e.requestPayload();
            String bodyVar = bodyType == null ? null : locals.claim(Names.camel(bodyType));
            String mapping = mapping(e);
            List<String> controllerArgs = new ArrayList<>();
            List<String> signature = new ArrayList<>();
            List<String> callArgs = new ArrayList<>();
            if (bodyType != null) {
                controllerArgs.add("@Valid @RequestBody " + bodyType + " " + bodyVar);
                signature.add(bodyType + " " + bodyVar);
                callArgs.add(bodyVar);
            }
            for (Param p : params) {
                controllerArgs.add(p.springAnnotation() + " " + p.javaType() + " " + p.javaName());
                signature.add(p.javaType() + " " + p.javaName());
                callArgs.add(p.javaName());
            }
            EndpointContext ctx = new EndpointContext(e, params, bodyType, bodyVar, returnType, route, groupMethods);
            ImplChain impl = route ? null : implChain(ctx, groupHandlers);
            Route r = route ? route(ctx) : null;
            return new CodeModel.Endpoint(e.name(), "REST", e.implementationStyle().name(), e.summary(), comment, mapping, e.path(),
                    status, returnType, bodyType, bodyVar, params, null, null, null, null, null, null,
                    String.join(", ", signature), String.join(", ", callArgs), String.join(", ", controllerArgs), impl, r);
        }

        String messageType = e.messagePayload();
        String messageVar = locals.claim(Names.camel(messageType));
        List<String> signature = new ArrayList<>(List.of(messageType + " " + messageVar));
        List<String> callArgs = new ArrayList<>(List.of(messageVar));
        for (Param p : params) {
            signature.add(p.javaType() + " " + p.javaName());
            callArgs.add(p.javaName());
        }
        if (route) {
            markCamel(e.protocol());
        } else {
            markSpring(e.protocol()); // listeners for RECEIVE; the messaging gateway for SEND
        }
        EndpointContext ctx = new EndpointContext(e, params, messageType, messageVar, "Void", route, groupMethods);
        ImplChain impl = route ? null : implChain(ctx, groupHandlers);
        Route r = route ? route(ctx) : null;
        return new CodeModel.Endpoint(e.name(), "ASYNC", e.implementationStyle().name(), e.summary(), comment, null, null, 0, "Void",
                null, null, params, e.channel(), e.protocol().name(), e.operation().name(), messageType, messageVar,
                listenerAnnotation(e), String.join(", ", signature), String.join(", ", callArgs), null, impl, r);
    }

    private Param param(EndpointSnapshot e, ParameterSnapshot p, String javaName) {
        String type = scalarType(p.type());
        String format = switch (p.type()) {
            case DATE -> "@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) ";
            case DATE_TIME -> "@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ";
            default -> "";
        };
        String spring = switch (p.location()) {
            case PATH -> format + "@PathVariable(" + Names.literal(p.name()) + ")";
            case QUERY -> format + "@RequestParam(name = " + Names.literal(p.name()) + ", required = " + p.required() + ")";
            case HEADER -> format + "@RequestHeader(name = " + Names.literal(p.name()) + ", required = " + p.required() + ")";
        };
        String listener = "@Header(name = " + Names.literal(p.name()) + ", required = false) String " + javaName;
        String conversion = type.equals("String") ? javaName : "Expressions.as(" + javaName + ", " + type + ".class)";
        return new Param(p.name(), javaName, type, p.location().name(), p.required(), spring, listener, conversion);
    }

    private static String mapping(EndpointSnapshot e) {
        String path = Names.literal(e.path());
        return switch (e.httpMethod()) {
            case GET -> "@GetMapping(" + path + ")";
            case POST -> "@PostMapping(" + path + ")";
            case PUT -> "@PutMapping(" + path + ")";
            case PATCH -> "@PatchMapping(" + path + ")";
            case DELETE -> "@DeleteMapping(" + path + ")";
            case HEAD -> "@RequestMapping(path = " + path + ", method = RequestMethod.HEAD)";
            case OPTIONS -> "@RequestMapping(path = " + path + ", method = RequestMethod.OPTIONS)";
        };
    }

    private static String listenerAnnotation(EndpointSnapshot e) {
        String channel = Names.literal(e.channel());
        return switch (e.protocol()) {
            case KAFKA -> "@KafkaListener(topics = " + channel + ", groupId = \"${spring.application.name}\")";
            case JMS -> "@JmsListener(destination = " + channel + ")";
            case AMQP -> "@RabbitListener(queuesToDeclare = @Queue(" + channel + "))";
        };
    }

    /** What every step of one endpoint needs to know. */
    private record EndpointContext(EndpointSnapshot e, List<Param> params, String inputType, String inputVar,
                                   String returnType, boolean route, Names.Registry methods) {

        Map<String, String> paramCode() {
            Map<String, String> code = new HashMap<>();
            for (Param p : params) {
                code.put(p.name(), route
                        ? "exchange.getMessage().getHeader(" + Names.literal(p.name()) + ", " + p.javaType() + ".class)"
                        : p.javaName());
            }
            return code;
        }

        String paramDecls() {
            return params.stream().map(p -> ", " + p.javaType() + " " + p.javaName()).collect(Collectors.joining());
        }

        String paramArgs() {
            return params.stream().map(p -> ", " + p.javaName()).collect(Collectors.joining());
        }

        String where(RuleSnapshot r) {
            return e.name() + " / " + r.name();
        }
    }

    // ================================================================== implementation-class chains

    private ImplChain implChain(EndpointContext ctx, List<Handler> groupHandlers) {
        EndpointSnapshot e = ctx.e();
        List<String> lines = new ArrayList<>();
        List<Step> steps = new ArrayList<>();
        Names.Registry vars = new Names.Registry("current", "result", "event");
        ctx.params().forEach(p -> vars.reserve(p.javaName()));
        Map<String, String> varOfType = new HashMap<>();

        String type = ctx.inputType() == null ? "Object" : ctx.inputType();
        String var;
        if (ctx.inputVar() == null) {
            var = vars.claim("input");
            lines.add("Object " + var + " = null; // this endpoint has no body");
        } else {
            var = ctx.inputVar();
            vars.reserve(var);
        }
        varOfType.put(type, var);

        Map<String, List<ErrorHandlingConfig>> stepHandlers = new HashMap<>();
        List<ErrorHandlingConfig> chainHandlers = new ArrayList<>();
        Map<ErrorHandlingConfig, Handler> handlerModels = new LinkedHashMap<>();
        for (RuleSnapshot r : e.rules()) {
            if (r.config() instanceof ErrorHandlingConfig h) {
                Handler model = handler(ctx, r, h);
                handlerModels.put(h, model);
                groupHandlers.add(model);
                if (h.targetRule() == null) {
                    chainHandlers.add(h);
                } else {
                    stepHandlers.computeIfAbsent(h.targetRule().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(h);
                }
            }
        }

        int copies = 0;
        for (RuleSnapshot r : e.rules()) {
            if (r.config() instanceof ErrorHandlingConfig) {
                continue;
            }
            JavaExpressions.Scope scope = new JavaExpressions.SimpleScope("current", payloads.get(type), ctx.paramCode());
            String method = ctx.methods().claim(Names.camel(r.name()));
            Step step = step(ctx, r, method, type, scope);
            steps.add(step);
            String outType = step.outType();

            String call = method + "(" + var + ctx.paramArgs() + ")";
            List<ErrorHandlingConfig> handlers = stepHandlers.getOrDefault(r.name().toLowerCase(Locale.ROOT), List.of());
            if (!handlers.isEmpty()) {
                String input = "$in" + (++copies);
                lines.add("final " + type + " " + input + " = " + var + ";");
                call = method + "(" + input + ctx.paramArgs() + ")";
                for (ErrorHandlingConfig h : handlers) {
                    String fallback = fallback(ctx, h, outType.equals(type) ? input : "null", ctx.inputVar());
                    call = handlerModels.get(h).methodName() + "(() -> " + call + ", " + fallback + ")";
                }
            }
            String condition = r.condition() == null ? null : java.condition(r.condition(),
                    new JavaExpressions.SimpleScope(var, payloads.get(type), ctx.paramCode()));
            if (condition != null && !outType.equals(type)) {
                error(ctx.where(r), "a step that changes the payload (" + type + " → " + outType + ") cannot have a condition");
            }
            String target;
            String declare = "";
            if (outType.equals(type)) {
                target = var;
            } else if (varOfType.containsKey(outType)) {
                target = varOfType.get(outType);
            } else {
                target = vars.claim(Names.camel(outType));
                varOfType.put(outType, target);
                declare = outType + " ";
            }
            String assignment = declare + target + " = " + call + ";";
            lines.add(condition == null ? assignment : "if (" + condition + ") {\n    " + assignment + "\n}");
            type = outType;
            var = target;
        }

        // Finish: return the response, or send the message for SEND endpoints.
        if (e.isRest()) {
            if (!ctx.returnType().equals("Void")) {
                lines.add("return " + convertTo(ctx, type, var, ctx.returnType()) + ";");
            }
        } else if (e.operation() == AsyncOperation.SEND) {
            String message = convertTo(ctx, type, var, e.messagePayload());
            lines.add(send(e.protocol(), e.channel(), "null", message) + ";");
        }

        String returnType = e.isRest() ? ctx.returnType() : "Void";
        String publicReturn = returnType.equals("Void") ? "void" : returnType;
        if (chainHandlers.isEmpty()) {
            return new ImplChain(null, null, null, publicReturn, lines, null, steps);
        }
        String chainMethod = ctx.methods().claim(e.name() + "Chain");
        String args = Arrays.stream(serviceCallArgs(ctx)).collect(Collectors.joining(", "));
        String call = publicReturn.equals("void") ? "() -> { " + chainMethod + "(" + args + "); return null; }" : "() -> " + chainMethod + "(" + args + ")";
        for (ErrorHandlingConfig h : chainHandlers) {
            String fallbackValue = publicReturn.equals("void") ? "null" : "new " + returnType + "()";
            call = "() -> " + handlerModels.get(h).methodName() + "(" + call + ", " + fallback(ctx, h, fallbackValue, ctx.inputVar()) + ")";
        }
        String wrapped = call.substring("() -> ".length());
        return new ImplChain(chainMethod, signatureOf(ctx), args, publicReturn, lines, wrapped, steps);
    }

    private static String[] serviceCallArgs(EndpointContext ctx) {
        List<String> args = new ArrayList<>();
        if (ctx.inputVar() != null) args.add(ctx.inputVar());
        ctx.params().forEach(p -> args.add(p.javaName()));
        return args.toArray(String[]::new);
    }

    private static String signatureOf(EndpointContext ctx) {
        List<String> parts = new ArrayList<>();
        if (ctx.inputVar() != null) parts.add(ctx.inputType() + " " + ctx.inputVar());
        ctx.params().forEach(p -> parts.add(p.javaType() + " " + p.javaName()));
        return String.join(", ", parts);
    }

    /** Converts the final payload to the type the endpoint returns or sends. */
    private String convertTo(EndpointContext ctx, String type, String var, String target) {
        if (type.equals(target)) {
            return var;
        }
        if (type.equals("Object")) {
            todos.add(ctx.e().name() + ": build the " + target + " to return (no rule produces it).");
            return "new " + target + "() /* TODO: no rule produces " + SummaryContext.withArticle(target) + " */";
        }
        return "dtoMapper." + autoMapper(ctx.e().name() + "To" + target, type, target) + "(" + var + ")";
    }

    /** The fallback lambda for a handler call site. */
    private String fallback(EndpointContext ctx, ErrorHandlingConfig h, String defaultValue, String messageVar) {
        boolean hasFallback = h.fallback() != null && h.fallback().type() != null;
        boolean deadLetter = h.deadLetterDestination() != null && ctx.e().kind() == EndpointKind.ASYNC && messageVar != null;
        if (!hasFallback && !deadLetter) {
            return "null";
        }
        StringBuilder body = new StringBuilder("() -> { ");
        if (deadLetter) {
            markSpring(ctx.e().protocol());
            body.append(send(ctx.e().protocol(), h.deadLetterDestination(), "null", messageVar)).append("; ");
        }
        if (hasFallback && h.fallback().type() == ErrorHandlingConfig.FallbackType.ERROR_RESPONSE) {
            int status = h.fallback().httpStatus() == null ? 503 : h.fallback().httpStatus();
            String message = h.fallback().message() == null ? "The request could not be completed" : h.fallback().message();
            body.append("throw new RuleViolationException(").append(status).append(", ").append(Names.literal(message)).append("); }");
        } else {
            body.append("return ").append(defaultValue).append("; }");
        }
        return body.toString();
    }

    private Handler handler(EndpointContext ctx, RuleSnapshot r, ErrorHandlingConfig h) {
        String method = ctx.methods().claim(Names.camel(r.name()));
        List<String> classes = h.on().contains(ErrorHandlingConfig.ErrorCategory.ANY)
                ? List.of("RuntimeException")
                : h.on().stream().map(CodeModelBuilder::exceptionClass).distinct().toList();
        boolean hasRetry = h.retry() != null;
        if (hasRetry && !ctx.route()) {
            retry = true;
        }
        boolean hasCatch = (h.fallback() != null && h.fallback().type() != null)
                || (h.deadLetterDestination() != null && ctx.e().kind() == EndpointKind.ASYNC);
        return new Handler(method, r.name(), summaryOf(r, null), hasRetry,
                hasRetry ? h.retry().maxAttempts() : 1, hasRetry ? h.retry().backoffMs() : 0, hasRetry ? h.retry().multiplier() : 1,
                classes.stream().map(c -> c + ".class").toList(), String.join(" | ", classes), hasCatch);
    }

    static String exceptionClass(ErrorHandlingConfig.ErrorCategory c) {
        return switch (c) {
            case ANY -> "RuntimeException";
            case VALIDATION -> "RuleViolationException";
            case DOWNSTREAM_UNAVAILABLE -> "DownstreamUnavailableException";
            case TIMEOUT -> "DownstreamTimeoutException";
            case PERSISTENCE -> "PersistenceFailedException";
            case MESSAGING -> "MessagingFailedException";
        };
    }

    // ================================================================== steps (both styles)

    private Step step(EndpointContext ctx, RuleSnapshot r, String method, String inType, JavaExpressions.Scope scope) {
        String signature = "(" + inType + " current" + ctx.paramDecls() + ")";
        String args = "current" + ctx.paramArgs();
        String summary = summaryOf(r, inType);
        String condition = null;
        if (ctx.route() && r.condition() != null) {
            condition = camelPredicate(r.condition(), inType, ctx);
        }
        String sub = "direct:" + ctx.e().name() + "-" + method;
        return switch (r.config()) {
            case ValidateConfig v -> new Step("VALIDATE", r.name(), summary, method, signature, args, inType, inType, condition,
                    new ValidateData(v.checks().stream().map(c -> check(c, scope)).toList(), v.failureStatus(),
                            "bean-validator:" + ctx.e().name() + "-" + method));
            case TransformConfig t -> transform(ctx, r, t, method, signature, inType, summary, condition);
            case EnrichConfig en -> enrich(ctx, r, en, method, signature, inType, summary, condition, scope, sub);
            case ConditionalRouteConfig c -> conditional(ctx, r, c, method, signature, inType, summary, condition, scope);
            case PersistConfig p -> persist(ctx, r, p, method, signature, inType, summary, condition, scope);
            case PublishEventConfig p -> publish(ctx, r, p, method, signature, inType, summary, condition);
            case SplitAggregateConfig s -> split(ctx, r, s, method, signature, inType, summary, condition, sub);
            case ErrorHandlingConfig h -> throw new IllegalStateException("handled separately");
        };
    }

    private Check check(ValidateConfig.Check c, JavaExpressions.Scope scope) {
        String v = c.value();
        String failsWhen = switch (c.kind()) {
            case REQUIRED -> java.anyValue(c.field(), scope, x -> x + " == null");
            case NOT_BLANK -> java.anyValue(c.field(), scope, x -> "Expressions.isBlank(" + x + ")");
            case PATTERN -> java.anyValue(c.field(), scope, x -> "!Expressions.matches(" + x + ", " + Names.literal(v) + ")");
            case MIN -> java.anyValue(c.field(), scope, x -> "Expressions.lt(" + x + ", " + v + ")");
            case MAX -> java.anyValue(c.field(), scope, x -> "Expressions.gt(" + x + ", " + v + ")");
            case MIN_LENGTH -> java.anyValue(c.field(), scope, x -> "Expressions.size(" + x + ") < " + v);
            case MAX_LENGTH -> java.anyValue(c.field(), scope, x -> "Expressions.size(" + x + ") > " + v);
            case CUSTOM -> "!(" + java.condition(v, scope) + ")";
        };
        String message = c.message() != null && !c.message().isBlank() ? c.message() : switch (c.kind()) {
            case REQUIRED -> c.field() + " is required";
            case NOT_BLANK -> c.field() + " must not be blank";
            case PATTERN -> c.field() + " must match " + v;
            case MIN -> c.field() + " must be at least " + v;
            case MAX -> c.field() + " must be at most " + v;
            case MIN_LENGTH -> c.field() + " needs at least " + v + " characters or items";
            case MAX_LENGTH -> c.field() + " allows at most " + v + " characters or items";
            case CUSTOM -> c.field() + " must satisfy " + v;
        };
        return new Check(failsWhen, Names.literal(message), c.kind() + " " + c.field() + (v == null ? "" : " " + v));
    }

    private Step transform(EndpointContext ctx, RuleSnapshot r, TransformConfig t, String method, String signature,
                           String inType, String summary, String condition) {
        String args = "current" + ctx.paramArgs();
        String target = payloadName(t.targetPayloadId());
        if (target == null) {
            error(ctx.where(r), "the target payload no longer exists");
            target = inType;
        }
        if (inType.equals("Object")) {
            error(ctx.where(r), "there is no payload to map from at this point");
        }
        String mapperName = mapperNames.claim(ctx.e().name() + Names.pascal(r.name()));
        List<String> mappings = new ArrayList<>();
        Set<String> explicit = new HashSet<>();
        PayloadSnapshot targetPayload = payloads.get(target);
        for (TransformConfig.FieldMapping m : t.mappings()) {
            if (m.target().contains("[]")) {
                error(ctx.where(r), "mapping target " + m.target() + " cannot step into list items");
                continue;
            }
            explicit.add(m.target().split("\\.")[0]);
            if (m.source() != null && !m.source().isBlank()) {
                if (m.source().contains("[]")) {
                    error(ctx.where(r), "mapping source " + m.source() + " is a list of values; use an expression such as sum(...)");
                    continue;
                }
                mappings.add("@Mapping(target = " + Names.literal(m.target()) + ", source = " + Names.literal(m.source()) + ")");
            } else {
                String targetType = targetFieldType(targetPayload, m.target());
                String code = java.value(m.expression(), new JavaExpressions.SimpleScope("source", payloads.get(inType), Map.of()), targetType);
                mappings.add("@Mapping(target = " + Names.literal(m.target()) + ", expression = " + Names.literal("java(" + code + ")") + ")");
            }
        }
        mappings.addAll(ignoredByName(inType, target, explicit));
        mapperMethods.put(mapperName, new MapperMethod(mapperName, inType, target, mappings, summary));
        return new Step("TRANSFORM", r.name(), summary, method, signature, args, inType, target, condition, new TransformData(mapperName));
    }

    /** A MapStruct method that maps fields by name only (ignoring same-named fields of incompatible types). */
    private String autoMapper(String wanted, String source, String target) {
        for (MapperMethod m : mapperMethods.values()) {
            if (m.sourceType().equals(source) && m.targetType().equals(target) && m.comment().startsWith("By name")) {
                return m.name();
            }
        }
        String name = mapperNames.claim(Names.camel(wanted));
        mapperMethods.put(name, new MapperMethod(name, source, target, ignoredByName(source, target, Set.of()),
                "By name: " + source + " → " + target));
        return name;
    }

    /** @Mapping(ignore = true) for target fields whose same-named source field has an incompatible type. */
    private List<String> ignoredByName(String source, String target, Set<String> explicit) {
        PayloadSnapshot s = payloads.get(source);
        PayloadSnapshot t = payloads.get(target);
        List<String> out = new ArrayList<>();
        if (s == null || t == null) return out;
        for (FieldSnapshot tf : t.fields()) {
            if (explicit.contains(tf.name())) continue;
            s.fields().stream().filter(sf -> sf.name().equals(tf.name())).findFirst().ifPresent(sf -> {
                if (!compatible(sf, tf, new HashSet<>())) {
                    out.add("@Mapping(target = " + Names.literal(tf.name()) + ", ignore = true) // " + sf.name() + " has a different type");
                    todos.add("Mapping " + source + " → " + target + ": '" + tf.name() + "' is ignored because the types differ.");
                }
            });
        }
        return out;
    }

    private boolean compatible(FieldSnapshot s, FieldSnapshot t, Set<String> seen) {
        if (s.type() == FieldType.ARRAY && t.type() == FieldType.ARRAY) {
            return elementCompatible(s.itemType(), s.refPayload(), t.itemType(), t.refPayload(), seen);
        }
        if (s.type() == FieldType.ARRAY || t.type() == FieldType.ARRAY) return false;
        return elementCompatible(s.type(), s.refPayload(), t.type(), t.refPayload(), seen);
    }

    private boolean elementCompatible(FieldType st, String sRef, FieldType tt, String tRef, Set<String> seen) {
        if (st == FieldType.OBJECT || tt == FieldType.OBJECT) {
            if (st != tt) return false;
            if (sRef.equals(tRef) || !seen.add(sRef + "→" + tRef)) return true;
            PayloadSnapshot sp = payloads.get(sRef);
            PayloadSnapshot tp = payloads.get(tRef);
            for (FieldSnapshot tf : tp.fields()) {
                for (FieldSnapshot sf : sp.fields()) {
                    if (sf.name().equals(tf.name()) && !compatible(sf, tf, seen)) return false;
                }
            }
            return true;
        }
        if (st == tt) return true;
        Set<FieldType> numbers = EnumSet.of(FieldType.INTEGER, FieldType.LONG, FieldType.NUMBER, FieldType.DECIMAL);
        return numbers.contains(st) && numbers.contains(tt);
    }

    private Step enrich(EndpointContext ctx, RuleSnapshot r, EnrichConfig en, String method, String signature, String inType,
                        String summary, String condition, JavaExpressions.Scope scope, String sub) {
        String args = "current" + ctx.paramArgs();
        ClientInfo client = clientInfos.computeIfAbsent(en.serviceName(), ClientInfo::new);
        client.timeoutMs = Math.max(client.timeoutMs, en.timeoutMs());
        String clientMethod = clientMethodNames.claim(Names.camel(ctx.e().name() + " " + r.name()));
        List<String> uriParams = new ArrayList<>();
        List<String> callArgs = new ArrayList<>();
        Matcher m = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_.\\[\\]]*)}").matcher(en.url());
        StringBuilder uri = new StringBuilder();
        while (m.find()) {
            String paramName = Names.camel(m.group(1));
            uriParams.add(paramName);
            callArgs.add(java.read(m.group(1), scope));
            m.appendReplacement(uri, Matcher.quoteReplacement("{" + paramName + "}"));
        }
        m.appendTail(uri);
        boolean hasBody = en.httpMethod() == HttpMethod.POST || en.httpMethod() == HttpMethod.PUT || en.httpMethod() == HttpMethod.PATCH;
        if (hasBody) {
            callArgs.add("current");
        }
        String resultType = en.responsePayloadId() == null ? "String" : payloadName(en.responsePayloadId());
        client.methods.add(new ClientMethod(clientMethod, en.httpMethod().name(), Names.literal(uri.toString()), uriParams, hasBody, resultType));

        String assignment = en.targetField() == null || en.targetField().isBlank()
                ? "// TODO: use the " + resultType + " returned by " + en.serviceName()
                : assignInto(ctx, r, inType, en.targetField(), resultType, "result");
        return new Step("ENRICH", r.name(), summary, method, signature, args, inType, inType, condition,
                new EnrichData(client.fieldName(), clientMethod, String.join(", ", callArgs), resultType, assignment, en.mode().name(), sub));
    }

    /** Java that stores {@code variable} (of {@code valueType}) into {@code targetField} of the current payload. */
    private String assignInto(EndpointContext ctx, RuleSnapshot r, String inType, String targetField, String valueType, String variable) {
        if (inType.equals("Object")) {
            return "// TODO: there is no payload to put the " + valueType + " into";
        }
        List<JavaExpressions.Segment> segs = JavaExpressions.segments(targetField);
        FieldSnapshot field = resolve(payloads.get(inType), targetField);
        String fieldType = field == null ? null : plainType(field);
        if (segs.stream().anyMatch(JavaExpressions.Segment::intoItems)) {
            error(ctx.where(r), "the " + valueType + " cannot be stored inside list items (" + targetField + ")");
            return "// TODO";
        }
        if (fieldType != null && !fieldType.equals(valueType)) {
            error(ctx.where(r), targetField + " is a " + fieldType + " but the step gives a " + valueType);
            return "// TODO";
        }
        String setter = Names.setter(segs.getLast().name()) + "(" + variable + ")";
        if (segs.size() == 1) {
            return "current." + setter + ";";
        }
        String parent = JavaExpressions.chain("current", segs.subList(0, segs.size() - 1));
        return "var $parent = " + parent + ";\nif ($parent != null) {\n    $parent." + setter + ";\n}";
    }

    private Step conditional(EndpointContext ctx, RuleSnapshot r, ConditionalRouteConfig c, String method, String signature,
                             String inType, String summary, String condition, JavaExpressions.Scope scope) {
        String args = "current" + ctx.paramArgs();
        List<Branch> branches = new ArrayList<>();
        for (ConditionalRouteConfig.Branch b : c.branches()) {
            String branchMethod = ctx.methods().claim(b.name());
            String cond = ctx.route() ? camelPredicate(b.condition(), inType, ctx) : java.condition(b.condition(), scope);
            branches.add(new Branch(branchMethod, b.name(), cond, b.description() == null ? "" : b.description(),
                    "direct:" + ctx.e().name() + "-" + branchMethod));
            todos.add(ctx.e().name() + ": implement branch '" + b.name() + "'" + (b.description() == null ? "" : " (" + b.description() + ")") + ".");
        }
        return new Step("CONDITIONAL_ROUTE", r.name(), summary, method, signature, args, inType, inType, condition,
                new ConditionalData(branches, c.otherwise() == null ? "" : c.otherwise(), "direct:" + ctx.e().name() + "-" + method + "Otherwise"));
    }

    private Step persist(EndpointContext ctx, RuleSnapshot r, PersistConfig p, String method, String signature, String inType,
                         String summary, String condition, JavaExpressions.Scope scope) {
        String args = "current" + ctx.paramArgs();
        EntityInfo entity = entityInfos.get(p.entityName());
        boolean sameType = inType.equals(entity.source);
        String idExpr = p.idField() == null || p.idField().isBlank() ? null
                : JavaExpressions.convert(java.read(p.idField(), scope), entity.id.javaType());
        String outType = inType;
        String assignment = null;
        switch (p.operation()) {
            case SAVE -> {
                if (!sameType) error(ctx.where(r), p.entityName() + " stores " + entity.source + ", not " + inType);
            }
            case FIND_BY_ID -> {
                if (entity.source == null) {
                    error(ctx.where(r), "cannot tell what " + p.entityName() + " holds: add a SAVE rule for it or a success response payload");
                } else if (intoField(p)) {
                    assignment = assignInto(ctx, r, inType, p.targetField(), entity.source, "loaded");
                } else {
                    outType = entity.source;
                }
                if (idExpr == null) error(ctx.where(r), "FIND_BY_ID needs an id field");
            }
            case UPDATE, DELETE -> {
                if (idExpr == null) error(ctx.where(r), p.operation() + " needs an id field");
                if (p.operation() == PersistConfig.Operation.UPDATE && !sameType) {
                    todos.add(ctx.e().name() + ": copy the " + inType + " changes onto " + p.entityName() + " in step '" + r.name() + "'.");
                }
            }
        }
        return new Step("PERSIST", r.name(), summary, method, signature, args, inType, outType, condition,
                new PersistData(Names.camel(p.entityName() + "Store"), p.operation().name(), idExpr == null ? "null" : idExpr,
                        sameType, p.entityName(), entity.source, assignment));
    }

    private Step publish(EndpointContext ctx, RuleSnapshot r, PublishEventConfig p, String method, String signature,
                         String inType, String summary, String condition) {
        String args = "current" + ctx.paramArgs();
        String eventType = p.eventPayloadId() == null ? inType : payloadName(p.eventPayloadId());
        String convert = null;
        if (!eventType.equals(inType)) {
            if (inType.equals("Object")) {
                todos.add(ctx.e().name() + ": build the " + eventType + " to publish in step '" + r.name() + "'.");
            } else {
                convert = autoMapper(inType + "To" + eventType, inType, eventType);
            }
        }
        String key = null;
        if (p.protocol() == MessagingProtocol.KAFKA && p.keyField() != null && !p.keyField().isBlank()) {
            key = JavaExpressions.convert(java.read(p.keyField(), new JavaExpressions.SimpleScope("event", payloads.get(eventType), Map.of())), "String");
        }
        if (ctx.route()) markCamel(p.protocol()); else markSpring(p.protocol());
        return new Step("PUBLISH_EVENT", r.name(), summary, method, signature, args, inType, inType, condition,
                new PublishData(p.protocol().name(), p.destination(), eventType, convert, key == null ? "null" : key,
                        producerUri(p.protocol(), p.destination())));
    }

    private Step split(EndpointContext ctx, RuleSnapshot r, SplitAggregateConfig s, String method, String signature, String inType,
                       String summary, String condition, String sub) {
        String args = "current" + ctx.paramArgs();
        FieldSnapshot list = resolve(payloads.get(inType), s.collectionField());
        String itemType = "Object";
        if (list == null || list.type() != FieldType.ARRAY) {
            error(ctx.where(r), s.collectionField() + " is not a list of " + inType);
        } else {
            itemType = list.itemType() == FieldType.OBJECT ? list.refPayload() : scalarType(list.itemType());
        }
        String listExpr = "Expressions.list(" + JavaExpressions.chain("current", JavaExpressions.segments(s.collectionField())) + ")";
        String sumExpr = s.sumField() == null ? "$it" : JavaExpressions.chain("$it", JavaExpressions.segments(s.sumField()));
        String itemMethod = ctx.methods().claim(method + "Item");
        todos.add(ctx.e().name() + ": implement the per-item processing of step '" + r.name() + "'.");
        return new Step("SPLIT_AGGREGATE", r.name(), summary, method, signature, args, inType, inType, condition,
                new SplitData(listExpr, itemType, itemMethod, s.parallel(), s.aggregation().name(), sumExpr, sub + "-item", method + "Result"));
    }

    // ================================================================== Camel routes

    private Route route(EndpointContext ctx) {
        EndpointSnapshot e = ctx.e();
        String type = ctx.inputType() == null ? "Object" : ctx.inputType();
        List<Step> steps = new ArrayList<>();
        List<OnException> onExceptions = new ArrayList<>();
        DeadLetter deadLetter = null;
        Names.Registry methods = new Names.Registry();
        EndpointContext routeCtx = new EndpointContext(e, ctx.params(), ctx.inputType(), ctx.inputVar(), ctx.returnType(), true, methods);
        // Handlers aimed at one step guard that step only; the rest apply to the whole route.
        Map<String, ErrorHandlingConfig> stepGuards = new HashMap<>();
        Map<String, String> guardNames = new HashMap<>();
        for (RuleSnapshot r : e.rules()) {
            if (r.config() instanceof ErrorHandlingConfig h && h.targetRule() != null) {
                String key = h.targetRule().toLowerCase(Locale.ROOT);
                if (stepGuards.putIfAbsent(key, h) == null) {
                    guardNames.put(key, r.name());
                } else {
                    todos.add(e.name() + ": step '" + h.targetRule() + "' has more than one error handler; only the first is generated.");
                }
            }
        }
        for (RuleSnapshot r : e.rules()) {
            if (r.config() instanceof ErrorHandlingConfig h && h.targetRule() != null) {
                continue;
            }
            if (r.config() instanceof ErrorHandlingConfig h) {
                if (h.deadLetterDestination() != null && e.kind() == EndpointKind.ASYNC) {
                    markCamel(e.protocol());
                    int attempts = h.retry() == null ? 1 : h.retry().maxAttempts();
                    if (deadLetter == null) {
                        deadLetter = new DeadLetter(producerUri(e.protocol(), h.deadLetterDestination()), attempts - 1,
                                h.retry() == null ? 0 : h.retry().backoffMs(), h.retry() == null ? 1 : h.retry().multiplier(),
                                summaryOf(r, null));
                    } else {
                        todos.add(e.name() + ": only the first dead-letter destination is used ('" + r.name() + "' is ignored).");
                    }
                    continue;
                }
                List<String> classes = h.on().contains(ErrorHandlingConfig.ErrorCategory.ANY) ? List.of("RuntimeException")
                        : h.on().stream().map(CodeModelBuilder::exceptionClass).distinct().toList();
                ErrorHandlingConfig.Fallback fb = h.fallback();
                String fallback = fb == null || fb.type() == null ? null : fb.type().name();
                String defaultType = e.isRest() && !ctx.returnType().equals("Void") ? ctx.returnType() : null;
                onExceptions.add(new OnException(classes.stream().map(c -> c + ".class").collect(Collectors.joining(", ")),
                        summaryOf(r, null),
                        h.retry() != null, h.retry() == null ? 0 : h.retry().maxAttempts() - 1,
                        h.retry() == null ? 0 : h.retry().backoffMs(), h.retry() == null ? 1 : h.retry().multiplier(),
                        fallback, fb == null || fb.httpStatus() == null ? 503 : fb.httpStatus(),
                        fb == null || fb.message() == null ? "The request could not be completed" : fb.message(), defaultType));
                continue;
            }
            JavaExpressions.Scope scope = new JavaExpressions.SimpleScope("current", payloads.get(type), routeCtx.paramCode());
            Step step = step(routeCtx, r, methods.claim(Names.camel(r.name())), type, scope);
            if (step.condition() != null && !step.outType().equals(type)) {
                error(routeCtx.where(r), "a step that changes the payload (" + type + " → " + step.outType() + ") cannot have a condition");
            }
            ErrorHandlingConfig guard = stepGuards.get(r.name().toLowerCase(Locale.ROOT));
            if (guard != null) {
                step = step.withGuard(guard(routeCtx, step, guard, guardNames.get(r.name().toLowerCase(Locale.ROOT)), type));
            }
            steps.add(step);
            type = step.outType();
        }
        String toUri = null;
        if (e.kind() == EndpointKind.ASYNC && e.operation() == AsyncOperation.SEND) {
            toUri = producerUri(e.protocol(), e.channel());
            markCamel(e.protocol());
            if (!type.equals(e.messagePayload()) && !type.equals("Object")) {
                todos.add(e.name() + ": the route ends with a " + type + " but sends " + e.messagePayload() + "; convert it before sending.");
            }
        }
        if (e.isRest() && !ctx.returnType().equals("Void") && !type.equals(ctx.returnType())) {
            String converter = type.equals("Object") ? null : autoMapper(e.name() + "To" + ctx.returnType(), type, ctx.returnType());
            steps.add(new Step("RESPOND", "Build the response", "Convert the " + type + " into the " + ctx.returnType() + " response.",
                    "respond", "", "", type, ctx.returnType(), null, new TransformData(converter)));
            if (converter == null) {
                todos.add(e.name() + ": build the " + ctx.returnType() + " response (no rule produces it).");
            }
        }
        boolean receive = e.kind() == EndpointKind.ASYNC && e.operation() == AsyncOperation.RECEIVE;
        String fromUri = receive ? consumerUri(e.protocol(), e.channel()) : "direct:" + e.name();
        if (receive) markCamel(e.protocol());

        List<Dependency> deps = new ArrayList<>();
        deps.add(new Dependency("JsonMapper", "json"));
        if (steps.stream().anyMatch(s -> s.data() instanceof TransformData t && t.mapperMethod() != null)
                || steps.stream().anyMatch(s -> s.data() instanceof PublishData p && p.convertMethod() != null)) {
            deps.add(new Dependency("DtoMapper", "dtoMapper"));
        }
        steps.stream().map(Step::data).filter(d -> d instanceof PersistData).map(d -> (PersistData) d)
                .map(p -> new Dependency(p.entityClass() + "Store", p.storeField())).distinct().forEach(deps::add);
        steps.stream().map(Step::data).filter(d -> d instanceof EnrichData).map(d -> (EnrichData) d)
                .map(en -> new Dependency(Names.capitalize(en.clientField()), en.clientField())).distinct().forEach(deps::add);
        return new Route(Names.pascal(e.name()) + "Route", e.name(), e.name(), fromUri, ctx.inputType(), receive, toUri,
                steps, onExceptions, deadLetter, deps);
    }

    private Guard guard(EndpointContext ctx, Step step, ErrorHandlingConfig h, String handlerName, String inType) {
        EndpointSnapshot e = ctx.e();
        List<String> classes = h.on().contains(ErrorHandlingConfig.ErrorCategory.ANY) ? List.of("RuntimeException")
                : h.on().stream().map(CodeModelBuilder::exceptionClass).distinct().toList();
        ErrorHandlingConfig.Fallback fb = h.fallback();
        String deadLetter = h.deadLetterDestination() != null && e.kind() == EndpointKind.ASYNC
                ? producerUri(e.protocol(), h.deadLetterDestination()) : null;
        if (deadLetter != null) markCamel(e.protocol());
        String id = e.name() + "-" + step.methodName() + "-guarded";
        return new Guard("direct:" + id, id, handlerName + ": " + h.summary(new SummaryContext(this::payloadName, null)),
                h.retry() != null, h.retry() == null ? 0 : h.retry().maxAttempts() - 1,
                h.retry() == null ? 0 : h.retry().backoffMs(), h.retry() == null ? 1 : h.retry().multiplier(),
                classes.stream().map(c -> c + ".class").collect(Collectors.joining(", ")),
                fb == null || fb.type() == null ? null : fb.type().name(),
                fb == null || fb.httpStatus() == null ? 503 : fb.httpStatus(),
                fb == null || fb.message() == null ? "The request could not be completed" : fb.message(),
                deadLetter, step.methodName() + "Input");
    }

    /** A Camel predicate: Simple when it can express the condition, otherwise a Java lambda. */
    private String camelPredicate(String condition, String inType, EndpointContext ctx) {
        Set<String> paramNames = ctx.params().stream().map(Param::name).collect(Collectors.toSet());
        String simple = SimpleExpressions.condition(condition, paramNames::contains, !inType.equals("Object"));
        if (simple != null) {
            return "simple(" + Names.literal(simple) + ")";
        }
        String body = java.condition(condition, new JavaExpressions.SimpleScope("current", payloads.get(inType), ctx.paramCode()));
        return "exchange -> {\n    " + inType + " current = exchange.getMessage().getBody(" + inType + ".class);\n    return " + body + ";\n}";
    }

    private static String producerUri(MessagingProtocol protocol, String destination) {
        return switch (protocol) {
            case KAFKA -> "kafka:" + destination + "?brokers={{spring.kafka.bootstrap-servers:localhost:9092}}";
            case JMS -> "jms:queue:" + destination;
            case AMQP -> "spring-rabbitmq:" + destination;
        };
    }

    private static String consumerUri(MessagingProtocol protocol, String destination) {
        return switch (protocol) {
            case KAFKA -> producerUri(protocol, destination) + "&groupId={{spring.application.name}}";
            case JMS -> "jms:queue:" + destination;
            case AMQP -> "spring-rabbitmq:" + destination + "?queues=" + destination + "&autoDeclare=true";
        };
    }

    private void markSpring(MessagingProtocol p) {
        switch (p) {
            case KAFKA -> springKafka = true;
            case JMS -> springJms = true;
            case AMQP -> springAmqp = true;
        }
    }

    private void markCamel(MessagingProtocol p) {
        switch (p) {
            case KAFKA -> camelKafka = true;
            case JMS -> camelJms = true;
            case AMQP -> camelAmqp = true;
        }
    }

    // ================================================================== groups, clients, checks

    private Group group(String name, List<CodeModel.Endpoint> endpoints, List<Handler> handlers) {
        List<CodeModel.Endpoint> rest = endpoints.stream().filter(CodeModel.Endpoint::isRest).toList();
        List<CodeModel.Endpoint> impl = endpoints.stream().filter(e -> !e.isRoute()).toList();
        List<CodeModel.Endpoint> listeners = impl.stream().filter(e -> !e.isRest() && e.isReceive()).toList();
        List<CodeModel.Endpoint> gateway = endpoints.stream().filter(e -> e.isRoute() && e.isSend()).toList();
        List<Dependency> deps = new ArrayList<>();
        deps.add(new Dependency("Validator", "validator"));
        deps.add(new Dependency("JsonMapper", "json"));
        List<Step> steps = impl.stream().flatMap(e -> e.impl().steps().stream()).toList();
        boolean usesMapper = steps.stream().anyMatch(s -> s.data() instanceof TransformData)
                || steps.stream().anyMatch(s -> s.data() instanceof PublishData p && p.convertMethod() != null)
                || impl.stream().anyMatch(e -> e.impl().lines().stream().anyMatch(l -> l.contains("dtoMapper.")));
        if (usesMapper) deps.add(new Dependency("DtoMapper", "dtoMapper"));
        boolean usesMessaging = steps.stream().anyMatch(s -> s.data() instanceof PublishData)
                || impl.stream().anyMatch(e -> e.impl().lines().stream().anyMatch(l -> l.contains("messaging."))
                || (e.impl().wrappedChainCall() != null && e.impl().wrappedChainCall().contains("messaging.")));
        if (usesMessaging) deps.add(new Dependency("MessagingGateway", "messaging"));
        steps.stream().map(Step::data).filter(d -> d instanceof PersistData).map(d -> (PersistData) d)
                .map(p -> new Dependency(p.entityClass() + "Store", p.storeField())).distinct().forEach(deps::add);
        steps.stream().map(Step::data).filter(d -> d instanceof EnrichData).map(d -> (EnrichData) d)
                .map(en -> new Dependency(Names.capitalize(en.clientField()), en.clientField())).distinct().forEach(deps::add);
        return new Group(name, name + "Controller", name + "Service", name + "ServiceImpl", name + "Listener", name + "Gateway",
                name + "ServiceImplTest", rest, impl, listeners, gateway, deps, List.copyOf(handlers));
    }

    private final class ClientInfo {
        final String serviceName;
        int timeoutMs;
        final List<ClientMethod> methods = new ArrayList<>();

        ClientInfo(String serviceName) {
            this.serviceName = serviceName;
        }

        String className() {
            return Names.pascal(serviceName) + "Client";
        }

        String fieldName() {
            return Names.camel(className());
        }

        Client toModel() {
            return new Client(className(), fieldName(), serviceName, "clients." + serviceName, timeoutMs, List.copyOf(methods));
        }
    }

    private void checkClassNames(List<Dto> dtos, List<Group> groups, List<Route> routes, List<CodeModel.Entity> entities,
                                 List<Client> clients, CodeModel.Project info) {
        Map<String, String> generated = new HashMap<>();
        Set<String> dtoNames = dtos.stream().map(Dto::className).collect(Collectors.toSet());
        groups.forEach(g -> List.of(g.controllerClass(), g.serviceClass(), g.serviceImplClass(), g.listenerClass(), g.gatewayClass())
                .forEach(c -> generated.put(c, "group " + g.name())));
        routes.forEach(r -> generated.put(r.className(), "route " + r.routeId()));
        entities.forEach(e -> {
            generated.put(e.className(), "entity");
            generated.put(e.repositoryClass(), "repository");
            generated.put(e.storeClass(), "store");
        });
        clients.forEach(c -> generated.put(c.className(), "client"));
        generated.put(info.appClass(), "application");
        for (String fixed : List.of("DtoMapper", "MessagingGateway", "Expressions", "ApiExceptionHandler", "RuleViolationException",
                "NotFoundException", "DownstreamUnavailableException", "DownstreamTimeoutException", "PersistenceFailedException",
                "MessagingFailedException", "CamelResponses")) {
            generated.put(fixed, "support class");
        }
        for (String dto : dtoNames) {
            if (generated.containsKey(dto)) {
                error("payload " + dto, "'" + dto + "' is also the name of a generated " + generated.get(dto) + " class; rename the payload");
            }
        }
    }

    // ================================================================== helpers

    private String inputType(EndpointSnapshot e) {
        return e.isRest() ? e.requestPayload() : e.messagePayload();
    }

    private String successPayload(EndpointSnapshot e) {
        return e.responses().stream().filter(r -> r.statusCode().startsWith("2") && r.payload() != null)
                .min(Comparator.comparing(ResponseSnapshot::statusCode)).map(ResponseSnapshot::payload).orElse(null);
    }

    private String payloadName(UUID id) {
        if (id == null) return null;
        return project.payloads().stream().filter(p -> p.id().equals(id)).map(PayloadSnapshot::name).findFirst().orElse(null);
    }

    private FieldSnapshot resolve(PayloadSnapshot root, String path) {
        PayloadSnapshot current = root;
        FieldSnapshot field = null;
        for (JavaExpressions.Segment s : JavaExpressions.segments(path)) {
            if (current == null) return null;
            field = current.fields().stream().filter(f -> f.name().equals(s.name())).findFirst().orElse(null);
            if (field == null) return null;
            current = field.refPayload() == null ? null : payloads.get(field.refPayload());
        }
        return field;
    }

    private String targetFieldType(PayloadSnapshot target, String path) {
        FieldSnapshot f = resolve(target, path);
        return f == null ? "Object" : plainType(f);
    }

    private String summaryOf(RuleSnapshot r, String inType) {
        String text = r.config().summary(new SummaryContext(this::payloadName, inType == null || inType.equals("Object") ? null : inType));
        return (r.condition() == null ? "" : "Only if " + r.condition() + ": ") + text;
    }

    private String send(MessagingProtocol protocol, String destination, String key, String payload) {
        return switch (protocol) {
            case KAFKA -> "messaging.sendKafka(" + Names.literal(destination) + ", " + key + ", " + payload + ")";
            case JMS -> "messaging.sendJms(" + Names.literal(destination) + ", " + payload + ")";
            case AMQP -> "messaging.sendAmqp(" + Names.literal(destination) + ", " + payload + ")";
        };
    }

    private void error(String where, String message) {
        problems.add(Problem.error(SOURCE, where, message));
    }
}
