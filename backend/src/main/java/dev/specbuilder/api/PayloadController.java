package dev.specbuilder.api;

import dev.specbuilder.api.dto.PayloadRequest;
import dev.specbuilder.api.dto.PayloadUsage;
import dev.specbuilder.api.dto.PayloadView;
import dev.specbuilder.service.PayloadService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import dev.specbuilder.domain.PayloadRole;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/payloads")
public class PayloadController {

    private final PayloadService payloads;

    public PayloadController(PayloadService payloads) {
        this.payloads = payloads;
    }

    @GetMapping
    public List<PayloadView> list(@PathVariable UUID projectId, @RequestParam(required = false) PayloadRole role) {
        return payloads.list(projectId, role);
    }

    @GetMapping("/{id}")
    public PayloadView get(@PathVariable UUID projectId, @PathVariable UUID id) {
        return payloads.get(projectId, id);
    }

    @PostMapping
    public ResponseEntity<PayloadView> create(@PathVariable UUID projectId, @RequestBody PayloadRequest request) {
        return created(payloads.create(projectId, request));
    }

    @PutMapping("/{id}")
    public PayloadView update(@PathVariable UUID projectId, @PathVariable UUID id, @RequestBody PayloadRequest request) {
        return payloads.update(projectId, id, request);
    }

    /** 409 with the list of usages while anything still refers to the payload. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID projectId, @PathVariable UUID id) {
        payloads.delete(projectId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<PayloadView> duplicate(@PathVariable UUID projectId, @PathVariable UUID id) {
        return created(payloads.duplicate(projectId, id));
    }

    @GetMapping("/{id}/usages")
    public List<PayloadUsage> usages(@PathVariable UUID projectId, @PathVariable UUID id) {
        return payloads.usages(projectId, id);
    }

    private static ResponseEntity<PayloadView> created(PayloadView view) {
        return ResponseEntity.created(URI.create("/api/projects/" + view.projectId() + "/payloads/" + view.id())).body(view);
    }
}
