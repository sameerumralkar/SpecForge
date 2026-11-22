package dev.specbuilder.api;

import dev.specbuilder.api.dto.EndpointRequest;
import dev.specbuilder.api.dto.EndpointView;
import dev.specbuilder.service.EndpointService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/endpoints")
public class EndpointController {

    private final EndpointService endpoints;

    public EndpointController(EndpointService endpoints) {
        this.endpoints = endpoints;
    }

    @GetMapping
    public List<EndpointView> list(@PathVariable UUID projectId) {
        return endpoints.list(projectId);
    }

    @GetMapping("/{id}")
    public EndpointView get(@PathVariable UUID projectId, @PathVariable UUID id) {
        return endpoints.get(projectId, id);
    }

    @PostMapping
    public ResponseEntity<EndpointView> create(@PathVariable UUID projectId, @RequestBody EndpointRequest request) {
        return created(endpoints.create(projectId, request));
    }

    @PutMapping("/{id}")
    public EndpointView update(@PathVariable UUID projectId, @PathVariable UUID id, @RequestBody EndpointRequest request) {
        return endpoints.update(projectId, id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID projectId, @PathVariable UUID id) {
        endpoints.delete(projectId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<EndpointView> duplicate(@PathVariable UUID projectId, @PathVariable UUID id) {
        return created(endpoints.duplicate(projectId, id));
    }

    private static ResponseEntity<EndpointView> created(EndpointView view) {
        return ResponseEntity.created(URI.create("/api/projects/" + view.projectId() + "/endpoints/" + view.id())).body(view);
    }
}
