package dev.specbuilder.validation;

import java.util.List;
import java.util.stream.Collectors;

public class ValidationException extends RuntimeException {

    private final List<ValidationErrors.FieldError> errors;

    public ValidationException(List<ValidationErrors.FieldError> errors) {
        super(errors.stream().map(e -> e.field() + ": " + e.message()).collect(Collectors.joining("; ")));
        this.errors = List.copyOf(errors);
    }

    public List<ValidationErrors.FieldError> errors() {
        return errors;
    }
}
