package dev.specbuilder.generator.code;

import dev.specbuilder.domain.FieldType;
import dev.specbuilder.expression.Expr;
import dev.specbuilder.expression.Expr.*;
import dev.specbuilder.expression.ExpressionParser;
import dev.specbuilder.generator.ProjectSnapshot.FieldSnapshot;
import dev.specbuilder.generator.ProjectSnapshot.PayloadSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Translates the shared expression syntax and field paths into Java. Values are read null-safely
 * and combined through the generated {@code Expressions} helper, which works on any value type,
 * so the output compiles whatever the field types are. Generated lambda parameters start with '$',
 * which user names cannot, so they never clash.
 */
final class JavaExpressions {

    /** Where values come from: the current payload and the endpoint's parameters. */
    interface Scope {
        /** Java code for the current payload, or null if there is none. */
        String root();

        PayloadSnapshot rootPayload();

        /** Java code that reads the named parameter, or null if there is no such parameter. */
        String param(String name);
    }

    record SimpleScope(String root, PayloadSnapshot rootPayload, Map<String, String> params) implements Scope {
        @Override
        public String param(String name) {
            return params.get(name);
        }
    }

    /** One segment of a field path such as {@code items[].sku}. */
    record Segment(String name, boolean intoItems) {
    }

    private record Code(String text, boolean bool) {
        String object() {
            return text;
        }

        String condition() {
            return bool ? text : "Expressions.truthy(" + text + ")";
        }
    }

    private final Map<String, PayloadSnapshot> payloads;

    JavaExpressions(Map<String, PayloadSnapshot> payloads) {
        this.payloads = payloads;
    }

    // ------------------------------------------------------------------ public entry points

    /** A boolean Java expression. */
    String condition(String source, Scope scope) {
        return translate(ExpressionParser.parse(source), scope).condition();
    }

    /** A Java expression of {@code javaType} (converted through Expressions.as for simple types). */
    String value(String source, Scope scope, String javaType) {
        return convert(translate(ExpressionParser.parse(source), scope).object(), javaType);
    }

    /** Reads a field path (no list items) or a parameter, as an Object-typed expression. */
    String read(String path, Scope scope) {
        List<Segment> segments = segments(path);
        if (segments.size() == 1 && scope.param(path) != null && !hasField(scope.rootPayload(), path)) {
            return scope.param(path);
        }
        if (scope.root() == null) {
            return "null /* " + path + " is not available here */";
        }
        return chain(scope.root(), segments);
    }

    /**
     * A boolean expression that is true when {@code failsWhen} is true for the value at {@code path};
     * list items ({@code items[].qty}) are checked one by one, at any depth.
     */
    String anyValue(String path, Scope scope, Function<String, String> failsWhen) {
        List<Segment> segments = segments(path);
        if (indexOfItems(segments) < 0) {
            return failsWhen.apply(read(path, scope));
        }
        return anyValue(segments, scope.root(), scope.rootPayload(), failsWhen, 0);
    }

    private String anyValue(List<Segment> segments, String root, PayloadSnapshot rootPayload,
                            Function<String, String> failsWhen, int depth) {
        int split = indexOfItems(segments);
        if (split < 0) {
            return failsWhen.apply(chain(root, segments));
        }
        List<Segment> listPath = segments.subList(0, split + 1);
        String list = "Expressions.list(" + chain(root, listPath.stream().map(s -> new Segment(s.name(), false)).toList()) + ")";
        String item = depth == 0 ? "$it" : "$it" + depth;
        List<Segment> rest = segments.subList(split + 1, segments.size());
        String inner = rest.isEmpty() ? failsWhen.apply(item)
                : anyValue(rest, item, itemPayload(rootPayload, listPath), failsWhen, depth + 1);
        return list + ".stream().filter(java.util.Objects::nonNull).anyMatch(" + item + " -> " + inner + ")";
    }

    static String convert(String code, String javaType) {
        return switch (javaType) {
            case "String", "Integer", "Long", "Double", "BigDecimal", "Boolean", "UUID", "LocalDate", "OffsetDateTime" ->
                    "Expressions.as(" + code + ", " + javaType + ".class)";
            case "Object" -> code;
            default -> "Expressions.cast(" + code + ")";
        };
    }

    // ------------------------------------------------------------------ expressions

    private Code translate(Expr e, Scope scope) {
        return switch (e) {
            case Literal l -> new Code(literal(l), l.kind() == LiteralKind.BOOLEAN);
            case Path p -> new Code(read(p.text(), scope), false);
            case Unary u -> u.op() == UnaryOp.NOT
                    ? new Code("!" + paren(translate(u.operand(), scope).condition()), true)
                    : new Code("Expressions.neg(" + translate(u.operand(), scope).object() + ")", false);
            case Binary b -> binary(b, scope);
            case Call c -> call(c, scope);
        };
    }

    private Code binary(Binary b, Scope scope) {
        return combine(b.op(), translate(b.left(), scope), translate(b.right(), scope));
    }

    private Code call(Call c, Scope scope) {
        String fn = c.function();
        switch (fn) {
            case "now", "today", "randomUUID" -> {
                return new Code("Expressions." + fn + "()", false);
            }
            case "sum", "min", "max", "count", "any", "all" -> {
                return aggregate(fn, c.args().getFirst(), scope);
            }
            default -> {
                String args = c.args().stream().map(a -> translate(a, scope).object()).collect(Collectors.joining(", "));
                boolean bool = fn.equals("isEmpty") || fn.equals("oneOf");
                return new Code("Expressions." + fn + "(" + args + ")", bool);
            }
        }
    }

    /** sum(items[].a * items[].b) → Expressions.sum(list.stream()....map($it -> ...).toList()) */
    private Code aggregate(String fn, Expr arg, Scope scope) {
        Path first = firstListPath(arg);
        List<Segment> segments = segments(first.arrayPrefix());
        String list = "Expressions.list(" + chain(scope.root(), segments.stream().map(s -> new Segment(s.name(), false)).toList()) + ")";
        String prefix = first.arrayPrefix();
        PayloadSnapshot itemPayload = itemPayload(scope.rootPayload(), segments);
        Scope inner = new Scope() {
            @Override public String root() { return "$it"; }
            @Override public PayloadSnapshot rootPayload() { return itemPayload; }
            @Override public String param(String name) { return scope.param(name); }
        };
        Function<Expr, Code> itemCode = e -> translateInItem(e, prefix, inner, scope);
        String stream = list + ".stream().filter(java.util.Objects::nonNull)";
        if (arg instanceof Path p && p.text().equals(prefix)) {
            if (fn.equals("count")) {
                return new Code("Long.valueOf(" + list + ".size())", false);
            }
        }
        return switch (fn) {
            case "any" -> new Code(stream + ".anyMatch($it -> " + itemCode.apply(arg).condition() + ")", true);
            case "all" -> new Code(stream + ".allMatch($it -> " + itemCode.apply(arg).condition() + ")", true);
            default -> new Code("Expressions." + fn + "(" + stream + ".map($it -> (Object) " + paren(itemCode.apply(arg).object()) + ").toList())", false);
        };
    }

    /** Translates inside an aggregate: paths under the list prefix read from the item. */
    private Code translateInItem(Expr e, String prefix, Scope inner, Scope outer) {
        return switch (e) {
            case Path p when p.text().equals(prefix) -> new Code("$it", false);
            case Path p when p.text().startsWith(prefix + ".") -> new Code(read(p.text().substring(prefix.length() + 1), inner), false);
            case Path p -> translate(p, outer);
            case Literal l -> translate(l, outer);
            case Unary u -> u.op() == UnaryOp.NOT
                    ? new Code("!" + paren(translateInItem(u.operand(), prefix, inner, outer).condition()), true)
                    : new Code("Expressions.neg(" + translateInItem(u.operand(), prefix, inner, outer).object() + ")", false);
            case Binary b -> combine(b.op(), translateInItem(b.left(), prefix, inner, outer),
                    translateInItem(b.right(), prefix, inner, outer));
            case Call c -> {
                String args = c.args().stream().map(a -> translateInItem(a, prefix, inner, outer).object()).collect(Collectors.joining(", "));
                yield new Code("Expressions." + c.function() + "(" + args + ")", c.function().equals("isEmpty") || c.function().equals("oneOf"));
            }
        };
    }

    private static Code combine(BinaryOp op, Code l, Code r) {
        return switch (op) {
            case AND -> new Code(paren(l.condition()) + " && " + paren(r.condition()), true);
            case OR -> new Code(paren(l.condition()) + " || " + paren(r.condition()), true);
            case EQ -> new Code("Expressions.eq(" + l.object() + ", " + r.object() + ")", true);
            case NE -> new Code("!Expressions.eq(" + l.object() + ", " + r.object() + ")", true);
            case LT -> new Code("Expressions.lt(" + l.object() + ", " + r.object() + ")", true);
            case LE -> new Code("Expressions.le(" + l.object() + ", " + r.object() + ")", true);
            case GT -> new Code("Expressions.gt(" + l.object() + ", " + r.object() + ")", true);
            case GE -> new Code("Expressions.ge(" + l.object() + ", " + r.object() + ")", true);
            case ADD -> new Code("Expressions.add(" + l.object() + ", " + r.object() + ")", false);
            case SUB -> new Code("Expressions.subtract(" + l.object() + ", " + r.object() + ")", false);
            case MUL -> new Code("Expressions.multiply(" + l.object() + ", " + r.object() + ")", false);
            case DIV -> new Code("Expressions.divide(" + l.object() + ", " + r.object() + ")", false);
            case MOD -> new Code("Expressions.remainder(" + l.object() + ", " + r.object() + ")", false);
        };
    }

    private static Path firstListPath(Expr e) {
        return switch (e) {
            case Path p -> p.hasItems() ? p : null;
            case Literal l -> null;
            case Unary u -> firstListPath(u.operand());
            case Binary b -> {
                Path l = firstListPath(b.left());
                yield l != null ? l : firstListPath(b.right());
            }
            case Call c -> c.args().stream().map(JavaExpressions::firstListPath).filter(java.util.Objects::nonNull).findFirst().orElse(null);
        };
    }

    // ------------------------------------------------------------------ paths

    static List<Segment> segments(String path) {
        List<Segment> out = new ArrayList<>();
        for (String s : path.split("\\.")) {
            boolean items = s.endsWith("[]");
            out.add(new Segment(items ? s.substring(0, s.length() - 2) : s, items));
        }
        return out;
    }

    private static int indexOfItems(List<Segment> segments) {
        for (int i = 0; i < segments.size(); i++) {
            if (segments.get(i).intoItems()) return i;
        }
        return -1;
    }

    /** Null-safe getter chain: (root == null ? null : root.getA()) or Optional...map(...).orElse(null). */
    static String chain(String root, List<Segment> segments) {
        if (root == null) {
            return "null";
        }
        if (segments.size() == 1) {
            return "(" + root + " == null ? null : " + root + "." + Names.getter(segments.getFirst().name()) + "())";
        }
        StringBuilder sb = new StringBuilder("java.util.Optional.ofNullable(" + root + ")");
        for (Segment s : segments) {
            sb.append(".map($v -> $v.").append(Names.getter(s.name())).append("())");
        }
        return sb.append(".orElse(null)").toString();
    }

    private PayloadSnapshot itemPayload(PayloadSnapshot root, List<Segment> segments) {
        PayloadSnapshot current = root;
        FieldSnapshot field = null;
        for (Segment s : segments) {
            if (current == null) return null;
            field = current.fields().stream().filter(f -> f.name().equals(s.name())).findFirst().orElse(null);
            if (field == null || field.refPayload() == null) {
                current = null;
                continue;
            }
            current = payloads.get(field.refPayload());
        }
        return field != null && field.valueType() == FieldType.OBJECT ? current : null;
    }

    private static boolean hasField(PayloadSnapshot payload, String name) {
        return payload != null && payload.fields().stream().anyMatch(f -> f.name().equals(name));
    }

    private static String literal(Literal l) {
        return switch (l.kind()) {
            case STRING -> Names.literal(l.text());
            case BOOLEAN, NULL -> l.text();
            case NUMBER -> l.text().contains(".") ? l.text() : (l.text().length() > 9 ? l.text() + "L" : l.text());
        };
    }

    private static String paren(String code) {
        return "(" + code + ")";
    }
}
