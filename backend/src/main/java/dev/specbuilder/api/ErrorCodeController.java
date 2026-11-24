package dev.specbuilder.api;

import dev.specbuilder.api.dto.ErrorCodeRequest;
import dev.specbuilder.api.dto.ErrorCodeView;
import dev.specbuilder.api.dto.ErrorLinksRequest;
import dev.specbuilder.service.ErrorCodeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** The error-code inventory: codes, messages and statuses, and the fields and rules each one is linked to. */
@RestController
@RequestMapping("/api/projects/{projectId}/error-codes")
public class ErrorCodeController {

    private final ErrorCodeService codes;

    public ErrorCodeController(ErrorCodeService codes) {
        this.codes = codes;
    }

    @GetMapping
    public List<ErrorCodeView> list(@PathVariable UUID projectId) {
        return codes.list(projectId);
    }

    @GetMapping("/{id}")
    public ErrorCodeView get(@PathVariable UUID projectId, @PathVariable UUID id) {
        return codes.get(projectId, id);
    }

    @PostMapping
    public ResponseEntity<ErrorCodeView> create(@PathVariable UUID projectId, @RequestBody ErrorCodeRequest request) {
        ErrorCodeView view = codes.create(projectId, request);
        return ResponseEntity.created(URI.create("/api/projects/" + projectId + "/error-codes/" + view.id())).body(view);
    }

    @PutMapping("/{id}")
    public ErrorCodeView update(@PathVariable UUID projectId, @PathVariable UUID id, @RequestBody ErrorCodeRequest request) {
        return codes.update(projectId, id, request);
    }

    /** Also unlinks the code from every field, rule and check. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID projectId, @PathVariable UUID id) {
        codes.delete(projectId, id);
        return ResponseEntity.noContent().build();
    }

    /** Replaces the fields and rules the code is linked to. */
    @PutMapping("/{id}/links")
    public ErrorCodeView setLinks(@PathVariable UUID projectId, @PathVariable UUID id, @RequestBody ErrorLinksRequest request) {
        return codes.setLinks(projectId, id, request);
    }
}
