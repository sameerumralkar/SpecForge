package dev.specbuilder.generator.code;

import java.util.List;
import java.util.Map;

/**
 * Everything the code templates need, with names resolved, types computed and expressions already
 * translated. Templates decide the shape of the code; this model supplies the pieces. All types
 * are public records so FreeMarker can read them.
 */
public record CodeModel(
        Project project,
        Features features,
        List<Dto> dtos,
        List<Group> groups,
        List<Route> routes,
        List<Entity> entities,
        List<Client> clients,
        List<MapperMethod> mapperMethods,
        List<String> todos) {

    public record Project(String name, String description, String groupId, String artifactId, String version,
                          String basePackage, String packagePath, String appClass) {
    }

    /** Which libraries and generated support classes the project needs. */
    public record Features(boolean jpa, boolean mapstruct, boolean retry, boolean clients, boolean camel,
                           boolean springKafka, boolean springJms, boolean springAmqp,
                           boolean camelKafka, boolean camelJms, boolean camelAmqp,
                           boolean messaging, boolean kafka, boolean jms, boolean amqp) {
    }

    // ---------------------------------------------------------------- DTOs

    public record Dto(String className, String description, List<DtoField> fields) {
    }

    /** {@code annotations} are complete Bean Validation annotations, e.g. {@code @Size(min = 2)}. */
    /** {@code javaType} may carry type-use annotations (List<@Size(max = 5) String>); {@code plainType} never does. */
    public record DtoField(String name, String javaType, String plainType, String getter, String setter, List<String> annotations,
                           String description) {
    }

    // ---------------------------------------------------------------- endpoints

    /**
     * A REST or async operation. For REST: {@code mappingAnnotation} (e.g. PostMapping), {@code path},
     * {@code status} and {@code returnType} ("Void" when there is no body). For async: channel data.
     */
    public record Endpoint(
            String name, String kind, String style, String summary, String comment,
            String mappingAnnotation, String path, int status, String returnType, String bodyType, String bodyVar,
            List<Param> params,
            String channel, String protocol, String operation, String messageType, String messageVar, String listenerAnnotation,
            String serviceSignature, String serviceCallArgs, String controllerArgs,
            ImplChain impl, Route route) {

        public boolean isRest() { return "REST".equals(kind); }
        public boolean isReceive() { return "RECEIVE".equals(operation); }
        public boolean isSend() { return "SEND".equals(operation); }
        public boolean isRoute() { return route != null; }
        public boolean hasReturn() { return !"Void".equals(returnType); }
    }

    /**
     * A path/query/header parameter or message header. {@code springAnnotation} is the full
     * controller annotation; {@code routeAccess} reads it from a Camel header.
     */
    public record Param(String name, String javaName, String javaType, String location, boolean required,
                        String springAnnotation, String listenerAnnotation, String listenerConversion) {
    }

    /** Implementation-class endpoints grouped by their first tag: one controller, service, listener per group. */
    public record Group(String name, String controllerClass, String serviceClass, String serviceImplClass,
                        String listenerClass, String gatewayClass, String testClass,
                        List<Endpoint> restEndpoints, List<Endpoint> implEndpoints, List<Endpoint> listenerEndpoints,
                        List<Endpoint> gatewayEndpoints, List<Dependency> implDependencies,
                        List<Handler> handlers) {

        public boolean hasController() { return !restEndpoints.isEmpty(); }
        public boolean hasService() { return !implEndpoints.isEmpty(); }
        public boolean hasListener() { return !listenerEndpoints.isEmpty(); }
        public boolean hasGateway() { return !gatewayEndpoints.isEmpty(); }
        public boolean usesCamel() { return hasGateway() || restEndpoints.stream().anyMatch(Endpoint::isRoute); }
        public List<String> endpointNames() { return implEndpoints.stream().map(Endpoint::name).toList(); }
    }

    public record Dependency(String type, String name) {
    }

    // ---------------------------------------------------------------- implementation-class chains

    /**
     * The service method of one endpoint. {@code lines} are the statements of the chain method;
     * {@code steps} the private methods it calls. When chain-level error handlers exist, the public
     * method calls {@code wrappedChainCall}.
     */
    public record ImplChain(String chainMethod, String chainSignature, String chainArgs, String chainReturnType,
                            List<String> lines, String wrappedChainCall, List<Step> steps) {
    }

    /** One rule as a private method. {@code data} depends on {@code type}: see the records below. */
    /** {@code args} passes the step's parameters on ("current, orderId"), e.g. to branch methods. */
    public record Step(String type, String name, String summary, String methodName, String signature, String args,
                       String inType, String outType, String condition, Object data, Guard guard) {

        public Step(String type, String name, String summary, String methodName, String signature, String args,
                    String inType, String outType, String condition, Object data) {
            this(type, name, summary, methodName, signature, args, inType, outType, condition, data, null);
        }

        public Step withGuard(Guard g) {
            return new Step(type, name, summary, methodName, signature, args, inType, outType, condition, data, g);
        }
    }

    /**
     * Error handling aimed at one route step: the step runs in its own sub-route ({@code uri}) whose
     * error handler retries it; the main route calls it inside doTry/doCatch for the fallback.
     * {@code fallback} is ERROR_RESPONSE, DEFAULT_RESPONSE or null.
     */
    public record Guard(String uri, String routeId, String summary, boolean retry, int maxRedeliveries, long delayMs,
                        double multiplier, String classes, String fallback, int status, String message,
                        String deadLetterUri, String inputProperty) {

        public boolean catches() {
            return fallback != null || deadLetterUri != null;
        }
    }

    public record Check(String failsWhen, String message, String comment) {
    }

    public record ValidateData(List<Check> checks, int status, String beanValidatorUri) {
    }

    public record TransformData(String mapperMethod) {
    }

    public record EnrichData(String clientField, String clientMethod, String callArgs, String resultType, String assignment,
                             String mode, String subRouteUri) {
    }

    public record Branch(String methodName, String name, String condition, String description, String subRouteUri) {
    }

    public record ConditionalData(List<Branch> branches, String otherwise, String otherwiseUri) {
    }

    /** {@code assignment} (FIND_BY_ID into a field only) stores the {@code loaded} record into the current payload. */
    public record PersistData(String storeField, String operation, String idExpr, boolean sameType, String entityClass,
                              String sourceType, String assignment) {
    }

    public record PublishData(String protocol, String destination, String eventType, String convertMethod, String keyExpr,
                              String toUri) {
    }

    public record SplitData(String listExpr, String itemType, String itemMethod, boolean parallel, String aggregation,
                            String sumExpr, String itemUri, String resultProperty) {
    }

    /** A retry/fallback wrapper method. {@code catchClasses} is a multi-catch list, e.g. "A | B". */
    public record Handler(String methodName, String name, String summary, boolean retry, int maxAttempts, long backoffMs,
                          double multiplier, List<String> retryClasses, String catchClasses, boolean hasCatch) {
    }

    // ---------------------------------------------------------------- Camel routes

    /**
     * One RouteBuilder per processing-route endpoint. {@code fromUri} is "direct:name" for REST and
     * SEND endpoints (called from the controller or gateway), or the broker URI for RECEIVE.
     * Steps use the same records as implementation classes; in a route, conditions are Camel
     * predicates and code reads parameters from message headers.
     */
    public record Route(String className, String routeId, String endpointName, String fromUri, String inputType,
                        boolean unmarshal, String toUri, List<Step> steps, List<OnException> onExceptions,
                        DeadLetter deadLetter, List<Dependency> dependencies) {

        public List<String> endpointNames() { return List.of(endpointName); }
    }

    public record DeadLetter(String uri, int maxRedeliveries, long delayMs, double multiplier, String summary) {
    }

    /** An onException clause. {@code fallback} is ERROR_RESPONSE, DEFAULT_RESPONSE or null. */
    public record OnException(String classes, String summary, boolean retry, int maxRedeliveries, long delayMs,
                              double multiplier, String fallback, int status, String message, String defaultType) {
    }

    // ---------------------------------------------------------------- persistence, clients, mapping

    /**
     * A JPA entity. {@code idStrategy}: UUID_IF_MISSING (the DTO's UUID id, assigned when empty),
     * IDENTITY (database-generated number), GENERATED_UUID (a UUID id the DTO doesn't have) or NONE.
     */
    public record Entity(String className, String tableName, String repositoryClass, String storeClass, String storeField,
                         String sourceType, Column id, String idStrategy, boolean idFromDto, List<Column> columns,
                         List<String> skipped) {

        public boolean hasSource() { return sourceType != null; }
    }

    public record Column(String name, String javaType, String getter, String setter) {
    }

    public record Client(String className, String fieldName, String serviceName, String propertyPrefix, int timeoutMs,
                         List<ClientMethod> methods) {
    }

    public record ClientMethod(String name, String httpMethod, String uri, List<String> uriParams, boolean hasBody,
                               String responseType) {
    }

    /** A MapStruct method. {@code mappings} are complete @Mapping annotations. */
    public record MapperMethod(String name, String sourceType, String targetType, List<String> mappings, String comment) {
    }

    /** Convenience for templates: name → value maps are awkward in FreeMarker, so lists are used. */
    public static <T> List<T> listOf(Map<?, T> map) {
        return List.copyOf(map.values());
    }
}
