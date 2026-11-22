package dev.specbuilder.generator;

/**
 * Something wrong (or worth knowing) about generated output.
 *
 * @param source   who found it, e.g. "openapi-schema" or "swagger-parser"
 * @param location a JSON pointer into the document, or "" for the whole document
 */
public record Problem(Severity severity, String source, String location, String message) {

    public enum Severity { ERROR, WARNING, INFO }

    public static Problem error(String source, String location, String message) {
        return new Problem(Severity.ERROR, source, location, message);
    }

    public static Problem warning(String source, String location, String message) {
        return new Problem(Severity.WARNING, source, location, message);
    }

    public static Problem info(String source, String message) {
        return new Problem(Severity.INFO, source, "", message);
    }
}
