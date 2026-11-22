package dev.specbuilder.validation;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Names that end up in generated Java code must be legal there. These checks are stricter than
 * the Java spec on purpose (ASCII only, no '$'), so generated code is portable and readable.
 */
public final class IdentifierRules {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern TYPE_NAME = Pattern.compile("[A-Z][A-Za-z0-9]*");
    private static final Pattern MEMBER_NAME = Pattern.compile("[a-z][A-Za-z0-9]*");
    private static final Pattern PACKAGE_SEGMENT = Pattern.compile("[a-z][a-z0-9_]*");

    public static final Set<String> JAVA_KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
            "volatile", "while", "true", "false", "null", "var", "yield", "record", "sealed", "permits",
            "when", "module", "open", "exports", "requires", "_");

    /**
     * Class names that generated code uses without a package; a payload with one of these names
     * would shadow them and the generated project would not compile.
     */
    public static final Set<String> RESERVED_TYPE_NAMES = Set.of(
            "Object", "String", "Integer", "Long", "Double", "Float", "Short", "Byte", "Character", "Boolean",
            "Number", "Math", "System", "Class", "Enum", "Record", "Void", "Exception", "RuntimeException",
            "Error", "Throwable", "Thread", "Iterable", "Override", "Deprecated", "SuppressWarnings",
            "List", "Map", "Set", "Optional", "Collection", "UUID", "LocalDate", "LocalDateTime",
            "OffsetDateTime", "Instant", "BigDecimal", "BigInteger", "Objects", "Collectors", "Stream",
            "Application", "ApiError", "ErrorResponse", "ResponseEntity", "HttpStatus");

    private IdentifierRules() {
    }

    public static boolean isKeyword(String s) {
        return JAVA_KEYWORDS.contains(s);
    }

    /** Any Java identifier, e.g. a field name. Returns an error message or null. */
    public static String checkIdentifier(String s) {
        if (s == null || s.isBlank()) {
            return "is required";
        }
        if (!IDENTIFIER.matcher(s).matches()) {
            return "must start with a letter or '_' and contain only letters, digits and '_'";
        }
        if (isKeyword(s)) {
            return "'" + s + "' is a reserved word in Java";
        }
        return null;
    }

    /** An UpperCamelCase class name, e.g. a payload name. */
    public static String checkTypeName(String s) {
        if (s == null || s.isBlank()) {
            return "is required";
        }
        if (!TYPE_NAME.matcher(s).matches()) {
            return "must be UpperCamelCase: start with a capital letter, then letters and digits only";
        }
        if (RESERVED_TYPE_NAMES.contains(s)) {
            return "'" + s + "' clashes with a Java class the generated code uses; pick another name";
        }
        return null;
    }

    /** A lowerCamelCase method name, e.g. an endpoint's operation id. */
    public static String checkMemberName(String s) {
        if (s == null || s.isBlank()) {
            return "is required";
        }
        if (!MEMBER_NAME.matcher(s).matches()) {
            return "must be lowerCamelCase: start with a lower-case letter, then letters and digits only";
        }
        if (isKeyword(s)) {
            return "'" + s + "' is a reserved word in Java";
        }
        return null;
    }

    public static String checkPackageName(String s) {
        if (s == null || s.isBlank()) {
            return "is required";
        }
        for (String segment : s.split("\\.", -1)) {
            if (!PACKAGE_SEGMENT.matcher(segment).matches()) {
                return "must be dot-separated lower-case segments, e.g. com.example.orders";
            }
            if (isKeyword(segment)) {
                return "'" + segment + "' is a reserved word in Java";
            }
        }
        return null;
    }
}
