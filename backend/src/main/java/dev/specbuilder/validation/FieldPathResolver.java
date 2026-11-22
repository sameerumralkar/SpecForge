package dev.specbuilder.validation;

import dev.specbuilder.domain.FieldType;
import dev.specbuilder.domain.Payload;
import dev.specbuilder.domain.PayloadField;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves a dotted field path against a payload. {@code customer.email} steps into an OBJECT field;
 * {@code items[].quantity} steps into the items of an ARRAY field.
 */
public final class FieldPathResolver {

    private static final Pattern SEGMENT = Pattern.compile("([A-Za-z_$][A-Za-z0-9_$]*)(\\[])?");

    /** The resolved field, whether the path ends inside its array items, or an error. */
    public record Resolution(PayloadField field, boolean intoItems, String error) {

        public boolean ok() {
            return error == null;
        }

        /** The type of the value the path points at. */
        public FieldType valueType() {
            return intoItems ? field.getItemType() : field.getType();
        }

        static Resolution failed(String error) {
            return new Resolution(null, false, error);
        }
    }

    private FieldPathResolver() {
    }

    public static Resolution resolve(Payload root, String path) {
        if (root == null) {
            return Resolution.failed("there is no payload to look up '" + path + "' in");
        }
        if (path == null || path.isBlank()) {
            return Resolution.failed("the field path is empty");
        }
        String[] segments = path.split("\\.", -1);
        Payload current = root;
        PayloadField field = null;
        boolean intoItems = false;
        for (int i = 0; i < segments.length; i++) {
            Matcher m = SEGMENT.matcher(segments[i]);
            if (!m.matches()) {
                return Resolution.failed("'" + segments[i] + "' is not a valid path segment");
            }
            String name = m.group(1);
            intoItems = m.group(2) != null;
            if (current == null) {
                return Resolution.failed("'" + field.getName() + "' is not an object, so it has no field '" + name + "'");
            }
            field = find(current, name);
            if (field == null) {
                return Resolution.failed("'" + name + "' is not a field of " + current.getName());
            }
            if (intoItems && field.getType() != FieldType.ARRAY) {
                return Resolution.failed("'" + name + "' is not an array, so '" + name + "[]' is not valid");
            }
            boolean last = i == segments.length - 1;
            if (!last && field.getType() == FieldType.ARRAY && !intoItems) {
                return Resolution.failed("'" + name + "' is an array: write '" + name + "[]' to step into its items");
            }
            current = field.elementType() == FieldType.OBJECT ? field.getRefPayload() : null;
        }
        return new Resolution(field, intoItems, null);
    }

    private static PayloadField find(Payload payload, String name) {
        for (PayloadField f : payload.getFields()) {
            if (f.getName().equals(name)) {
                return f;
            }
        }
        return null;
    }
}
