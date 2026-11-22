package ${project.basePackage}.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExpressionsTest {

    @Test
    void comparesAcrossNumberTypesAndHandlesNull() {
        assertTrue(Expressions.eq(2, new BigDecimal("2.0")));
        assertTrue(Expressions.gt(3L, 2.5));
        assertFalse(Expressions.lt(null, 1));
        assertTrue(Expressions.eq(null, null));
        assertTrue(Expressions.eq("PAID", "PAID"));
    }

    @Test
    void doesArithmeticAndAggregates() {
        assertEquals(new BigDecimal("7.5"), Expressions.add(5, new BigDecimal("2.5")));
        assertNull(Expressions.multiply(null, 2));
        assertEquals(new BigDecimal("6"), Expressions.sum(List.of(1, 2, 3)));
        assertEquals(2L, Expressions.count(List.of("a", "b")));
        assertEquals(0, Expressions.list(null).size());
    }

    @Test
    void convertsToFieldTypes() {
        UUID id = UUID.randomUUID();
        assertEquals(id, Expressions.as(id.toString(), UUID.class));
        assertEquals(Integer.valueOf(3), Expressions.as(new BigDecimal("3"), Integer.class));
        assertEquals("12", Expressions.as(12, String.class));
        assertTrue(Expressions.oneOf("B", "A", "B"));
        assertTrue(Expressions.matches(null, "[0-9]+"));
        assertFalse(Expressions.matches("x", "[0-9]+"));
    }
}
