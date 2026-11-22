package dev.specbuilder.expression;

import dev.specbuilder.expression.Expr.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses the shared expression syntax:
 * <pre>
 *   or      := and ('or' and)*
 *   and     := not ('and' not)*
 *   not     := 'not' not | compare
 *   compare := sum (('=='|'!='|'&lt;'|'&lt;='|'&gt;'|'&gt;=') sum)?
 *   sum     := product (('+'|'-') product)*
 *   product := unary (('*'|'/'|'%') unary)*
 *   unary   := '-' unary | primary
 *   primary := number | 'text' | true | false | null | '(' or ')' | name '(' args ')' | path
 *   path    := name '[]'? ('.' name '[]'?)*
 * </pre>
 * Besides syntax, it checks what needs no payload: known functions, argument counts, and that
 * {@code []} paths appear only inside an aggregate, all over the same list.
 */
public final class ExpressionParser {

    private static final Set<String> KEYWORDS = Set.of("and", "or", "not", "true", "false", "null");

    private enum T { NUMBER, STRING, NAME, OP, LPAREN, RPAREN, COMMA, DOT, ITEMS, END }

    private record Token(T type, String text, int position) {
    }

    private final List<Token> tokens;
    private int index;

    private ExpressionParser(String source) {
        this.tokens = lex(source);
    }

    public static Expr parse(String source) {
        if (source == null || source.isBlank()) {
            throw new ExpressionException("the expression is empty", 0);
        }
        ExpressionParser parser = new ExpressionParser(source);
        Expr expr = parser.or();
        Token next = parser.peek();
        if (next.type() != T.END) {
            throw new ExpressionException("unexpected '" + next.text() + "'", next.position());
        }
        checkStructure(expr, null);
        return expr;
    }

    /** Null if valid, else a message with the position. */
    public static String problem(String source) {
        try {
            parse(source);
            return null;
        } catch (ExpressionException e) {
            return e.describe();
        }
    }

    // ---- grammar ----

    private Expr or() {
        Expr left = and();
        while (keyword("or")) {
            int at = next().position();
            left = new Binary(BinaryOp.OR, left, and(), at);
        }
        return left;
    }

    private Expr and() {
        Expr left = not();
        while (keyword("and")) {
            int at = next().position();
            left = new Binary(BinaryOp.AND, left, not(), at);
        }
        return left;
    }

    private Expr not() {
        if (keyword("not")) {
            int at = next().position();
            return new Unary(UnaryOp.NOT, not(), at);
        }
        return compare();
    }

    private Expr compare() {
        Expr left = sum();
        BinaryOp op = operator("==", "!=", "<", "<=", ">", ">=");
        if (op == null) {
            return left;
        }
        int at = next().position();
        Expr result = new Binary(op, left, sum(), at);
        if (operator("==", "!=", "<", "<=", ">", ">=") != null) {
            throw new ExpressionException("comparisons cannot be chained; combine them with 'and'", peek().position());
        }
        return result;
    }

    private Expr sum() {
        Expr left = product();
        for (BinaryOp op = operator("+", "-"); op != null; op = operator("+", "-")) {
            int at = next().position();
            left = new Binary(op, left, product(), at);
        }
        return left;
    }

    private Expr product() {
        Expr left = unary();
        for (BinaryOp op = operator("*", "/", "%"); op != null; op = operator("*", "/", "%")) {
            int at = next().position();
            left = new Binary(op, left, unary(), at);
        }
        return left;
    }

    private Expr unary() {
        if (peek().type() == T.OP && peek().text().equals("-")) {
            int at = next().position();
            return new Unary(UnaryOp.NEGATE, unary(), at);
        }
        return primary();
    }

    private Expr primary() {
        Token t = next();
        switch (t.type()) {
            case NUMBER:
                return new Literal(LiteralKind.NUMBER, t.text(), t.position());
            case STRING:
                return new Literal(LiteralKind.STRING, t.text(), t.position());
            case LPAREN: {
                Expr inner = or();
                expect(T.RPAREN, "')'");
                return inner;
            }
            case NAME:
                return switch (t.text()) {
                    case "true", "false" -> new Literal(LiteralKind.BOOLEAN, t.text(), t.position());
                    case "null" -> new Literal(LiteralKind.NULL, "null", t.position());
                    case "and", "or", "not" -> throw new ExpressionException("expected a value before '" + t.text() + "'", t.position());
                    default -> peek().type() == T.LPAREN ? call(t) : path(t);
                };
            case END:
                throw new ExpressionException("the expression ends too early", t.position());
            default:
                throw new ExpressionException("unexpected '" + t.text() + "'", t.position());
        }
    }

    private Expr call(Token name) {
        next(); // (
        List<Expr> args = new ArrayList<>();
        if (peek().type() != T.RPAREN) {
            args.add(or());
            while (peek().type() == T.COMMA) {
                next();
                args.add(or());
            }
        }
        expect(T.RPAREN, "')' or ','");
        return new Call(name.text(), args, name.position());
    }

    private Expr path(Token first) {
        List<Path.Segment> segments = new ArrayList<>();
        segments.add(new Path.Segment(first.text(), items()));
        while (peek().type() == T.DOT) {
            next();
            Token name = expect(T.NAME, "a field name after '.'");
            if (KEYWORDS.contains(name.text())) {
                throw new ExpressionException("'" + name.text() + "' cannot be used as a field name", name.position());
            }
            segments.add(new Path.Segment(name.text(), items()));
        }
        return new Path(List.copyOf(segments), first.position());
    }

    private boolean items() {
        if (peek().type() == T.ITEMS) {
            next();
            return true;
        }
        return false;
    }

    // ---- structural checks ----

    /** {@code prefixes} is null outside an aggregate call; inside one it collects the lists the call reads. */
    private static void checkStructure(Expr expr, Set<String> prefixes) {
        switch (expr) {
            case Literal l -> { }
            case Path p -> {
                long levels = p.segments().stream().filter(Path.Segment::intoItems).count();
                if (levels > 1) {
                    throw new ExpressionException("only one level of [] is supported in '" + p.text() + "'", p.position());
                }
                if (levels > 0 && prefixes == null) {
                    throw new ExpressionException("'" + p.text() + "' is a list of values: wrap it in sum(), count(), min(), max(), any() or all()",
                            p.position());
                }
                if (levels == 1) {
                    prefixes.add(p.arrayPrefix());
                }
            }
            case Unary u -> checkStructure(u.operand(), prefixes);
            case Binary b -> {
                checkStructure(b.left(), prefixes);
                checkStructure(b.right(), prefixes);
            }
            case Call c -> {
                Functions fn = Functions.byName(c.function());
                if (fn == null) {
                    throw new ExpressionException("unknown function '" + c.function() + "'", c.position());
                }
                int n = c.args().size();
                if (n < fn.minArgs() || n > fn.maxArgs()) {
                    String expected = fn.minArgs() == fn.maxArgs() ? String.valueOf(fn.minArgs()) : fn.minArgs() + " to " + fn.maxArgs();
                    throw new ExpressionException(c.function() + "() takes " + expected + " argument" + (fn.maxArgs() == 1 ? "" : "s")
                            + ", not " + n + " (e.g. " + fn.example() + ")", c.position());
                }
                if (fn.aggregate()) {
                    if (prefixes != null) {
                        throw new ExpressionException(c.function() + "() cannot be used inside another list function", c.position());
                    }
                    Set<String> own = new HashSet<>();
                    c.args().forEach(a -> checkStructure(a, own));
                    if (own.isEmpty()) {
                        throw new ExpressionException(c.function() + "() needs a list path with [], e.g. " + fn.example(), c.position());
                    }
                    if (own.size() > 1) {
                        throw new ExpressionException(c.function() + "() mixes different lists (" + String.join(", ", own.stream().sorted().toList())
                                + "); use one list per function", c.position());
                    }
                } else {
                    c.args().forEach(a -> checkStructure(a, prefixes));
                }
            }
        }
    }

    // ---- tokens ----

    private Token peek() {
        return tokens.get(index);
    }

    private Token next() {
        Token t = tokens.get(index);
        if (t.type() != T.END) {
            index++;
        }
        return t;
    }

    private Token expect(T type, String what) {
        Token t = next();
        if (t.type() != type) {
            throw new ExpressionException("expected " + what + (t.type() == T.END ? " before the end" : ", found '" + t.text() + "'"),
                    t.position());
        }
        return t;
    }

    private boolean keyword(String word) {
        return peek().type() == T.NAME && peek().text().equals(word);
    }

    private BinaryOp operator(String... symbols) {
        Token t = peek();
        if (t.type() != T.OP) {
            return null;
        }
        for (String s : symbols) {
            if (t.text().equals(s)) {
                for (BinaryOp op : BinaryOp.values()) {
                    if (op.symbol().equals(s)) {
                        return op;
                    }
                }
            }
        }
        return null;
    }

    private static List<Token> lex(String s) {
        List<Token> out = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (Character.isDigit(c)) {
                int start = i;
                while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
                if (i + 1 < s.length() && s.charAt(i) == '.' && Character.isDigit(s.charAt(i + 1))) {
                    i++;
                    while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
                }
                out.add(new Token(T.NUMBER, s.substring(start, i), start));
            } else if (Character.isLetter(c) || c == '_') {
                int start = i;
                while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_')) i++;
                out.add(new Token(T.NAME, s.substring(start, i), start));
            } else if (c == '\'') {
                int start = i++;
                StringBuilder text = new StringBuilder();
                while (true) {
                    if (i >= s.length()) {
                        throw new ExpressionException("this text is missing its closing '", start);
                    }
                    char ch = s.charAt(i);
                    if (ch == '\\' && i + 1 < s.length()) {
                        text.append(s.charAt(i + 1));
                        i += 2;
                    } else if (ch == '\'') {
                        i++;
                        break;
                    } else {
                        text.append(ch);
                        i++;
                    }
                }
                out.add(new Token(T.STRING, text.toString(), start));
            } else if (c == '[') {
                if (i + 1 < s.length() && s.charAt(i + 1) == ']') {
                    out.add(new Token(T.ITEMS, "[]", i));
                    i += 2;
                } else {
                    throw new ExpressionException("only empty [] is supported, to step into list items", i);
                }
            } else {
                String two = i + 1 < s.length() ? s.substring(i, i + 2) : "";
                switch (two) {
                    case "==", "!=", "<=", ">=" -> {
                        out.add(new Token(T.OP, two, i));
                        i += 2;
                        continue;
                    }
                    case "&&" -> throw new ExpressionException("write 'and' instead of '&&'", i);
                    case "||" -> throw new ExpressionException("write 'or' instead of '||'", i);
                    default -> { }
                }
                T type = switch (c) {
                    case '(' -> T.LPAREN;
                    case ')' -> T.RPAREN;
                    case ',' -> T.COMMA;
                    case '.' -> T.DOT;
                    case '<', '>', '+', '-', '*', '/', '%' -> T.OP;
                    case '=' -> throw new ExpressionException("write '==' to compare", i);
                    case '!' -> throw new ExpressionException("write 'not' instead of '!'", i);
                    case '"' -> throw new ExpressionException("use single quotes for text, e.g. 'PAID'", i);
                    default -> throw new ExpressionException("unexpected character '" + c + "'", i);
                };
                out.add(new Token(type, String.valueOf(c), i));
                i++;
            }
        }
        out.add(new Token(T.END, "end", s.length()));
        return out;
    }
}
