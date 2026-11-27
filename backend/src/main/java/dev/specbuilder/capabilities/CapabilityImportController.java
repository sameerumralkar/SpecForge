package dev.specbuilder.capabilities;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Capability imports: upload a file (Excel .xlsx, CSV, TSV or JSON) into a draft, review it against the
 * existing capabilities, edit it, then apply or discard it.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/capability-imports")
public class CapabilityImportController {

    private final CapabilityImportService imports;

    public CapabilityImportController(CapabilityImportService imports) {
        this.imports = imports;
    }

    @GetMapping
    public List<CapabilityImportService.ImportListItem> list(@PathVariable UUID projectId) {
        return imports.list(projectId);
    }

    /** {@code {fileName, content}}, the content as base64. */
    @PostMapping
    public ResponseEntity<CapabilityImportService.ImportView> upload(@PathVariable UUID projectId,
                                                                    @RequestBody CapabilityImportService.Upload upload) {
        CapabilityImportService.ImportView view = imports.upload(projectId, upload);
        return ResponseEntity.created(URI.create("/api/projects/" + projectId + "/capability-imports/" + view.id())).body(view);
    }

    @GetMapping("/{id}")
    public CapabilityImportService.ImportView get(@PathVariable UUID projectId, @PathVariable UUID id) {
        return imports.get(projectId, id);
    }

    /** Saves the edited draft: {@code {targetParentId, nodes}}. */
    @PutMapping("/{id}")
    public CapabilityImportService.ImportView update(@PathVariable UUID projectId, @PathVariable UUID id,
                                                     @RequestBody CapabilityImportService.Update update) {
        return imports.update(projectId, id, update);
    }

    @PostMapping("/{id}/apply")
    public CapabilityImportService.ImportView apply(@PathVariable UUID projectId, @PathVariable UUID id) {
        return imports.apply(projectId, id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> discard(@PathVariable UUID projectId, @PathVariable UUID id) {
        imports.discard(projectId, id);
        return ResponseEntity.noContent().build();
    }
}
