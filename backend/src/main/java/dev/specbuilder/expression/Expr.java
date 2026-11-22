package dev.specbuilder.expression;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Parsed form of the shared expression syntax. Code generation translates this tree to Java
 * (implementation-class mode) or Camel Simple (processing-route mode).
 */
public sealed interface Expr {

    /** Character offset in the source, for error messages. */
    int position();

    enum LiteralKind { NUMBER, STRING, BOOLEAN, NULL }

    record Literal(LiteralKind kind, String text, int position) implements Expr {
    }

    /** {@code items[].quantity}: segments with a flag for "step into the array items". */
    record Path(List<Segment> segments, int position) implements Expr {

        public record Segment(String name, boolean intoItems) {
        }

        public boolean hasItems() {
            return segments.stream().anyMatch(Segment::intoItems);
        }

        /** The part up to and including the first {@code []}, e.g. "items[]". */
        public String arrayPrefix() {
            StringBuilder sb = new StringBuilder();
            for (Segment s : segments) {
                if (!sb.isEmpty()) {
                    sb.append('.');
                }
                sb.append(s.name());
                if (s.intoItems()) {
                    return sb.append("[]").toString();
                }
            }
            return null;
        }

        public String text() {
            return segments.stream().map(s -> s.name() + (s.intoItems() ? "[]" : "")).collect(Collectors.joining("."));
        }
    }

    record Call(String function, List<Expr> args, int position) implements Expr {
    }

    enum UnaryOp { NOT, NEGATE }

    record Unary(UnaryOp op, Expr operand, int position) implements Expr {
    }

    enum BinaryOp {
        OR("or"), AND("and"),
        EQ("=="), NE("!="), LT("<"), LE("<="), GT(">"), GE(">="),
        ADD("+"), SUB("-"), MUL("*"), DIV("/"), MOD("%");

        private final String symbol;

        BinaryOp(String symbol) {
            this.symbol = symbol;
        }

        public String symbol() {
            return symbol;
        }

        public boolean isComparison() {
            return this == EQ || this == NE || this == LT || this == LE || this == GT || this == GE;
        }

        public boolean isArithmetic() {
            return this == ADD || this == SUB || this == MUL || this == DIV || this == MOD;
        }
    }

    record Binary(BinaryOp op, Expr left, Expr right, int position) implements Expr {
    }
}
