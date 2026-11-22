package dev.specbuilder.validation;

import java.util.ArrayList;
import java.util.List;

/** Collects every problem with a request so the client sees them all at once. */
public final class ValidationErrors {

    public record FieldError(String field, String message) {
    }

    private final List<FieldError> errors = new ArrayList<>();

    public void add(String field, String message) {
        errors.add(new FieldError(field, message));
    }

    /** Adds an error if {@code value} is longer than {@code max} characters. */
    public void maxLength(String field, String value, int max) {
        if (value != null && value.length() > max) {
            add(field, "must be at most " + max + " characters");
        }
    }

    public boolean isEmpty() {
        return errors.isEmpty();
    }

    public List<FieldError> list() {
        return List.copyOf(errors);
    }

    public void throwIfAny() {
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    public static String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    public static ValidationException single(String field, String message) {
        return new ValidationException(List.of(new FieldError(field, message)));
    }
}
