package ${project.basePackage}.error;

<#if features.camel>
import org.apache.camel.CamelExecutionException;
</#if>
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Turns the exceptions rules throw into RFC 9457 problem responses. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(RuleViolationException.class)
    ProblemDetail ruleViolation(RuleViolationException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(e.getStatus()), e.getMessage());
        if (e.getCode() != null) {
            problem.setProperty("code", e.getCode());
        }
        return problem;
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(DownstreamUnavailableException.class)
    ProblemDetail downstream(DownstreamUnavailableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    @ExceptionHandler(DownstreamTimeoutException.class)
    ProblemDetail timeout(DownstreamTimeoutException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.GATEWAY_TIMEOUT, e.getMessage());
    }

    @ExceptionHandler({PersistenceFailedException.class, MessagingFailedException.class})
    ProblemDetail infrastructure(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }
<#if features.camel>

    /** Routes report failures wrapped by Camel; answer for the original problem. */
    @ExceptionHandler(CamelExecutionException.class)
    ProblemDetail camel(CamelExecutionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof RuleViolationException r) return ruleViolation(r);
        if (cause instanceof NotFoundException n) return notFound(n);
        if (cause instanceof DownstreamUnavailableException d) return downstream(d);
        if (cause instanceof DownstreamTimeoutException t) return timeout(t);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, String.valueOf(cause == null ? e.getMessage() : cause.getMessage()));
    }
</#if>
}
