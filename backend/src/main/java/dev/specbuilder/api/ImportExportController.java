package dev.specbuilder.api;

import dev.specbuilder.api.dto.ImportResult;
import dev.specbuilder.api.dto.ProjectView;
import dev.specbuilder.api.dto.SpecImportRequest;
import dev.specbuilder.importexport.ProjectExport;
import dev.specbuilder.importexport.ProjectImportExportService;
import dev.specbuilder.importexport.SpecImporter;
import dev.specbuilder.service.SpecService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
public class ImportExportController {

    private final ProjectImportExportService service;
    private final SpecService specs;

    public ImportExportController(ProjectImportExportService service, SpecService specs) {
        this.service = service;
        this.specs = specs;
    }

    /** The whole project as a portable JSON file. */
    @GetMapping("/api/projects/{projectId}/export")
    public ResponseEntity<ProjectExport> export(@PathVariable UUID projectId) {
        String name = specs.projectSlug(projectId) + ".specbuilder.json";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .body(service.export(projectId));
    }

    /** A new project from an exported file. {@code name} overrides the project name in the file. */
    @PostMapping("/api/projects/import")
    public ResponseEntity<ImportResult> importProject(@RequestBody ProjectExport export, @RequestParam(required = false) String name) {
        return created(new ImportResult(service.importProject(export, name), List.of()));
    }

    /** A new project seeded from OpenAPI and/or AsyncAPI documents. */
    @PostMapping("/api/projects/import/spec")
    public ResponseEntity<ImportResult> importSpec(@RequestBody SpecImportRequest request) {
        SpecImporter.Result result = SpecImporter.read(request.documents(), request.name(), request.basePackage());
        return created(new ImportResult(service.importProject(result.export(), null), result.warnings()));
    }

    private static ResponseEntity<ImportResult> created(ImportResult result) {
        ProjectView p = result.project();
        return ResponseEntity.status(HttpStatus.CREATED).location(URI.create("/api/projects/" + p.id())).body(result);
    }
}
