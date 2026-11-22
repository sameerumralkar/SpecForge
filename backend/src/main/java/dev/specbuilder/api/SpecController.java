package dev.specbuilder.api;

import dev.specbuilder.api.dto.CodeView;
import dev.specbuilder.api.dto.SpecsView;
import dev.specbuilder.generator.GeneratedFile;
import dev.specbuilder.service.CodeService;
import dev.specbuilder.service.SpecService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

@RestController
public class SpecController {

    private static final Set<String> FILES = Set.of("openapi.yaml", "openapi.json", "asyncapi.yaml", "asyncapi.json");

    private final SpecService specs;
    private final CodeService code;

    public SpecController(SpecService specs, CodeService code) {
        this.specs = specs;
        this.code = code;
    }

    /** The generated Maven project, file by file, with any problems that block the download. */
    @GetMapping("/api/projects/{projectId}/code")
    public CodeView code(@PathVariable UUID projectId) {
        return code.preview(projectId);
    }

    /** The generated Maven project as a zip; 409 with the problems while there are errors. */
    @GetMapping("/api/projects/{projectId}/generate/code.zip")
    public ResponseEntity<byte[]> codeZip(@PathVariable UUID projectId) {
        byte[] zip = code.zip(projectId);
        String name = specs.projectSlug(projectId) + ".zip";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(zip);
    }

    /** Both specs, as YAML and JSON, with validation problems. */
    @GetMapping("/api/projects/{projectId}/specs")
    public SpecsView specs(@PathVariable UUID projectId) {
        return specs.specs(projectId);
    }

    @GetMapping("/api/projects/{projectId}/specs/{file:.+}")
    public ResponseEntity<String> file(@PathVariable UUID projectId, @PathVariable String file) {
        GeneratedFile generated = FILES.contains(file) ? specs.file(projectId, file) : null;
        if (generated == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file).build().toString())
                .contentType(new MediaType(MediaType.parseMediaType(generated.mediaType()), StandardCharsets.UTF_8))
                .body(generated.content());
    }

    /** openapi.yaml/json and asyncapi.yaml/json in one zip. */
    @GetMapping("/api/projects/{projectId}/generate/specs.zip")
    public ResponseEntity<byte[]> zip(@PathVariable UUID projectId) {
        byte[] zip = specs.zip(projectId);
        String name = specs.projectSlug(projectId) + "-specs.zip";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(zip);
    }
}
