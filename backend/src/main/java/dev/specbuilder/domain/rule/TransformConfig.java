package dev.specbuilder.domain.rule;

import dev.specbuilder.validation.ValidationErrors;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Maps the current payload onto another payload, which becomes the current payload for later steps.
 * Fields with the same name are mapped automatically; {@code mappings} lists the rest.
 */
public record TransformConfig(
        @NotNull(message = "choose the payload to map to") UUID targetPayloadId,
        List<@Valid FieldMapping> mappings) implements RuleConfig {

    public TransformConfig {
        mappings = RuleConfig.clean(mappings);
    }

    /** Sets {@code target} from a field path ({@code source}) or from an expression, never both. */
    public record FieldMapping(@NotBlank String target, String source, String expression) {
    }

    @Override
    public void validate(ValidationErrors errors, String prefix) {
        for (int i = 0; i < mappings.size(); i++) {
            FieldMapping m = mappings.get(i);
            if (RuleConfig.blank(m.source()) == RuleConfig.blank(m.expression())) {
                errors.add(prefix + "mappings[" + i + "]", "give either a source field or an expression");
            } else if (!RuleConfig.blank(m.expression())) {
                RuleConfig.checkSyntax(errors, prefix + "mappings[" + i + "].expression", m.expression());
            }
        }
    }

    @Override
    public Set<UUID> payloadRefs() {
        return targetPayloadId == null ? Set.of() : Set.of(targetPayloadId);
    }

    @Override
    public String summary(SummaryContext ctx) {
        String extra = mappings.isEmpty()
                ? "matching fields by name"
                : mappings.size() + " explicit mapping" + (mappings.size() == 1 ? "" : "s") + ", other fields by name";
        return "Map " + ctx.current() + " to " + ctx.payload(targetPayloadId) + " (" + extra + ").";
    }
}
