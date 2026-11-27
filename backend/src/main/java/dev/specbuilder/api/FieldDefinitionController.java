package dev.specbuilder.api;

import dev.specbuilder.api.dto.FieldDefinitionView;
import dev.specbuilder.api.dto.FieldDto;
import dev.specbuilder.service.FieldDefinitionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** The field inventory: reusable field definitions to add to payloads. */
@RestController
@RequestMapping("/api/projects/{projectId}/field-definitions")
public class FieldDefinitionController {

    private final FieldDefinitionService definitions;

    public FieldDefinitionController(FieldDefinitionService definitions) {
        this.definitions = definitions;
    }

    @GetMapping
    public List<FieldDefinitionView> list(@PathVariable UUID projectId) {
        return definitions.list(projectId);
    }

    @GetMapping("/{id}")
    public FieldDefinitionView get(@PathVariable UUID projectId, @PathVariable UUID id) {
        return definitions.get(projectId, id);
    }

    @PostMapping
    public ResponseEntity<FieldDefinitionView> create(@PathVariable UUID projectId, @RequestBody FieldDto request) {
        FieldDefinitionView view = definitions.create(projectId, request);
        return ResponseEntity.created(URI.create("/api/projects/" + projectId + "/field-definitions/" + view.id())).body(view);
    }

    @PutMapping("/{id}")
    public FieldDefinitionView update(@PathVariable UUID projectId, @PathVariable UUID id, @RequestBody FieldDto request) {
        return definitions.update(projectId, id, request);
    }

    /** Payload fields copied from the definition stay as they are. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID projectId, @PathVariable UUID id) {
        definitions.delete(projectId, id);
        return ResponseEntity.noContent().build();
    }
}
