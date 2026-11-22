package dev.specbuilder.validation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses REST path templates such as {@code /orders/{orderId}/items}. */
public final class PathTemplate {

    private static final Pattern PARAM_SEGMENT = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_]*)}");
    private static final Pattern LITERAL_SEGMENT = Pattern.compile("[A-Za-z0-9._~-]+");

    public record Parsed(List<String> errors, Set<String> parameterNames, String key) {
        public boolean valid() {
            return errors.isEmpty();
        }
    }

    private PathTemplate() {
    }

    public static Parsed parse(String path) {
        List<String> errors = new ArrayList<>();
        Set<String> params = new LinkedHashSet<>();
        if (path == null || path.isBlank()) {
            return new Parsed(List.of("is required"), params, null);
        }
        if (!path.startsWith("/")) {
            errors.add("must start with '/'");
        }
        if (path.length() > 1 && path.endsWith("/")) {
            errors.add("must not end with '/'");
        }
        StringBuilder key = new StringBuilder();
        String body = path.startsWith("/") ? path.substring(1) : path;
        if (!body.isEmpty()) {
            for (String segment : body.split("/", -1)) {
                if (segment.isEmpty()) {
                    if (!path.endsWith("/")) {
                        errors.add("must not contain '//'");
                    }
                    continue;
                }
                Matcher m = PARAM_SEGMENT.matcher(segment);
                if (m.matches()) {
                    if (!params.add(m.group(1))) {
                        errors.add("uses {" + m.group(1) + "} more than once");
                    }
                    key.append("/{}");
                } else if (LITERAL_SEGMENT.matcher(segment).matches()) {
                    key.append('/').append(segment);
                } else {
                    errors.add("segment '" + segment + "' is not valid: use letters, digits, '-', '.', '_', '~' or a whole-segment {param}");
                }
            }
        }
        return new Parsed(errors, params, key.isEmpty() ? "/" : key.toString());
    }
}
