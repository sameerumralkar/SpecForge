package dev.specbuilder.api.dto;

import dev.specbuilder.generator.Problem;

import java.util.List;

/**
 * One generated spec. {@code present} is false when the project has nothing for it (e.g. no async
 * endpoints); {@code valid} means no ERROR problems.
 */
public record SpecView(String id, boolean present, String yaml, String json, boolean valid, List<Problem> problems) {
}
