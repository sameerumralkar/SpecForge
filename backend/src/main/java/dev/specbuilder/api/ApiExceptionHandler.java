package dev.specbuilder.api;

import dev.specbuilder.service.CodeNotReadyException;
import dev.specbuilder.service.PayloadInUseException;
import dev.specbuilder.validation.NotFoundException;
import dev.specbuilder.validation.ValidationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every error is an RFC 9457 problem. Validation problems carry "errors": [{field, message}];
 * a blocked payload delete carries "usages".
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ValidationException.class)
    ProblemDetail validation(ValidationException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The request has invalid values.");
        problem.setTitle("Validation failed");
        problem.setProperty("errors", e.errors());
        return problem;
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Not found");
        return problem;
    }

    @ExceptionHandler(PayloadInUseException.class)
    ProblemDetail inUse(PayloadInUseException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Payload in use");
        problem.setProperty("usages", e.usages());
        return problem;
    }

    @ExceptionHandler(CodeNotReadyException.class)
    ProblemDetail codeNotReady(CodeNotReadyException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Code not ready");
        problem.setProperty("problems", e.problems());
        return problem;
    }

    /** A safety net: the services check uniqueness first, so this only fires on a race between two requests. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail conflict(DataIntegrityViolationException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The change conflicts with existing data. Reload and try again.");
        problem.setTitle("Conflict");
        return problem;
    }
}
