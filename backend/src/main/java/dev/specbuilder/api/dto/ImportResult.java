package dev.specbuilder.api.dto;

import java.util.List;

/** The project an import created, and what could not be imported exactly. */
public record ImportResult(ProjectView project, List<String> warnings) {
}
