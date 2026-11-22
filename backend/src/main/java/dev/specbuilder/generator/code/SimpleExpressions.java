package dev.specbuilder.generator.code;

import dev.specbuilder.expression.Expr;
import dev.specbuilder.expression.Expr.*;
import dev.specbuilder.expression.ExpressionParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Translates conditions to Camel Simple when Simple can express them: field paths, parameters (as
 * headers), literals, comparisons, and a flat chain of only "and" or only "or". Anything else
 * (functions, arithmetic, "not", mixed and/or) returns null, and the caller falls back to a Java predicate.
 */
final class SimpleExpressions {

    private SimpleExpressions() {
    }

    /** Simple text such as {@code ${body.status} == 'PAID'}, or null if Simple cannot express it. */
    static String condition(String source, Predicate<String> isParam, boolean hasBody) {
        Expr e = ExpressionParser.parse(source);
        List<Expr> parts = new ArrayList<>();
        BinaryOp joiner = flatten(e, parts);
        List<String> out = new ArrayList<>();
        for (Expr part : parts) {
            String s = comparison(part, isParam, hasBody);
            if (s == null) return null;
            out.add(s);
        }
        return String.join(joiner == BinaryOp.OR ? " || " : " && ", out);
    }

    /** Collects the operands of a chain of one logical operator; null joiner for a single operand. */
    private static BinaryOp flatten(Expr e, List<Expr> parts) {
        if (e instanceof Binary b && (b.op() == BinaryOp.AND || b.op() == BinaryOp.OR)) {
            BinaryOp left = flatten(b.left(), parts);
            BinaryOp right = flatten(b.right(), parts);
            if ((left != null && left != b.op()) || (right != null && right != b.op())) {
                parts.clear();
                parts.add(new Literal(LiteralKind.NULL, "mixed", 0)); // forces the Java fallback
                return null;
            }
            return b.op();
        }
        parts.add(e);
        return null;
    }

    private static final Set<BinaryOp> COMPARISONS = Set.of(BinaryOp.EQ, BinaryOp.NE, BinaryOp.LT, BinaryOp.LE, BinaryOp.GT, BinaryOp.GE);

    private static String comparison(Expr e, Predicate<String> isParam, boolean hasBody) {
        if (e instanceof Binary b && COMPARISONS.contains(b.op())) {
            String l = operand(b.left(), isParam, hasBody);
            String r = operand(b.right(), isParam, hasBody);
            return l == null || r == null ? null : l + " " + b.op().symbol() + " " + r;
        }
        if (e instanceof Path p) {
            String v = operand(p, isParam, hasBody);
            return v == null ? null : v + " == true";
        }
        return null;
    }

    private static String operand(Expr e, Predicate<String> isParam, boolean hasBody) {
        return switch (e) {
            case Path p when p.hasItems() -> null;
            case Path p when p.segments().size() == 1 && isParam.test(p.text()) -> "${header." + p.text() + "}";
            case Path p when hasBody -> "${body?." + String.join("?.", p.segments().stream().map(Path.Segment::name).toList()) + "}";
            case Literal l when l.kind() == LiteralKind.STRING -> l.text().contains("'") ? null : "'" + l.text() + "'";
            case Literal l when l.text().equals("mixed") -> null;
            case Literal l -> l.text();
            default -> null;
        };
    }
}
