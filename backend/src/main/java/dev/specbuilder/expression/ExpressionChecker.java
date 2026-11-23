package dev.specbuilder.expression;

import dev.specbuilder.domain.FieldType;
import dev.specbuilder.expression.Expr.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Type-checks a parsed expression against the payload it will run on. Problems found here depend
 * on the payloads, which can still change, so callers report them as warnings.
 */
public final class ExpressionChecker {

    public enum Type {
        NUMBER, STRING, BOOLEAN, DATE, DATE_TIME, UUID, OBJECT, ARRAY, NULL, UNKNOWN;

        public static Type of(FieldType t) {
            if (t == null) {
                return UNKNOWN;
            }
            return switch (t) {
                case INTEGER, LONG, NUMBER, DECIMAL -> NUMBER;
                case STRING -> STRING;
                case BOOLEAN -> BOOLEAN;
                case DATE -> DATE;
                case DATE_TIME -> DATE_TIME;
                case UUID -> UUID;
                case OBJECT -> OBJECT;
                case ARRAY -> ARRAY;
            };
        }

        String label() {
            return switch (this) {
                case NUMBER -> "a number";
                case STRING -> "text";
                case BOOLEAN -> "true/false";
                case DATE -> "a date";
                case DATE_TIME -> "a date-time";
                case UUID -> "a UUID";
                case OBJECT -> "an object";
                case ARRAY -> "a list";
                case NULL -> "null";
                case UNKNOWN -> "unknown";
            };
        }
    }

    /** Resolves a field path to its type, or returns an error message. */
    public record Lookup(Type type, String error) {
        public static Lookup found(Type type) {
            return new Lookup(type, null);
        }

        public static Lookup missing(String error) {
            return new Lookup(Type.UNKNOWN, error);
        }
    }

    private final Function<String, Lookup> scope;
    private final List<String> problems = new ArrayList<>();

    private ExpressionChecker(Function<String, Lookup> scope) {
        this.scope = scope;
    }

    /** Checks a condition: it must be true/false. */
    public static List<String> checkCondition(Expr expr, Function<String, Lookup> scope) {
        ExpressionChecker checker = new ExpressionChecker(scope);
        Type t = checker.type(expr);
        if (!compatible(t, Type.BOOLEAN)) {
            checker.problems.add("a condition must be true or false, but this is " + t.label());
        }
        return checker.problems;
    }

    /** Checks a value assigned to something of type {@code expected} (UNKNOWN = anything goes). */
    public static List<String> checkValue(Expr expr, Type expected, Function<String, Lookup> scope) {
        ExpressionChecker checker = new ExpressionChecker(scope);
        Type t = checker.type(expr);
        if (!compatible(t, expected)) {
            checker.problems.add("this gives " + t.label() + ", but " + expected.label() + " is needed");
        }
        return checker.problems;
    }

    private Type type(Expr expr) {
        return switch (expr) {
            case Literal l -> switch (l.kind()) {
                case NUMBER -> Type.NUMBER;
                case STRING -> Type.STRING;
                case BOOLEAN -> Type.BOOLEAN;
                case NULL -> Type.NULL;
            };
            case Path p -> {
                Lookup lookup = scope.apply(p.text());
                if (lookup.error() != null) {
                    problems.add(lookup.error());
                }
                yield lookup.type();
            }
            case Unary u -> {
                Type t = type(u.operand());
                Type want = u.op() == UnaryOp.NOT ? Type.BOOLEAN : Type.NUMBER;
                require(t, want, u.op() == UnaryOp.NOT ? "'not'" : "'-'");
                yield want;
            }
            case Binary b -> binary(b);
            case Call c -> call(c);
        };
    }

    private Type binary(Binary b) {
        Type l = type(b.left());
        Type r = type(b.right());
        String op = "'" + b.op().symbol() + "'";
        if (b.op() == BinaryOp.AND || b.op() == BinaryOp.OR) {
            require(l, Type.BOOLEAN, op);
            require(r, Type.BOOLEAN, op);
            return Type.BOOLEAN;
        }
        if (b.op().isArithmetic()) {
            require(l, Type.NUMBER, op);
            require(r, Type.NUMBER, op);
            return Type.NUMBER;
        }
        if (b.op() == BinaryOp.EQ || b.op() == BinaryOp.NE) {
            if (!compatible(l, r) && !compatible(r, l)) {
                problems.add("cannot compare " + l.label() + " with " + r.label() + " using " + op);
            }
            return Type.BOOLEAN;
        }
        boolean ordered = (compatible(l, Type.NUMBER) && compatible(r, Type.NUMBER))
                || (compatible(l, Type.STRING) && compatible(r, Type.STRING))
                || (dateLike(l) && dateLike(r) && (l == r || l == Type.UNKNOWN || r == Type.UNKNOWN));
        if (!ordered || l == Type.NULL || r == Type.NULL) {
            problems.add(op + " needs two numbers, two texts or two dates, not " + l.label() + " and " + r.label());
        }
        return Type.BOOLEAN;
    }

    private Type call(Call c) {
        Functions fn = Functions.byName(c.function());
        List<Type> args = c.args().stream().map(this::type).toList();
        String name = c.function() + "()";
        return switch (fn) {
            case NOW -> Type.DATE_TIME;
            case TODAY -> Type.DATE;
            case RANDOM_UUID -> Type.UUID;
            case SUM, MIN, MAX -> {
                require(args.getFirst(), Type.NUMBER, name);
                yield Type.NUMBER;
            }
            case COUNT -> Type.NUMBER;
            case ANY, ALL -> {
                require(args.getFirst(), Type.BOOLEAN, name);
                yield Type.BOOLEAN;
            }
            case SIZE -> {
                Type t = args.getFirst();
                if (!(compatible(t, Type.STRING) || compatible(t, Type.ARRAY))) {
                    problems.add(name + " needs text or a list, not " + t.label());
                }
                yield Type.NUMBER;
            }
            case IS_EMPTY -> Type.BOOLEAN;
            case LOWER, UPPER, TRIM -> {
                require(args.getFirst(), Type.STRING, name);
                yield Type.STRING;
            }
            case CONCAT -> Type.STRING;
            case PLUS_SECONDS -> {
                require(args.get(0), Type.DATE_TIME, name);
                require(args.get(1), Type.NUMBER, name);
                yield Type.DATE_TIME;
            }
            case COALESCE -> args.stream().filter(t -> t != Type.NULL && t != Type.UNKNOWN).findFirst().orElse(Type.UNKNOWN);
            case ONE_OF -> {
                Type first = args.getFirst();
                for (Type t : args.subList(1, args.size())) {
                    if (!compatible(t, first) && !compatible(first, t)) {
                        problems.add(name + " compares " + first.label() + " with " + t.label());
                    }
                }
                yield Type.BOOLEAN;
            }
        };
    }

    private void require(Type actual, Type wanted, String where) {
        if (!compatible(actual, wanted) || actual == Type.NULL) {
            problems.add(where + " needs " + wanted.label() + ", not " + actual.label());
        }
    }

    private static boolean dateLike(Type t) {
        return t == Type.DATE || t == Type.DATE_TIME || t == Type.UNKNOWN;
    }

    /** Whether a value of type {@code actual} can be used where {@code wanted} is expected. */
    static boolean compatible(Type actual, Type wanted) {
        return actual == wanted || actual == Type.UNKNOWN || wanted == Type.UNKNOWN || actual == Type.NULL;
    }
}
