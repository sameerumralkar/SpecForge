package dev.specbuilder.generator.diagram;

import dev.specbuilder.generator.*;
import dev.specbuilder.generator.ProjectSnapshot.EndpointSnapshot;
import dev.specbuilder.generator.code.CodeGenerator;
import dev.specbuilder.generator.code.CodeModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The project's diagrams, each in Mermaid and PlantUML: the class diagram of the generated code,
 * an overview of the service and what it talks to, and one sequence diagram per endpoint.
 */
@Component
public class DiagramGenerator implements Generator {

    public static final String ID = "diagrams";

    public enum Kind { CLASS, OVERVIEW, SEQUENCE }

    public record Diagram(String id, Kind kind, String title, String endpointName, String mermaid, String plantuml) {
    }

    private final CodeGenerator code;

    public DiagramGenerator(CodeGenerator code) {
        this.code = code;
    }

    @Override
    public String id() {
        return ID;
    }

    public List<Diagram> diagrams(ProjectSnapshot project) {
        CodeModel model = code.model(project);
        DiagramBuilder builder = new DiagramBuilder(project, model);
        List<Diagram> diagrams = new ArrayList<>();
        DiagramModel.Overview overview = builder.overview();
        diagrams.add(new Diagram("overview", Kind.OVERVIEW, "Overview", null,
                MermaidWriter.overview(overview), PlantUmlWriter.overview(overview, project.name() + ": overview")));
        addClasses(diagrams, project, "classes", "Class diagram: everything", builder.classDiagram());
        addClasses(diagrams, project, "classes-model", "Class diagram: data model", builder.classDiagram(builder.modelClasses()));
        List<String> groups = builder.groupNames();
        if (groups.size() > 1) {
            for (String group : groups) {
                addClasses(diagrams, project, "classes-" + group.toLowerCase(java.util.Locale.ROOT), "Class diagram: " + group,
                        builder.classDiagram(builder.groupClasses(group)));
            }
        }
        for (EndpointSnapshot e : project.endpoints()) {
            String title = e.name() + (e.isRest() ? " (" + e.httpMethod() + " " + e.path() + ")" : " (" + e.operation() + " " + e.channel() + ")");
            DiagramModel.Sequence sequence = builder.sequence(e);
            diagrams.add(new Diagram("sequence-" + e.name(), Kind.SEQUENCE, title, e.name(),
                    MermaidWriter.sequence(sequence), PlantUmlWriter.sequence(sequence, title)));
        }
        return diagrams;
    }

    private static void addClasses(List<Diagram> diagrams, ProjectSnapshot project, String id, String title, DiagramModel.ClassDiagram d) {
        diagrams.add(new Diagram(id, Kind.CLASS, title, null, MermaidWriter.classes(d), PlantUmlWriter.classes(d, project.name() + ": " + title)));
    }

    /** Source files only: diagram.mmd and diagram.puml per diagram, in diagrams/. */
    @Override
    public GenerationResult generate(ProjectSnapshot project) {
        List<GeneratedFile> files = new ArrayList<>();
        for (Diagram d : diagrams(project)) {
            files.add(new GeneratedFile("diagrams/" + d.id() + ".mmd", d.mermaid(), "text/vnd.mermaid"));
            files.add(new GeneratedFile("diagrams/" + d.id() + ".puml", d.plantuml(), "text/vnd.plantuml"));
        }
        return new GenerationResult(files, List.of());
    }
}
