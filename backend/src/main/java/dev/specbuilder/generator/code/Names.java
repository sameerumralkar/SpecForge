package dev.specbuilder.generator.code;

import dev.specbuilder.validation.IdentifierRules;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Turns free text into Java names and literals. */
public final class Names {

    private Names() {
    }

    /** Words of a name: splits on non-alphanumerics and on lower→upper case changes. */
    static List<String> words(String text) {
        List<String> words = new ArrayList<>();
        StringBuilder w = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isLetterOrDigit(c) || c > 127) {
                if (!w.isEmpty()) { words.add(w.toString()); w.setLength(0); }
                continue;
            }
            if (Character.isUpperCase(c) && !w.isEmpty() && Character.isLowerCase(w.charAt(w.length() - 1))) {
                words.add(w.toString());
                w.setLength(0);
            }
            w.append(c);
        }
        if (!w.isEmpty()) words.add(w.toString());
        return words;
    }

    /** "order service" → "OrderService"; never empty, never starts with a digit. */
    public static String pascal(String text) {
        StringBuilder sb = new StringBuilder();
        for (String w : words(text)) {
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        if (sb.isEmpty()) return "Api";
        if (Character.isDigit(sb.charAt(0))) sb.insert(0, 'X');
        return sb.toString();
    }

    /** "Enrich customer" → "enrichCustomer"; Java keywords get a trailing underscore. */
    public static String camel(String text) {
        String p = pascal(text);
        String c = Character.toLowerCase(p.charAt(0)) + p.substring(1);
        return IdentifierRules.isKeyword(c) ? c + "_" : c;
    }

    /** "Order Service" → "order-service". */
    public static String kebab(String text) {
        String k = String.join("-", words(text)).toLowerCase(Locale.ROOT);
        return k.isEmpty() ? "app" : k;
    }

    /** "OrderEntity" → "order_entity". */
    public static String snake(String text) {
        return String.join("_", words(text)).toLowerCase(Locale.ROOT);
    }

    public static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public static String getter(String field) {
        return "get" + capitalize(field);
    }

    public static String setter(String field) {
        return "set" + capitalize(field);
    }

    /** A Java string literal, quotes included. */
    public static String literal(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }

    /** Keeps names unique within one Java class (methods, or fields). */
    static final class Registry {
        private final Set<String> taken = new HashSet<>();

        Registry(String... reserved) {
            taken.addAll(List.of(reserved));
        }

        String claim(String wanted) {
            String name = wanted;
            for (int n = 2; !taken.add(name); n++) {
                name = wanted + n;
            }
            return name;
        }

        void reserve(String name) {
            taken.add(name);
        }
    }
}
