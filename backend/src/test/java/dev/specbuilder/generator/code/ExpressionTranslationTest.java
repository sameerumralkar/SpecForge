package dev.specbuilder.generator.code;

import dev.specbuilder.domain.FieldType;
import dev.specbuilder.generator.ProjectSnapshot.PayloadSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static dev.specbuilder.generator.Snapshots.*;
import static org.assertj.core.api.Assertions.assertThat;

class ExpressionTranslationTest {

    private final PayloadSnapshot item = payload("Item", field("price", FieldType.DECIMAL, true), field("qty", FieldType.INTEGER, true));
    private final PayloadSnapshot customer = payload("Customer", field("email", FieldType.STRING, false));
    private final PayloadSnapshot order = payload("Order", field("status", FieldType.STRING, true), ref("customer", "Customer"),
            listOf("items", "Item"));
    private final JavaExpressions java = new JavaExpressions(Map.of("Item", item, "Customer", customer, "Order", order));
    private final JavaExpressions.Scope scope = new JavaExpressions.SimpleScope("current", order, Map.of("orderId", "orderId"));

    @Test
    void translatesConditionsToNullSafeJava() {
        assertThat(java.condition("status == 'PAID' and not isEmpty(items)", scope)).isEqualTo(
                "(Expressions.eq((current == null ? null : current.getStatus()), \"PAID\")) && "
                        + "(!(Expressions.isEmpty((current == null ? null : current.getItems()))))");
        assertThat(java.condition("customer.email == null", scope)).isEqualTo(
                "Expressions.eq(java.util.Optional.ofNullable(current).map($v -> $v.getCustomer()).map($v -> $v.getEmail()).orElse(null), null)");
        assertThat(java.condition("orderId != null", scope)).isEqualTo("!Expressions.eq(orderId, null)");
    }

    @Test
    void translatesAggregatesOverListItems() {
        assertThat(java.value("sum(items[].price * items[].qty)", scope, "BigDecimal")).isEqualTo(
                "Expressions.as(Expressions.sum(Expressions.list((current == null ? null : current.getItems()))"
                        + ".stream().filter(java.util.Objects::nonNull).map($it -> (Object) (Expressions.multiply("
                        + "($it == null ? null : $it.getPrice()), ($it == null ? null : $it.getQty())))).toList()), BigDecimal.class)");
        assertThat(java.condition("any(items[].qty > 10)", scope)).isEqualTo(
                "Expressions.list((current == null ? null : current.getItems())).stream().filter(java.util.Objects::nonNull)"
                        + ".anyMatch($it -> Expressions.gt(($it == null ? null : $it.getQty()), 10))");
        assertThat(java.value("count(items[])", scope, "Long"))
                .isEqualTo("Expressions.as(Long.valueOf(Expressions.list((current == null ? null : current.getItems())).size()), Long.class)");
    }

    @Test
    void checksListItemPathsOneByOne() {
        assertThat(java.anyValue("items[].qty", scope, v -> v + " == null")).isEqualTo(
                "Expressions.list((current == null ? null : current.getItems())).stream().filter(java.util.Objects::nonNull)"
                        + ".anyMatch($it -> ($it == null ? null : $it.getQty()) == null)");
        assertThat(java.anyValue("status", scope, v -> v + " == null")).isEqualTo("(current == null ? null : current.getStatus()) == null");
    }

    @Test
    void usesCamelSimpleOnlyWhenItFits() {
        assertThat(SimpleExpressions.condition("status == 'PAID' and orderId != null", "orderId"::equals, true))
                .isEqualTo("${body?.status} == 'PAID' && ${header.orderId} != null");
        assertThat(SimpleExpressions.condition("customer.email == 'a@b.c' or status == 'X'", n -> false, true))
                .isEqualTo("${body?.customer?.email} == 'a@b.c' || ${body?.status} == 'X'");
        for (String javaOnly : List.of("sum(items[].qty) > 1", "not (status == 'A')", "status == 'A' and status == 'B' or status == 'C'",
                "status == 'it''s'", "total + 1 > 2")) {
            assertThat(SimpleExpressions.condition(javaOnly.replace("'it''s'", "'it\\'s'"), n -> false, true)).as(javaOnly).isNull();
        }
    }

    @Test
    void namesAreJavaFriendly() {
        assertThat(Names.pascal("order service")).isEqualTo("OrderService");
        assertThat(Names.pascal("createOrder")).isEqualTo("CreateOrder");
        assertThat(Names.camel("Enrich customer")).isEqualTo("enrichCustomer");
        assertThat(Names.camel("new")).isEqualTo("new_");
        assertThat(Names.kebab("Order Service 2")).isEqualTo("order-service-2");
        assertThat(Names.snake("OrderEntity")).isEqualTo("order_entity");
        assertThat(Names.literal("say \"hi\"\n")).isEqualTo("\"say \\\"hi\\\"\\n\"");
        assertThat(Names.pascal("3d printer")).isEqualTo("X3dPrinter");
    }
}
