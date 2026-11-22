package dev.specbuilder.domain.rule;

import dev.specbuilder.validation.ValidationErrors;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** If / else-if / else. Each branch becomes its own method or sub-route in the generated code. */
public record ConditionalRouteConfig(
        @NotEmpty(message = "add at least one branch") List<@Valid Branch> branches,
        String otherwise) implements RuleConfig {

    public ConditionalRouteConfig {
        branches = RuleConfig.clean(branches);
    }

    public record Branch(
            @NotBlank @Pattern(regexp = "[a-z][A-Za-z0-9]*", message = "use a lowerCamelCase name, e.g. priorityOrder")
            String name,
            @NotBlank String condition,
            String description) {
    }

    @Override
    public void validate(ValidationErrors errors, String prefix) {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < branches.size(); i++) {
            if (!RuleConfig.blank(branches.get(i).condition())) {
                RuleConfig.checkSyntax(errors, prefix + "branches[" + i + "].condition", branches.get(i).condition());
            }
            String name = branches.get(i).name();
            if (name != null && !seen.add(name.toLowerCase(Locale.ROOT))) {
                errors.add(prefix + "branches[" + i + "].name", "another branch already uses this name");
            }
        }
    }

    @Override
    public String summary(SummaryContext ctx) {
        String ifs = branches.stream()
                .map(b -> "if " + b.condition() + " → " + b.name() + (RuleConfig.blank(b.description()) ? "" : " (" + b.description() + ")"))
                .collect(Collectors.joining("; else "));
        String otherwisePart = RuleConfig.blank(otherwise) ? "otherwise continue" : "otherwise " + otherwise;
        return "Route " + ctx.current() + ": " + ifs + "; " + otherwisePart + ".";
    }
}
