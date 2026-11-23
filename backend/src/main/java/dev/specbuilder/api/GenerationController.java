package dev.specbuilder.api;

import dev.specbuilder.api.dto.GenerationDiff;
import dev.specbuilder.api.dto.SnapshotView;
import dev.specbuilder.generation.GenerationService;
import dev.specbuilder.generation.GenerationSnapshot;
import dev.specbuilder.service.CodeService;
import dev.specbuilder.service.SpecService;
import dev.specbuilder.validation.ValidationErrors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Snapshots of generated output and diffs against them. Downloads of code and specs take a
 * snapshot automatically; POST takes one on purpose.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/generations")
public class GenerationController {

    public record SnapshotRequest(GenerationSnapshot.Kind kind) {
    }

    private final GenerationService generations;
    private final CodeService code;
    private final SpecService specs;

    public GenerationController(GenerationService generations, CodeService code, SpecService specs) {
        this.generations = generations;
        this.code = code;
        this.specs = specs;
    }

    /** Newest first; at most 10 per kind are kept. */
    @GetMapping
    public List<SnapshotView> list(@PathVariable UUID projectId, @RequestParam GenerationSnapshot.Kind kind) {
        return generations.list(projectId, kind);
    }

    /** Saves the current output as a snapshot (or returns the latest one if nothing changed since). */
    @PostMapping
    public ResponseEntity<SnapshotView> take(@PathVariable UUID projectId, @RequestBody SnapshotRequest request) {
        if (request == null || request.kind() == null) {
            throw ValidationErrors.single("kind", "is required (CODE or SPECS)");
        }
        SnapshotView view = generations.record(projectId, request.kind(), GenerationSnapshot.Reason.MANUAL, current(projectId, request.kind()));
        if (view == null) {
            throw ValidationErrors.single("kind", "the generated files are too large to keep a snapshot of");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }

    /** What changed between a snapshot and the current output. */
    @GetMapping("/{snapshotId}/diff")
    public GenerationDiff diff(@PathVariable UUID projectId, @PathVariable UUID snapshotId) {
        return generations.diff(projectId, snapshotId, kind -> current(projectId, kind));
    }

    private Map<String, String> current(UUID projectId, GenerationSnapshot.Kind kind) {
        return kind == GenerationSnapshot.Kind.CODE ? code.currentFiles(projectId) : specs.currentFiles(projectId);
    }
}
