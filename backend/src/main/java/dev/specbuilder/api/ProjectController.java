package dev.specbuilder.api;

import dev.specbuilder.api.dto.ProjectRequest;
import dev.specbuilder.api.dto.ProjectView;
import dev.specbuilder.seed.SampleProjects;
import dev.specbuilder.service.ProjectService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projects;
    private final SampleProjects samples;

    public ProjectController(ProjectService projects, SampleProjects samples) {
        this.projects = projects;
        this.samples = samples;
    }

    @GetMapping
    public List<ProjectView> list() {
        return projects.list();
    }

    @GetMapping("/{id}")
    public ProjectView get(@PathVariable UUID id) {
        return projects.get(id);
    }

    @PostMapping
    public ResponseEntity<ProjectView> create(@RequestBody ProjectRequest request) {
        ProjectView created = projects.create(request);
        return ResponseEntity.created(URI.create("/api/projects/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public ProjectView update(@PathVariable UUID id, @RequestBody ProjectRequest request) {
        return projects.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        projects.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Creates a fresh copy of the "Order Service" sample project. */
    @PostMapping("/samples/order-service")
    public ResponseEntity<ProjectView> createSample() {
        ProjectView created = samples.createOrderService();
        return ResponseEntity.created(URI.create("/api/projects/" + created.id())).body(created);
    }
}
