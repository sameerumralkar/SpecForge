package dev.specbuilder.service;

import dev.specbuilder.api.dto.RuleWarning;
import dev.specbuilder.domain.*;
import dev.specbuilder.domain.rule.*;
import dev.specbuilder.expression.Expr;
import dev.specbuilder.expression.ExpressionChecker;
import dev.specbuilder.expression.ExpressionParser;
import dev.specbuilder.validation.FieldPathResolver;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Walks an endpoint's rules in order, tracking which payload is "current" (the input, until a
 * TRANSFORM replaces it). It writes each step's summary and warns about anything that will not
 * line up at generation time, such as a field path that does not exist.
 */
public final class RuleChainAnalyzer {

    private static final Pattern URL_PLACEHOLDER = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_.\\[\\]]*)}");

    public record Step(ProcessingRule rule, RuleConfig config) {
    }

    public record Result(Map<UUID, String> summaries, List<RuleWarning> warnings) {
    }

    private final Endpoint endpoint;
    private final Map<UUID, Payload> payloads;
    private final List<RuleWarning> warnings = new ArrayList<>();
    private ProcessingRule rule;

    private RuleChainAnalyzer(Endpoint endpoint, Map<UUID, Payload> payloads) {
        this.endpoint = endpoint;
        this.payloads = payloads;
    }

    public static Result analyze(Endpoint endpoint, List<Step> steps, Map<UUID, Payload> payloads) {
        return new RuleChainAnalyzer(endpoint, payloads).run(steps);
    }

    private Result run(List<Step> steps) {
        Map<UUID, String> summaries = new LinkedHashMap<>();
        Set<String> ruleNames = new HashSet<>();
        steps.forEach(s -> ruleNames.add(s.rule().getName().toLowerCase(Locale.ROOT)));
        Payload current = endpoint.inputPayload();

        for (Step step : steps) {
            rule = step.rule();
            Payload in = current;
            SummaryContext ctx = new SummaryContext(id -> payloads.containsKey(id) ? payloads.get(id).getName() : null,
                    in == null ? null : in.getName());
            summaries.put(rule.getId(), withCondition(rule.getCondition(), step.config().summary(ctx)));

            if (rule.getCondition() != null && !rule.getCondition().isBlank() && !(step.config() instanceof ErrorHandlingConfig)) {
                condition("condition", rule.getCondition(), in);
            }
            switch (step.config()) {
                case ValidateConfig c -> validate(c, in);
                case TransformConfig c -> current = transform(c, in);
                case EnrichConfig c -> enrich(c, in);
                case ConditionalRouteConfig c -> c.branches().forEach(b -> condition("branch " + b.name(), b.condition(), in));
                case PersistConfig c -> persist(c, in);
                case PublishEventConfig c -> publish(c, in);
                case SplitAggregateConfig c -> split(c, in);
                case ErrorHandlingConfig c -> errorHandling(c, ruleNames);
            }
        }
        return new Result(summaries, List.copyOf(warnings));
    }

    private void validate(ValidateConfig c, Payload in) {
        for (ValidateConfig.Check check : c.checks()) {
            if (check.kind() == ValidateConfig.CheckKind.CUSTOM) {
                condition("check on " + check.field(), check.value(), in);
            }
            FieldType type = valueType(in, check.field());
            if (type == null || check.kind() == null) {
                continue;
            }
            boolean fits = switch (check.kind()) {
                case MIN, MAX -> type.isNumeric();
                case NOT_BLANK, PATTERN -> type == FieldType.STRING;
                case MIN_LENGTH, MAX_LENGTH -> type == FieldType.STRING || type == FieldType.ARRAY;
                case REQUIRED, CUSTOM -> true;
            };
            if (!fits) {
                warn("a " + check.kind() + " check does not fit " + check.field() + ", which is " + type);
            }
        }
    }

    private Payload transform(TransformConfig c, Payload in) {
        Payload target = payloads.get(c.targetPayloadId());
        if (target == null) {
            warn("the target payload no longer exists");
            return in;
        }
        for (TransformConfig.FieldMapping m : c.mappings()) {
            FieldPathResolver.Resolution t = resolve(target, m.target());
            if (m.source() != null && !m.source().isBlank()) {
                valueType(in, m.source());
            } else if (m.expression() != null && !m.expression().isBlank()) {
                ExpressionChecker.Type expected = t == null ? ExpressionChecker.Type.UNKNOWN : ExpressionChecker.Type.of(t.valueType());
                Expr expr = ExpressionParser.parse(m.expression());
                ExpressionChecker.checkValue(expr, expected, scope(in))
                        .forEach(p -> warn("mapping for " + m.target() + ": " + p));
            }
        }
        return target;
    }

    private void enrich(EnrichConfig c, Payload in) {
        Matcher placeholder = URL_PLACEHOLDER.matcher(c.url());
        while (placeholder.find()) {
            String path = placeholder.group(1);
            ExpressionChecker.Lookup lookup = scope(in).apply(path);
            if (lookup.error() != null) {
                warn("url placeholder {" + path + "}: " + lookup.error());
            }
        }
        if (c.targetField() == null || c.targetField().isBlank()) {
            return;
        }
        FieldPathResolver.Resolution r = resolve(in, c.targetField());
        if (r != null && c.responsePayloadId() != null && r.valueType() == FieldType.OBJECT
                && !c.responsePayloadId().equals(r.field().getRefPayload().getId())) {
            warn(c.targetField() + " holds a " + r.field().getRefPayload().getName() + ", but the service returns a "
                    + name(c.responsePayloadId()));
        }
    }

    private void persist(PersistConfig c, Payload in) {
        if (in == null && (c.operation() == PersistConfig.Operation.SAVE || c.operation() == PersistConfig.Operation.UPDATE)) {
            warn("there is no payload to " + c.operation().name().toLowerCase(Locale.ROOT) + " at this point");
        }
        if (c.idField() == null || c.idField().isBlank()) {
            if (c.operation() == PersistConfig.Operation.DELETE || c.operation() == PersistConfig.Operation.FIND_BY_ID
                    || c.operation() == PersistConfig.Operation.UPDATE) {
                warn(c.operation() + " needs an id field to find the record");
            }
        } else {
            valueType(in, c.idField());
        }
        if (payloads.values().stream().anyMatch(p -> p.getName().equalsIgnoreCase(c.entityName()))) {
            warn("the entity name " + c.entityName() + " is also a payload name; pick another, e.g. " + c.entityName() + "Entity");
        }
    }

    private void publish(PublishEventConfig c, Payload in) {
        Payload event = c.eventPayloadId() == null ? in : payloads.get(c.eventPayloadId());
        if (event == null) {
            warn(c.eventPayloadId() == null ? "there is no payload to publish at this point; choose an event payload"
                    : "the event payload no longer exists");
            return;
        }
        if (c.keyField() != null && !c.keyField().isBlank()) {
            if (c.protocol() != MessagingProtocol.KAFKA) {
                warn("a message key only applies to Kafka");
            }
            resolve(event, c.keyField());
        }
    }

    private void split(SplitAggregateConfig c, Payload in) {
        FieldPathResolver.Resolution r = resolve(in, c.collectionField());
        if (r == null) {
            return;
        }
        if (r.field().getType() != FieldType.ARRAY || r.intoItems()) {
            warn(c.collectionField() + " is not an array, so there is nothing to split");
            return;
        }
        if (c.aggregation() == SplitAggregateConfig.Aggregation.SUM && c.sumField() != null) {
            if (r.field().getItemType() != FieldType.OBJECT) {
                warn("the items of " + c.collectionField() + " are not objects, so they have no field " + c.sumField());
                return;
            }
            FieldPathResolver.Resolution sum = resolve(r.field().getRefPayload(), c.sumField());
            if (sum != null && (sum.valueType() == null || !sum.valueType().isNumeric())) {
                warn(c.sumField() + " is not a number, so it cannot be added up");
            }
        }
    }

    private void errorHandling(ErrorHandlingConfig c, Set<String> ruleNames) {
        if (c.targetRule() != null) {
            if (c.targetRule().equalsIgnoreCase(rule.getName())) {
                warn("an error-handling rule cannot target itself");
            } else if (!ruleNames.contains(c.targetRule().toLowerCase(Locale.ROOT))) {
                warn("there is no rule called '" + c.targetRule() + "' on this endpoint");
            }
        }
        if (rule.getCondition() != null && !rule.getCondition().isBlank()) {
            warn("conditions are ignored on error-handling rules");
        }
        if (c.deadLetterDestination() != null && endpoint.getKind() == EndpointKind.REST) {
            warn("a dead-letter destination only applies when a message is being processed; REST callers get the fallback instead");
        }
    }

    private void condition(String where, String text, Payload in) {
        Expr expr = ExpressionParser.parse(text);
        ExpressionChecker.checkCondition(expr, scope(in)).forEach(p -> warn(where + ": " + p));
    }

    /** Field lookups for expressions: the payload's fields, or an endpoint parameter for a single name. */
    private Function<String, ExpressionChecker.Lookup> scope(Payload in) {
        return path -> {
            if (!path.contains(".") && !path.contains("[")) {
                for (ParameterDefinition p : endpoint.getParameters()) {
                    if (p.getName().equals(path) && (in == null || in.getFields().stream().noneMatch(f -> f.getName().equals(path)))) {
                        return ExpressionChecker.Lookup.found(ExpressionChecker.Type.of(p.getType()));
                    }
                }
            }
            FieldPathResolver.Resolution r = FieldPathResolver.resolve(in, path);
            return r.ok() ? ExpressionChecker.Lookup.found(ExpressionChecker.Type.of(r.valueType()))
                    : ExpressionChecker.Lookup.missing(r.error());
        };
    }

    /** Resolves a path against the payload, or against the endpoint's parameters for a single name. */
    private FieldType valueType(Payload payload, String path) {
        if (!path.contains(".") && !path.contains("[")) {
            for (ParameterDefinition p : endpoint.getParameters()) {
                if (p.getName().equals(path) && (payload == null || payload.getFields().stream().noneMatch(f -> f.getName().equals(path)))) {
                    return p.getType();
                }
            }
        }
        FieldPathResolver.Resolution r = resolve(payload, path);
        return r == null ? null : r.valueType();
    }

    private FieldPathResolver.Resolution resolve(Payload payload, String path) {
        FieldPathResolver.Resolution r = FieldPathResolver.resolve(payload, path);
        if (!r.ok()) {
            warn(r.error());
            return null;
        }
        return r;
    }

    private String name(UUID id) {
        Payload p = payloads.get(id);
        return p == null ? "unknown payload" : p.getName();
    }

    private void warn(String message) {
        warnings.add(new RuleWarning(rule.getId(), rule.getName(), message));
    }

    private static String withCondition(String condition, String summary) {
        if (condition == null || condition.isBlank()) {
            return summary;
        }
        return "If " + condition + ": " + Character.toLowerCase(summary.charAt(0)) + summary.substring(1);
    }
}
