package ${project.basePackage}.support;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Runtime support for the rule expressions. Every method accepts any value (numbers of any type,
 * text, dates, UUIDs) and treats null safely, so the translated expressions compile and run
 * whatever the field types are.
 */
public final class Expressions {

    private Expressions() {
    }

    // ---- comparison

    public static boolean truthy(Object value) {
        return value instanceof Boolean b ? b : value != null && Boolean.parseBoolean(value.toString());
    }

    public static boolean eq(Object a, Object b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a instanceof Number && b instanceof Number) {
            return number(a).compareTo(number(b)) == 0;
        }
        return a.equals(b) || a.toString().equals(b.toString());
    }

    public static boolean lt(Object a, Object b) {
        return a != null && b != null && compare(a, b) < 0;
    }

    public static boolean le(Object a, Object b) {
        return a != null && b != null && compare(a, b) <= 0;
    }

    public static boolean gt(Object a, Object b) {
        return a != null && b != null && compare(a, b) > 0;
    }

    public static boolean ge(Object a, Object b) {
        return a != null && b != null && compare(a, b) >= 0;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compare(Object a, Object b) {
        if (a instanceof Number || b instanceof Number) {
            return number(a).compareTo(number(b));
        }
        if (a instanceof Comparable ca && a.getClass().isInstance(b)) {
            return ca.compareTo(b);
        }
        return a.toString().compareTo(b.toString());
    }

    // ---- arithmetic (null in, null out)

    public static BigDecimal add(Object a, Object b) {
        return a == null || b == null ? null : number(a).add(number(b));
    }

    public static BigDecimal subtract(Object a, Object b) {
        return a == null || b == null ? null : number(a).subtract(number(b));
    }

    public static BigDecimal multiply(Object a, Object b) {
        return a == null || b == null ? null : number(a).multiply(number(b));
    }

    public static BigDecimal divide(Object a, Object b) {
        return a == null || b == null ? null : number(a).divide(number(b), MathContext.DECIMAL64);
    }

    public static BigDecimal remainder(Object a, Object b) {
        return a == null || b == null ? null : number(a).remainder(number(b));
    }

    public static BigDecimal neg(Object a) {
        return a == null ? null : number(a).negate();
    }

    // ---- functions of the expression syntax

    public static OffsetDateTime now() {
        return OffsetDateTime.now();
    }

    public static LocalDate today() {
        return LocalDate.now();
    }

    public static OffsetDateTime plusSeconds(Object dateTime, Object seconds) {
        return dateTime == null || seconds == null ? null : ((OffsetDateTime) dateTime).plusSeconds(number(seconds).longValue());
    }

    public static UUID randomUUID() {
        return UUID.randomUUID();
    }

    public static BigDecimal sum(Collection<?> values) {
        return values.stream().filter(Objects::nonNull).map(Expressions::number).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static BigDecimal min(Collection<?> values) {
        return values.stream().filter(Objects::nonNull).map(Expressions::number).min(BigDecimal::compareTo).orElse(null);
    }

    public static BigDecimal max(Collection<?> values) {
        return values.stream().filter(Objects::nonNull).map(Expressions::number).max(BigDecimal::compareTo).orElse(null);
    }

    public static Long count(Collection<?> values) {
        return values.stream().filter(Objects::nonNull).count();
    }

    /** A list, or an empty list for null, so list paths never fail. */
    public static <T> List<T> list(List<T> values) {
        return values == null ? List.of() : values;
    }

    public static int size(Object value) {
        if (value instanceof Collection<?> c) return c.size();
        return value == null ? 0 : value.toString().length();
    }

    public static boolean isEmpty(Object value) {
        return size(value) == 0;
    }

    public static boolean isBlank(Object value) {
        return value == null || value.toString().isBlank();
    }

    /** True when the value matches the regular expression; a missing value is not checked. */
    public static boolean matches(Object value, String regex) {
        return value == null || Pattern.matches(regex, value.toString());
    }

    public static String lower(Object value) {
        return value == null ? null : value.toString().toLowerCase();
    }

    public static String upper(Object value) {
        return value == null ? null : value.toString().toUpperCase();
    }

    public static String trim(Object value) {
        return value == null ? null : value.toString().trim();
    }

    public static String concat(Object... parts) {
        return Arrays.stream(parts).map(p -> p == null ? "" : p.toString()).collect(Collectors.joining());
    }

    public static Object coalesce(Object... values) {
        return Arrays.stream(values).filter(Objects::nonNull).findFirst().orElse(null);
    }

    public static boolean oneOf(Object value, Object... options) {
        return Arrays.stream(options).anyMatch(option -> eq(value, option));
    }

    // ---- conversion

    /** Converts a value to the type a field needs: numbers between number types, text to UUID or dates, anything to text. */
    @SuppressWarnings("unchecked")
    public static <T> T as(Object value, Class<T> type) {
        if (value == null || type.isInstance(value)) {
            return (T) value;
        }
        String text = value.toString();
        Object converted;
        if (type == String.class) converted = text;
        else if (type == BigDecimal.class) converted = number(value);
        else if (type == Integer.class) converted = number(value).intValue();
        else if (type == Long.class) converted = number(value).longValue();
        else if (type == Double.class) converted = number(value).doubleValue();
        else if (type == Boolean.class) converted = truthy(value);
        else if (type == UUID.class) converted = UUID.fromString(text);
        else if (type == LocalDate.class) converted = value instanceof OffsetDateTime t ? t.toLocalDate() : LocalDate.parse(text);
        else if (type == OffsetDateTime.class) converted = OffsetDateTime.parse(text);
        else throw new IllegalArgumentException("Cannot convert " + value.getClass().getSimpleName() + " to " + type.getSimpleName());
        return (T) converted;
    }

    @SuppressWarnings("unchecked")
    public static <T> T cast(Object value) {
        return (T) value;
    }

    private static BigDecimal number(Object value) {
        if (value instanceof BigDecimal d) return d;
        if (value instanceof Number n) return new BigDecimal(n.toString());
        return new BigDecimal(value.toString().trim());
    }
}
