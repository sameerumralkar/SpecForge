package dev.specbuilder.domain.rule;

import dev.specbuilder.domain.HttpMethod;
import dev.specbuilder.validation.ValidationErrors;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.Set;
import java.util.UUID;

/** Calls another service and stores the result in a field of the current payload. */
public record EnrichConfig(
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9-]*", message = "use lower-case letters, digits and dashes, e.g. customer-service")
        String serviceName,
        HttpMethod httpMethod,
        @NotBlank String url,
        UUID responsePayloadId,
        String targetField,
        @Min(100) @Max(120000) Integer timeoutMs,
        Mode mode) implements RuleConfig {

    public enum Mode { ENRICH, POLL_ENRICH }

    public EnrichConfig {
        httpMethod = httpMethod == null ? HttpMethod.GET : httpMethod;
        timeoutMs = timeoutMs == null ? 2000 : timeoutMs;
        mode = mode == null ? Mode.ENRICH : mode;
    }

    @Override
    public void validate(ValidationErrors errors, String prefix) {
        if (url != null && !url.isBlank()
                && !(url.startsWith("http://") || url.startsWith("https://") || url.startsWith("/") || url.startsWith("${"))) {
            errors.add(prefix + "url", "start with http://, https://, / (relative to the service's base URL) or a ${property}");
        }
    }

    @Override
    public Set<UUID> payloadRefs() {
        return responsePayloadId == null ? Set.of() : Set.of(responsePayloadId);
    }

    @Override
    public String summary(SummaryContext ctx) {
        String result = responsePayloadId == null ? "the response" : "the " + ctx.payload(responsePayloadId);
        String where = RuleConfig.blank(targetField) ? "" : " and put " + result + " into " + targetField;
        return "Call " + serviceName + " (" + httpMethod + " " + url + ", timeout " + timeoutMs + " ms)" + where + ".";
    }
}
