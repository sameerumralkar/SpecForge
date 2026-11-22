package dev.specbuilder.api;

import dev.specbuilder.api.dto.DiagramsView;
import dev.specbuilder.generator.diagram.PlantUmlRenderer;
import dev.specbuilder.service.DiagramService;
import dev.specbuilder.service.SpecService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
public class DiagramController {

    private final DiagramService diagrams;
    private final SpecService specs;

    public DiagramController(DiagramService diagrams, SpecService specs) {
        this.diagrams = diagrams;
        this.specs = specs;
    }

    /** Every diagram, as Mermaid and PlantUML source. */
    @GetMapping("/api/projects/{projectId}/diagrams")
    public DiagramsView list(@PathVariable UUID projectId) {
        return diagrams.diagrams(projectId);
    }

    /**
     * One diagram as a file: .mmd or .puml (source), or .svg / .png (rendered by PlantUML).
     * {@code download=false} serves it inline, for showing in the page.
     */
    @GetMapping("/api/projects/{projectId}/diagrams/{diagramId}.{format}")
    public ResponseEntity<byte[]> file(@PathVariable UUID projectId, @PathVariable String diagramId, @PathVariable String format,
                                       @RequestParam(defaultValue = "true") boolean download) {
        byte[] body;
        MediaType type;
        switch (format) {
            case "mmd" -> {
                body = diagrams.diagram(projectId, diagramId).mermaid().getBytes(StandardCharsets.UTF_8);
                type = new MediaType("text", "vnd.mermaid", StandardCharsets.UTF_8);
            }
            case "puml" -> {
                body = diagrams.diagram(projectId, diagramId).plantuml().getBytes(StandardCharsets.UTF_8);
                type = new MediaType("text", "vnd.plantuml", StandardCharsets.UTF_8);
            }
            case "svg" -> {
                body = diagrams.render(projectId, diagramId, PlantUmlRenderer.Format.SVG);
                type = MediaType.parseMediaType("image/svg+xml");
            }
            case "png" -> {
                body = diagrams.render(projectId, diagramId, PlantUmlRenderer.Format.PNG);
                type = MediaType.IMAGE_PNG;
            }
            default -> {
                return ResponseEntity.notFound().build();
            }
        }
        ContentDisposition disposition = download
                ? ContentDisposition.attachment().filename(diagramId + "." + format).build()
                : ContentDisposition.inline().build();
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString()).contentType(type).body(body);
    }

    /** All diagrams: Mermaid and PlantUML sources, and PlantUML-rendered SVG and PNG. */
    @GetMapping("/api/projects/{projectId}/generate/diagrams.zip")
    public ResponseEntity<byte[]> zip(@PathVariable UUID projectId) {
        String name = specs.projectSlug(projectId) + "-diagrams.zip";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(diagrams.zip(projectId));
    }
}
