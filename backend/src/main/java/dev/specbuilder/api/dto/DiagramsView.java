package dev.specbuilder.api.dto;

import dev.specbuilder.generator.Problem;
import dev.specbuilder.generator.diagram.DiagramGenerator;

import java.util.List;

/** Every diagram of a project in both notations, plus any problems (e.g. a PlantUML syntax error). */
public record DiagramsView(List<DiagramGenerator.Diagram> diagrams, List<Problem> problems) {
}
