package dev.specbuilder.seed;

import dev.specbuilder.api.dto.*;
import dev.specbuilder.domain.*;
import dev.specbuilder.domain.rule.RuleType;
import dev.specbuilder.repository.ProjectRepository;
import dev.specbuilder.service.EndpointService;
import dev.specbuilder.service.ErrorCodeService;
import dev.specbuilder.service.FieldDefinitionService;
import dev.specbuilder.service.CapabilityService;
import dev.specbuilder.service.PayloadService;
import dev.specbuilder.service.ProjectService;
import dev.specbuilder.service.RuleService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the "Order Service" demo project through the normal services, so it passes the same
 * validation as anything a user creates.
 */
@Component
public class SampleProjects {

    public static final String ORDER_SERVICE = "Order Service";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ProjectRepository projectRepository;
    private final ProjectService projects;
    private final PayloadService payloads;
    private final EndpointService endpoints;
    private final RuleService rules;
    private final ErrorCodeService errorCodes;
    private final FieldDefinitionService fieldInventory;
    private final CapabilityService capabilities;

    public SampleProjects(ProjectRepository projectRepository, ProjectService projects, PayloadService payloads,
                          EndpointService endpoints, RuleService rules, ErrorCodeService errorCodes,
                          FieldDefinitionService fieldInventory, CapabilityService capabilities) {
        this.capabilities = capabilities;
        this.errorCodes = errorCodes;
        this.fieldInventory = fieldInventory;
        this.projectRepository = projectRepository;
        this.projects = projects;
        this.payloads = payloads;
        this.endpoints = endpoints;
        this.rules = rules;
    }

    @Transactional
    public ProjectView createOrderService() {
        String name = ORDER_SERVICE;
        for (int n = 2; projectRepository.existsByNameIgnoreCase(name); n++) {
            name = ORDER_SERVICE + " " + n;
        }
        UUID p = projects.create(new ProjectRequest(name, "com.example.orders", "1.0.0",
                "Sample project: takes orders over REST, checks the customer, stores the order and announces it on Kafka; "
                        + "listens for payment results to update the order.",
                ImplementationStyle.IMPLEMENTATION_CLASS, false)).id();

        // The field inventory: fields that recur across payloads, ready to drag into new objects.
        for (FieldDto definition : List.of(
                field("id", FieldType.UUID, true, f -> f.description = "Identifier assigned by the service"),
                field("customerId", FieldType.UUID, true, f -> f.description = "The customer's id in customer-service"),
                field("email", FieldType.STRING, true, f -> f.format = "email"),
                field("country", FieldType.STRING, true, f -> {
                    f.minLength = 2;
                    f.maxLength = 2;
                    f.description = "ISO 3166-1 alpha-2 country code";
                }),
                field("sku", FieldType.STRING, true, f -> f.pattern = "[A-Z0-9-]{4,20}"),
                field("quantity", FieldType.INTEGER, true, f -> {
                    f.minimum = 1.0;
                    f.maximum = 1000.0;
                }),
                field("amount", FieldType.DECIMAL, true, f -> f.minimum = 0.0),
                field("createdAt", FieldType.DATE_TIME, true, f -> f.description = "When the record was created (UTC)"))) {
            fieldInventory.create(p, definition);
        }

        UUID address = object(p, "Address", "A postal address.",
                "{\"street\":\"1 Main St\",\"city\":\"Springfield\",\"postalCode\":\"12345\",\"country\":\"US\"}",
                str("street", true), str("city", true),
                field("postalCode", FieldType.STRING, true, f -> f.pattern = "[A-Z0-9 -]{3,10}"),
                field("country", FieldType.STRING, true, f -> {
                    f.minLength = 2;
                    f.maxLength = 2;
                    f.description = "ISO 3166-1 alpha-2 country code";
                }));
        UUID customer = object(p, "Customer", "A customer as returned by customer-service.", null,
                field("id", FieldType.UUID, true, f -> { }),
                str("name", true),
                field("email", FieldType.STRING, true, f -> f.format = "email"),
                field("tier", FieldType.STRING, false, f -> f.enumValues = List.of("STANDARD", "GOLD", "PLATINUM")),
                ref("shippingAddress", address, false));
        UUID item = object(p, "OrderItem", "One line of an order.", null,
                field("sku", FieldType.STRING, true, f -> f.pattern = "[A-Z0-9-]{4,20}"),
                field("quantity", FieldType.INTEGER, true, f -> {
                    f.minimum = 1.0;
                    f.maximum = 1000.0;
                }),
                field("unitPrice", FieldType.DECIMAL, true, f -> f.minimum = 0.0));
        UUID createRequest = payload(p, "CreateOrderRequest", "What a client sends to place an order.",
                "{\"customerId\":\"3f1c2a9e-8d5b-4c7a-9e2f-1b6d4a8c0e57\",\"items\":[{\"sku\":\"BOOK-0001\",\"quantity\":2,\"unitPrice\":12.5}]}",
                field("customerId", FieldType.UUID, true, f -> { }),
                list("items", item, true, 1),
                ref("shippingAddress", address, false),
                field("notes", FieldType.STRING, false, f -> f.maxLength = 500));
        UUID order = object(p, "Order", "A stored order.", null,
                field("id", FieldType.UUID, true, f -> { }),
                field("customerId", FieldType.UUID, true, f -> { }),
                ref("customer", customer, false),
                list("items", item, true, 1),
                ref("shippingAddress", address, false),
                field("status", FieldType.STRING, true, f -> f.enumValues = List.of("PENDING", "CONFIRMED", "PAID", "SHIPPED", "CANCELLED")),
                field("total", FieldType.DECIMAL, true, f -> f.minimum = 0.0),
                field("createdAt", FieldType.DATE_TIME, true, f -> { }));
        UUID orderPage = payload(p, "OrderPage", "A page of orders.", null,
                list("orders", order, true, null),
                field("totalCount", FieldType.LONG, true, f -> { }));
        UUID orderError = payload(p, "OrderError", "Error body returned by every endpoint.", null,
                str("code", true), str("message", true),
                field("details", FieldType.ARRAY, false, f -> f.itemType = FieldType.STRING));
        UUID created = payload(p, "OrderCreatedEvent", "Published once an order is stored.", null,
                field("orderId", FieldType.UUID, true, f -> { }),
                field("customerId", FieldType.UUID, true, f -> { }),
                field("total", FieldType.DECIMAL, true, f -> { }),
                field("itemCount", FieldType.INTEGER, true, f -> { }),
                field("occurredAt", FieldType.DATE_TIME, true, f -> { }));
        UUID payment = payload(p, "PaymentCompletedEvent", "Sent by the payment service when a payment finishes.", null,
                field("paymentId", FieldType.UUID, true, f -> { }),
                field("orderId", FieldType.UUID, true, f -> { }),
                field("amount", FieldType.DECIMAL, true, f -> { }),
                field("status", FieldType.STRING, true, f -> f.enumValues = List.of("SUCCEEDED", "FAILED")),
                field("completedAt", FieldType.DATE_TIME, true, f -> { }));

        // ---- REST: create order ----
        UUID createOrder = rest(p, "createOrder", "Place an order", HttpMethod.POST, "/orders", null, createRequest,
                List.of(response("201", "Order created", order), response("400", "Invalid order", orderError),
                        response("503", "A downstream service is unavailable", orderError)));
        rule(p, createOrder, RuleType.VALIDATE, "Validate order", null, Map.of(
                "checks", List.of(
                        check("customerId", "REQUIRED", null),
                        check("items", "MIN_LENGTH", "1"),
                        check("items[].quantity", "MIN", "1"),
                        check("items[].sku", "PATTERN", "[A-Z0-9-]{4,20}")),
                "failureStatus", 400));
        rule(p, createOrder, RuleType.TRANSFORM, "Map to order", null, Map.of(
                "targetPayloadId", order.toString(),
                "mappings", List.of(
                        Map.of("target", "id", "expression", "randomUUID()"),
                        Map.of("target", "status", "expression", "'PENDING'"),
                        Map.of("target", "total", "expression", "sum(items[].quantity * items[].unitPrice)"),
                        Map.of("target", "createdAt", "expression", "now()"))));
        UUID enrichCustomer = rule(p, createOrder, RuleType.ENRICH, "Enrich customer", null, Map.of(
                "serviceName", "customer-service", "httpMethod", "GET", "url", "/customers/{customerId}",
                "responsePayloadId", customer.toString(), "targetField", "customer", "timeoutMs", 2000));
        UUID saveOrder = rule(p, createOrder, RuleType.PERSIST, "Save order", null, Map.of("entityName", "OrderEntity", "operation", "SAVE"));
        UUID publishCreated = rule(p, createOrder, RuleType.PUBLISH_EVENT, "Publish OrderCreated", null, Map.of(
                "protocol", "KAFKA", "destination", "orders.created", "eventPayloadId", created.toString(), "keyField", "orderId"));
        UUID resilience = rule(p, createOrder, RuleType.ERROR_HANDLING, "Customer service resilience", null, Map.of(
                "on", List.of("DOWNSTREAM_UNAVAILABLE", "TIMEOUT"),
                "targetRule", "Enrich customer",
                "retry", Map.of("maxAttempts", 3, "backoffMs", 500, "multiplier", 2.0),
                "fallback", Map.of("type", "ERROR_RESPONSE", "httpStatus", 503, "message", "Customer service unavailable")));

        // ---- REST: read orders ----
        UUID getOrder = rest(p, "getOrder", "Get one order", HttpMethod.GET, "/orders/{orderId}",
                List.of(new ParameterDto("orderId", ParameterLocation.PATH, FieldType.UUID, true, "The order id")), null,
                List.of(response("200", "The order", order), response("404", "No such order", orderError)));
        UUID loadOrder = rule(p, getOrder, RuleType.PERSIST, "Load order", null,
                Map.of("entityName", "OrderEntity", "operation", "FIND_BY_ID", "idField", "orderId"));
        rest(p, "listOrders", "List orders", HttpMethod.GET, "/orders",
                List.of(new ParameterDto("status", ParameterLocation.QUERY, FieldType.STRING, false, "Only orders with this status"),
                        new ParameterDto("limit", ParameterLocation.QUERY, FieldType.INTEGER, false, "Page size, default 20")),
                null, List.of(response("200", "A page of orders", orderPage)));

        // ---- Async ----
        endpoints.create(p, new EndpointRequest(EndpointKind.ASYNC, "orderCreated", "Announces new orders",
                "Other services subscribe to this topic to react to new orders.", List.of("Events"), null, List.of(),
                null, null, null, null, null, "orders.created", AsyncOperation.SEND, MessagingProtocol.KAFKA, created));
        UUID paymentCompleted = endpoints.create(p, new EndpointRequest(EndpointKind.ASYNC, "handlePaymentCompleted",
                "Update the order when a payment finishes", null, List.of("Payments"), ImplementationStyle.PROCESSING_ROUTE,
                List.of(new ParameterDto("correlationId", ParameterLocation.HEADER, FieldType.STRING, false, "Trace id from the payment service")),
                null, null, null, null, null, "payments.completed", AsyncOperation.RECEIVE, MessagingProtocol.KAFKA, payment)).id();
        rule(p, paymentCompleted, RuleType.CONDITIONAL_ROUTE, "Route by payment status", null, Map.of(
                "branches", List.of(
                        Map.of("name", "paymentSucceeded", "condition", "status == 'SUCCEEDED'", "description", "mark the order PAID"),
                        Map.of("name", "paymentFailed", "condition", "status == 'FAILED'", "description", "cancel the order")),
                "otherwise", "log and ignore the message"));
        UUID updateStatus = rule(p, paymentCompleted, RuleType.PERSIST, "Update order status", null,
                Map.of("entityName", "OrderEntity", "operation", "UPDATE", "idField", "orderId"));
        rule(p, paymentCompleted, RuleType.ERROR_HANDLING, "Dead-letter failed payments", null, Map.of(
                "on", List.of("ANY"),
                "retry", Map.of("maxAttempts", 3, "backoffMs", 1000, "multiplier", 2.0),
                "deadLetterDestination", "payments.completed.DLT"));

        // ---- Error codes, linked to the fields and steps that fail with them ----
        code(p, "ORD-001", ErrorCategory.VALIDATION, null, "customerId is required",
                ErrorLink.field(createRequest, "customerId"));
        code(p, "ORD-002", ErrorCategory.VALIDATION, null, "An order needs at least one item",
                ErrorLink.field(createRequest, "items"));
        code(p, "ORD-003", ErrorCategory.VALIDATION, null, "Each item's quantity must be from 1 to 1000",
                ErrorLink.field(item, "quantity"));
        code(p, "ORD-004", ErrorCategory.VALIDATION, null, "A SKU is 4 to 20 upper-case letters, digits or dashes",
                ErrorLink.field(item, "sku"));
        code(p, "ORD-010", ErrorCategory.DOWNSTREAM, null, "Customer service unavailable",
                ErrorLink.rule(enrichCustomer), ErrorLink.rule(resilience));
        code(p, "ORD-011", ErrorCategory.NOT_FOUND, null, "No order with this id", ErrorLink.rule(loadOrder));
        code(p, "ORD-020", ErrorCategory.PERSISTENCE, null, "The order could not be saved",
                ErrorLink.rule(saveOrder), ErrorLink.rule(updateStatus));
        code(p, "ORD-021", ErrorCategory.MESSAGING, null, "OrderCreated could not be published", ErrorLink.rule(publishCreated));

        // The capability model: where each API sits in what the business does.
        Map<String, UUID> api = new HashMap<>();
        endpoints.list(p).forEach(e -> api.put(e.name(), e.id()));
        UUID commerce = capability(p, null, "Commerce", "COM", "Selling to customers and fulfilling what they buy.");
        UUID orders = capability(p, commerce, "Order management", "COM.ORD", "Taking, tracking and completing customer orders.");
        UUID capture = capability(p, orders, "Order capture", "COM.ORD.CAP", "Accepting orders from customers.");
        link(p, capability(p, capture, "Place order", "COM.ORD.CAP.01", "Check and store a new order, then announce it."),
                api.get("createOrder"), api.get("orderCreated"));
        UUID inquiry = capability(p, orders, "Order inquiry", "COM.ORD.INQ", "Letting customers and staff look orders up.");
        link(p, inquiry, api.get("getOrder"), api.get("listOrders"));
        UUID payments = capability(p, commerce, "Payment handling", "COM.PAY", "Reacting to payments made for orders.");
        link(p, capability(p, payments, "Payment settlement", "COM.PAY.SET", "Mark orders paid when the payment completes."),
                api.get("handlePaymentCompleted"));
        capability(p, payments, "Refunds", "COM.PAY.REF", "Not built yet: a gap the capability map makes visible.");

        return projects.get(p);
    }

    // ---- small builders ----

    private UUID capability(UUID project, UUID parent, String name, String code, String description) {
        return capabilities.create(project, new CapabilityRequest(name, code, description, parent, null)).id();
    }

    private void link(UUID project, UUID capability, UUID... apis) {
        capabilities.setApis(project, capability, new IdList(Arrays.stream(apis).filter(Objects::nonNull).toList()));
    }

    private static final class F {
        String format;
        String description;
        Integer minLength;
        Integer maxLength;
        Double minimum;
        Double maximum;
        String pattern;
        List<String> enumValues = List.of();
        FieldType itemType;
    }

    private static FieldDto field(String name, FieldType type, boolean required, java.util.function.Consumer<F> extra) {
        F f = new F();
        extra.accept(f);
        return new FieldDto(name, type, f.itemType, null, null, required, f.format, f.description, null,
                f.minLength, f.maxLength, f.minimum, f.maximum, f.pattern, f.enumValues);
    }

    private static FieldDto str(String name, boolean required) {
        return field(name, FieldType.STRING, required, f -> { });
    }

    private static FieldDto ref(String name, UUID payload, boolean required) {
        return new FieldDto(name, FieldType.OBJECT, null, payload, null, required, null, null, null,
                null, null, null, null, null, List.of());
    }

    private static FieldDto list(String name, UUID payload, boolean required, Integer minItems) {
        return new FieldDto(name, FieldType.ARRAY, FieldType.OBJECT, payload, null, required, null, null, null,
                minItems, null, null, null, null, List.of());
    }

    private UUID payload(UUID project, String name, String description, String example, FieldDto... fields) {
        return payloads.create(project, new PayloadRequest(name, description, example, List.of(fields), PayloadRole.STRUCTURE)).id();
    }

    /** A building block for the object inventory. */
    private UUID object(UUID project, String name, String description, String example, FieldDto... fields) {
        return payloads.create(project, new PayloadRequest(name, description, example, List.of(fields), PayloadRole.OBJECT)).id();
    }

    private static ResponseDto response(String status, String description, UUID payload) {
        return new ResponseDto(status, description, payload, null);
    }

    private UUID rest(UUID project, String name, String summary, HttpMethod method, String path,
                      List<ParameterDto> params, UUID body, List<ResponseDto> responses) {
        return endpoints.create(project, new EndpointRequest(EndpointKind.REST, name, summary, null, List.of("Orders"), null,
                params == null ? List.of() : params, method, path, body, responses, List.of(SecurityScheme.BEARER_JWT),
                null, null, null, null)).id();
    }

    private static Map<String, Object> check(String field, String kind, String value) {
        return value == null ? Map.of("field", field, "kind", kind) : Map.of("field", field, "kind", kind, "value", value);
    }

    private UUID rule(UUID project, UUID endpoint, RuleType type, String name, String condition, Map<String, Object> config) {
        JsonNode node = JSON.valueToTree(config);
        return rules.create(project, endpoint, new RuleRequest(type, name, condition, node, null)).id();
    }

    private void code(UUID project, String code, ErrorCategory category, Integer status, String message, ErrorLink... links) {
        UUID id = errorCodes.create(project, new ErrorCodeRequest(code, message, status, category, null)).id();
        errorCodes.setLinks(project, id, new ErrorLinksRequest(List.of(links)));
    }
}
