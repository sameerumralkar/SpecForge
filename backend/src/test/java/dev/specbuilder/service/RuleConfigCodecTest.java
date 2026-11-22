package dev.specbuilder.service;

import dev.specbuilder.domain.rule.*;
import dev.specbuilder.validation.ValidationErrors;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RuleConfigCodecTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final RuleConfigCodec codec = new RuleConfigCodec(Validation.buildDefaultValidatorFactory().getValidator());
    private final SummaryContext ctx = new SummaryContext(id -> "Customer", "CreateOrderRequest");

    private RuleConfig parse(RuleType type, String json, ValidationErrors errors) {
        return codec.parse(type, node(json), errors);
    }

    private static JsonNode node(String json) {
        return JSON.readTree(json.replace('\'', '"'));
    }

    @Test
    void parsesEveryTypeAndFillsDefaults() {
        String[][] samples = {
                {"VALIDATE", "{'checks':[{'field':'a','kind':'REQUIRED'}]}"},
                {"TRANSFORM", "{'targetPayloadId':'" + UUID.randomUUID() + "'}"},
                {"ENRICH", "{'serviceName':'customer-service','url':'/customers/{id}'}"},
                {"CONDITIONAL_ROUTE", "{'branches':[{'name':'big','condition':'total > 100'}]}"},
                {"PERSIST", "{'entityName':'OrderEntity'}"},
                {"PUBLISH_EVENT", "{'destination':'orders.created'}"},
                {"SPLIT_AGGREGATE", "{'collectionField':'items'}"},
                {"ERROR_HANDLING", "{'retry':{}}"},
        };
        for (String[] sample : samples) {
            ValidationErrors errors = new ValidationErrors();
            RuleConfig config = parse(RuleType.valueOf(sample[0]), sample[1], errors);
            assertThat(errors.list()).as(sample[0]).isEmpty();
            assertThat(config).isInstanceOf(RuleType.valueOf(sample[0]).configClass());
            assertThat(config.summary(ctx)).isNotBlank();
        }
        ValidationErrors errors = new ValidationErrors();
        EnrichConfig enrich = (EnrichConfig) parse(RuleType.ENRICH, "{'serviceName':'s','url':'/x'}", errors);
        assertThat(enrich.timeoutMs()).isEqualTo(2000);
        ErrorHandlingConfig retry = (ErrorHandlingConfig) parse(RuleType.ERROR_HANDLING, "{'retry':{}}", errors);
        assertThat(retry.retry().maxAttempts()).isEqualTo(3);
        assertThat(retry.on()).containsExactly(ErrorHandlingConfig.ErrorCategory.ANY);
    }

    @Test
    void rejectsUnknownPropertiesAndBadEnumsWithAPath() {
        ValidationErrors errors = new ValidationErrors();
        parse(RuleType.PERSIST, "{'entityName':'OrderEntity','table':'orders'}", errors);
        assertThat(errors.list()).singleElement().satisfies(e -> {
            assertThat(e.field()).isEqualTo("config.table");
            assertThat(e.message()).contains("Unrecognized property");
        });

        errors = new ValidationErrors();
        parse(RuleType.VALIDATE, "{'checks':[{'field':'a','kind':'NOPE'}]}", errors);
        assertThat(errors.list()).singleElement().satisfies(e -> assertThat(e.field()).isEqualTo("config.checks[0].kind"));
    }

    @Test
    void reportsBeanValidationAndCrossFieldProblems() {
        ValidationErrors errors = new ValidationErrors();
        parse(RuleType.VALIDATE, "{'checks':[{'field':'a','kind':'PATTERN','value':'[a-'},{'field':'b','kind':'MIN','value':'x'}],'failureStatus':500}", errors);
        assertThat(errors.list()).extracting(ValidationErrors.FieldError::field)
                .containsExactlyInAnyOrder("config.failureStatus", "config.checks[0].value", "config.checks[1].value");

        errors = new ValidationErrors();
        parse(RuleType.ERROR_HANDLING, "{'on':['TIMEOUT']}", errors);
        assertThat(errors.list()).singleElement().satisfies(e -> assertThat(e.message()).contains("retry, a fallback or a dead-letter"));

        errors = new ValidationErrors();
        parse(RuleType.ENRICH, "{'serviceName':'Customer Service','url':'ftp://x','timeoutMs':5}", errors);
        assertThat(errors.list()).extracting(ValidationErrors.FieldError::field)
                .containsExactlyInAnyOrder("config.serviceName", "config.url", "config.timeoutMs");

        errors = new ValidationErrors();
        parse(RuleType.TRANSFORM, "{'targetPayloadId':'" + UUID.randomUUID() + "','mappings':[{'target':'a','source':'b','expression':'c'}]}", errors);
        assertThat(errors.list()).extracting(ValidationErrors.FieldError::field).containsExactly("config.mappings[0]");

        errors = new ValidationErrors();
        parse(RuleType.CONDITIONAL_ROUTE, "{'branches':[{'name':'big','condition':'x'},{'name':'big','condition':'y'}]}", errors);
        assertThat(errors.list()).extracting(ValidationErrors.FieldError::field).containsExactly("config.branches[1].name");
    }

    @Test
    void storedJsonIsCanonicalAndRoundTrips() {
        ValidationErrors errors = new ValidationErrors();
        RuleConfig config = parse(RuleType.PUBLISH_EVENT, "{'destination':'orders.created','keyField':'orderId'}", errors);
        String stored = codec.write(config);
        assertThat(codec.read(RuleType.PUBLISH_EVENT, stored)).isEqualTo(config);
        assertThat(codec.write(codec.read(RuleType.PUBLISH_EVENT, stored))).isEqualTo(stored);
    }

    @Test
    void summariesReadAsPlainEnglish() {
        ValidationErrors errors = new ValidationErrors();
        assertThat(parse(RuleType.PUBLISH_EVENT, "{'destination':'orders.created','eventPayloadId':'" + UUID.randomUUID() + "','keyField':'id'}", errors).summary(
                new SummaryContext(id -> "OrderCreatedEvent", "Order")))
                .isEqualTo("Publish an OrderCreatedEvent to Kafka topic orders.created keyed by id.");
        assertThat(parse(RuleType.SPLIT_AGGREGATE, "{'collectionField':'items','parallel':true,'aggregation':'SUM','sumField':'price'}", errors).summary(ctx))
                .isEqualTo("Split items and process each item in parallel, then add up price.");
        assertThat(parse(RuleType.ERROR_HANDLING, "{'on':['TIMEOUT'],'targetRule':'Call','retry':{'maxAttempts':2},'fallback':{'type':'ERROR_RESPONSE','httpStatus':503}}", errors).summary(ctx))
                .isEqualTo("On timeout errors in 'Call': retry up to 2 times (500 ms backoff ×2), then return 503.");
        assertThat(errors.list()).isEmpty();
    }
}
