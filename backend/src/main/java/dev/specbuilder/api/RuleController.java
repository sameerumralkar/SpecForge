package dev.specbuilder.api;

import dev.specbuilder.api.dto.RuleChainView;
import dev.specbuilder.api.dto.RuleOrderRequest;
import dev.specbuilder.api.dto.RuleRequest;
import dev.specbuilder.api.dto.RuleView;
import dev.specbuilder.service.RuleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/endpoints/{endpointId}/rules")
public class RuleController {

    private final RuleService rules;

    public RuleController(RuleService rules) {
        this.rules = rules;
    }

    /** The whole chain: rules in order, a numbered summary and warnings. */
    @GetMapping
    public RuleChainView chain(@PathVariable UUID projectId, @PathVariable UUID endpointId) {
        return rules.chain(projectId, endpointId);
    }

    @GetMapping("/{ruleId}")
    public RuleView get(@PathVariable UUID projectId, @PathVariable UUID endpointId, @PathVariable UUID ruleId) {
        return rules.get(projectId, endpointId, ruleId);
    }

    @PostMapping
    public ResponseEntity<RuleView> create(@PathVariable UUID projectId, @PathVariable UUID endpointId,
                                           @RequestBody RuleRequest request) {
        RuleView view = rules.create(projectId, endpointId, request);
        return ResponseEntity.created(URI.create("/api/projects/" + projectId + "/endpoints/" + endpointId + "/rules/" + view.id()))
                .body(view);
    }

    @PutMapping("/{ruleId}")
    public RuleView update(@PathVariable UUID projectId, @PathVariable UUID endpointId, @PathVariable UUID ruleId,
                           @RequestBody RuleRequest request) {
        return rules.update(projectId, endpointId, ruleId, request);
    }

    @DeleteMapping("/{ruleId}")
    public ResponseEntity<Void> delete(@PathVariable UUID projectId, @PathVariable UUID endpointId, @PathVariable UUID ruleId) {
        rules.delete(projectId, endpointId, ruleId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/order")
    public RuleChainView reorder(@PathVariable UUID projectId, @PathVariable UUID endpointId,
                                 @RequestBody RuleOrderRequest request) {
        return rules.reorder(projectId, endpointId, request);
    }
}
