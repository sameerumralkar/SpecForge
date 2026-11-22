package dev.specbuilder.service;

import dev.specbuilder.api.dto.DiagramsView;
import dev.specbuilder.generator.Problem;
import dev.specbuilder.generator.ProjectSnapshot;
import dev.specbuilder.generator.SnapshotService;
import dev.specbuilder.generator.diagram.DiagramGenerator;
import dev.specbuilder.generator.diagram.PlantUmlRenderer;
import dev.specbuilder.validation.NotFoundException;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DiagramService {

    private static final FileTime FIXED_TIME = FileTime.fromMillis(315532800000L);

    private final SnapshotService snapshots;
    private final DiagramGenerator generator;
    private final PlantUmlRenderer plantUml;

    public DiagramService(SnapshotService snapshots, DiagramGenerator generator, PlantUmlRenderer plantUml) {
        this.snapshots = snapshots;
        this.generator = generator;
        this.plantUml = plantUml;
    }

    public DiagramsView diagrams(UUID projectId) {
        List<DiagramGenerator.Diagram> diagrams = generator.diagrams(snapshots.load(projectId));
        List<Problem> problems = new ArrayList<>();
        for (DiagramGenerator.Diagram d : diagrams) {
            String error = plantUml.syntaxError(d.plantuml());
            if (error != null) problems.add(Problem.error("plantuml", d.id(), error));
        }
        return new DiagramsView(diagrams, problems);
    }

    public DiagramGenerator.Diagram diagram(UUID projectId, String diagramId) {
        return generator.diagrams(snapshots.load(projectId)).stream().filter(d -> d.id().equals(diagramId)).findFirst()
                .orElseThrow(() -> new NotFoundException("Diagram " + diagramId + " was not found"));
    }

    public byte[] render(UUID projectId, String diagramId, PlantUmlRenderer.Format format) {
        return plantUml.render(diagram(projectId, diagramId).plantuml(), format);
    }

    /**
     * diagrams/&lt;id&gt;.mmd and .puml for every diagram, plus PlantUML's images as .plantuml.svg and
     * .plantuml.png. Mermaid renders only in a browser, so the app adds the Mermaid images itself.
     */
    public byte[] zip(UUID projectId) {
        ProjectSnapshot project = snapshots.load(projectId);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(bytes)) {
            for (DiagramGenerator.Diagram d : generator.diagrams(project)) {
                add(zip, d.id() + ".mmd", d.mermaid().getBytes(StandardCharsets.UTF_8));
                add(zip, d.id() + ".puml", d.plantuml().getBytes(StandardCharsets.UTF_8));
                add(zip, d.id() + ".plantuml.svg", plantUml.render(d.plantuml(), PlantUmlRenderer.Format.SVG));
                add(zip, d.id() + ".plantuml.png", plantUml.render(d.plantuml(), PlantUmlRenderer.Format.PNG));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    private static void add(ZipArchiveOutputStream zip, String name, byte[] content) throws IOException {
        ZipArchiveEntry entry = new ZipArchiveEntry("diagrams/" + name);
        entry.setLastModifiedTime(FIXED_TIME);
        zip.putArchiveEntry(entry);
        zip.write(content);
        zip.closeArchiveEntry();
    }
}
