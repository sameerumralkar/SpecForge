package dev.specbuilder.expression;

import dev.specbuilder.expression.Expr.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExpressionParserTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "status == 'PAID'",
            "total >= 100 and not express or customer.tier == 'GOLD'",
            "sum(items[].quantity * items[].unitPrice) - discount",
            "any(items[].quantity > 10)",
            "count(items[]) > 0",
            "oneOf(status, 'PAID', 'SHIPPED')",
            "concat(firstName, ' ', lastName)",
            "-(a + b) * 2 % 3 / 1.5",
            "'it\\'s' != null",
            "now()",
            "isEmpty(notes) == false",
    })
    void acceptsValidExpressions(String source) {
        assertThat(ExpressionParser.problem(source)).isNull();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
            "a = 1                    | at character 3: write '==' to compare",
            "a && b                   | at character 3: write 'and' instead of '&&'",
            "!a                       | at character 1: write 'not' instead of '!'",
            "\"PAID\"                 | at character 1: use single quotes for text, e.g. 'PAID'",
            "a < b < c                | at character 7: comparisons cannot be chained; combine them with 'and'",
            "(a + 1                   | at character 7: expected ')' before the end",
            "a +                      | at character 4: the expression ends too early",
            "'open                    | at character 1: this text is missing its closing '",
            "items[0].x               | at character 6: only empty [] is supported, to step into list items",
            "items[].x > 1            | at character 1: 'items[].x' is a list of values: wrap it in sum(), count(), min(), max(), any() or all()",
            "sum(a[].x + b[].y)       | at character 1: sum() mixes different lists (a[], b[]); use one list per function",
            "sum(total)               | at character 1: sum() needs a list path with [], e.g. sum(items[].price * items[].quantity)",
            "sum(count(a[]))          | at character 5: count() cannot be used inside another list function",
            "a[].b[].c == 1           | at character 1: only one level of [] is supported in 'a[].b[].c'",
            "foo(1)                   | at character 1: unknown function 'foo'",
            "now(1)                   | at character 1: now() takes 0 arguments, not 1 (e.g. now())",
            "a b                      | at character 3: unexpected 'b'",
            "a.and                    | at character 3: 'and' cannot be used as a field name",
    })
    void explainsSyntaxErrors(String source, String message) {
        assertThat(ExpressionParser.problem(source)).isEqualTo(message);
    }

    @Test
    void buildsTheTreeWithUsualPrecedence() {
        Expr e = ExpressionParser.parse("a or b and c == 1 + 2 * 3");
        Binary or = (Binary) e;
        assertThat(or.op()).isEqualTo(BinaryOp.OR);
        Binary and = (Binary) or.right();
        assertThat(and.op()).isEqualTo(BinaryOp.AND);
        Binary eq = (Binary) and.right();
        Binary plus = (Binary) eq.right();
        assertThat(plus.op()).isEqualTo(BinaryOp.ADD);
        assertThat(((Binary) plus.right()).op()).isEqualTo(BinaryOp.MUL);

        Path path = (Path) ((Call) ExpressionParser.parse("count(items[].parts)")).args().getFirst();
        assertThat(path.text()).isEqualTo("items[].parts");
        assertThat(path.arrayPrefix()).isEqualTo("items[]");
    }

    @Test
    void typeChecksAgainstFieldTypes() {
        Map<String, ExpressionChecker.Type> fields = Map.of(
                "status", ExpressionChecker.Type.STRING,
                "total", ExpressionChecker.Type.NUMBER,
                "items[].price", ExpressionChecker.Type.NUMBER,
                "createdAt", ExpressionChecker.Type.DATE_TIME);
        java.util.function.Function<String, ExpressionChecker.Lookup> scope = path -> fields.containsKey(path)
                ? ExpressionChecker.Lookup.found(fields.get(path))
                : ExpressionChecker.Lookup.missing("'" + path + "' is not a field");

        assertThat(ExpressionChecker.checkCondition(ExpressionParser.parse("status == 'PAID' and total > sum(items[].price)"), scope)).isEmpty();
        assertThat(ExpressionChecker.checkCondition(ExpressionParser.parse("createdAt < now()"), scope)).isEmpty();
        assertThat(ExpressionChecker.checkCondition(ExpressionParser.parse("total + 1"), scope))
                .containsExactly("a condition must be true or false, but this is a number");
        assertThat(ExpressionChecker.checkCondition(ExpressionParser.parse("status == 1 or missing"), scope))
                .containsExactly("cannot compare text with a number using '=='", "'missing' is not a field");
        assertThat(ExpressionChecker.checkCondition(ExpressionParser.parse("upper(total) == 'X'"), scope))
                .containsExactly("upper() needs text, not a number");
        assertThat(ExpressionChecker.checkValue(ExpressionParser.parse("concat(status, '!')"), ExpressionChecker.Type.STRING, scope)).isEmpty();
        assertThat(ExpressionChecker.checkValue(ExpressionParser.parse("total * 2"), ExpressionChecker.Type.STRING, scope))
                .containsExactly("this gives a number, but text is needed");
        assertThat(ExpressionChecker.checkValue(ExpressionParser.parse("null"), ExpressionChecker.Type.UUID, scope)).isEmpty();
        assertThat(List.of(ExpressionChecker.Type.values())).hasSize(10);
    }
}
