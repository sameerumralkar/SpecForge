package dev.specbuilder.service;

import dev.specbuilder.domain.rule.RuleConfig;
import dev.specbuilder.domain.rule.RuleType;
import dev.specbuilder.validation.ValidationErrors;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Comparator;

/** Reads, checks and writes rule configs. Stored JSON is always written by this class, so it is canonical. */
@Component
public class RuleConfigCodec {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final JsonMapper strict = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    private final Validator validator;

    public RuleConfigCodec(Validator validator) {
        this.validator = validator;
    }

    /** Parses and validates client input. Adds problems to {@code errors} under "config..." and returns null if it cannot parse. */
    public RuleConfig parse(RuleType type, JsonNode node, ValidationErrors errors) {
        JsonNode input = node == null || node.isNull() || node.isMissingNode() ? mapper.createObjectNode() : node;
        if (!input.isObject()) {
            errors.add("config", "must be a JSON object");
            return null;
        }
        RuleConfig config;
        try {
            config = strict.treeToValue(input, type.configClass());
        } catch (JacksonException e) {
            errors.add("config" + path(e), firstLine(e.getOriginalMessage()));
            return null;
        }
        validator.validate(config).stream()
                .sorted(Comparator.comparing((ConstraintViolation<RuleConfig> v) -> v.getPropertyPath().toString()))
                .forEach(v -> errors.add("config." + v.getPropertyPath(), v.getMessage()));
        config.validate(errors, "config.");
        return config;
    }

    public String write(RuleConfig config) {
        return mapper.writeValueAsString(config);
    }

    public RuleConfig read(RuleType type, String json) {
        return mapper.readValue(json, type.configClass());
    }

    public JsonNode tree(String json) {
        return mapper.readTree(json);
    }

    private static String path(JacksonException e) {
        StringBuilder sb = new StringBuilder();
        for (JacksonException.Reference ref : e.getPath()) {
            if (ref.getPropertyName() != null) {
                sb.append('.').append(ref.getPropertyName());
            } else if (ref.getIndex() >= 0) {
                sb.append('[').append(ref.getIndex()).append(']');
            }
        }
        return sb.toString();
    }

    private static String firstLine(String s) {
        if (s == null) {
            return "is not valid";
        }
        int nl = s.indexOf('\n');
        return nl < 0 ? s : s.substring(0, nl);
    }
}
