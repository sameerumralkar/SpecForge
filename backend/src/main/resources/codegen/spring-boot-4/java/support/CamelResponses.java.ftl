package ${project.basePackage}.support;

import ${project.basePackage}.error.RuleViolationException;
import org.apache.camel.Exchange;
import org.springframework.http.ResponseEntity;

/** Turns the outcome of a route into an HTTP response. */
public final class CamelResponses {

    private CamelResponses() {
    }

    public static <T> ResponseEntity<T> respond(Exchange result, int status, Class<T> type) {
        Exception failure = result.getException();
        if (failure != null) {
            throw failure instanceof RuntimeException runtime ? runtime : new IllegalStateException(failure);
        }
        Integer code = result.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
        if (code != null && code >= 400) {
            throw new RuleViolationException(code, result.getMessage().getBody(String.class));
        }
        if (type == Void.class) {
            return ResponseEntity.status(status).build();
        }
        return ResponseEntity.status(status).body(result.getMessage().getBody(type));
    }
}
