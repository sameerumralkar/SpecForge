package dev.specbuilder.generator.spec;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Builds ordered maps for documents, leaving out empty values so output stays tidy. */
final class Tree {

    private Tree() {
    }

    static Map<String, Object> obj() {
        return new LinkedHashMap<>();
    }

    /** Puts {@code value} unless it is null, a blank string, or an empty collection or map. */
    static void put(Map<String, Object> map, String key, Object value) {
        if (value == null
                || (value instanceof String s && s.isBlank())
                || (value instanceof Collection<?> c && c.isEmpty())
                || (value instanceof Map<?, ?> m && m.isEmpty())) {
            return;
        }
        map.put(key, value);
    }

    static Map<String, Object> ref(String pointer) {
        Map<String, Object> m = obj();
        m.put("$ref", pointer);
        return m;
    }

    /** Whole numbers are written without ".0" so integer constraints look like integers. */
    static Object number(Double d) {
        if (d == null) {
            return null;
        }
        return d == Math.rint(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15 ? (Object) (long) d.doubleValue() : d;
    }

    /** JSON-pointer escaping for one path segment. */
    static String escape(String segment) {
        return segment.replace("~", "~0").replace("/", "~1");
    }
}
