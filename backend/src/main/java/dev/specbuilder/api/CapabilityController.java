package dev.specbuilder.api;

import dev.specbuilder.api.dto.*;
import dev.specbuilder.service.CapabilityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** The functional capability model (L1 to L8), and the APIs linked to each capability. */
@RestController
@RequestMapping("/api/projects/{projectId}")
public class CapabilityController {

    private final CapabilityService capabilities;

    public CapabilityController(CapabilityService capabilities) {
        this.capabilities = capabilities;
    }

    @GetMapping("/capability-levels")
    public CapabilityLevels levels(@PathVariable UUID projectId) {
        return capabilities.levels(projectId);
    }

    /** Eight names, or an empty list for the defaults. */
    @PutMapping("/capability-levels")
    public CapabilityLevels setLevels(@PathVariable UUID projectId, @RequestBody CapabilityLevels request) {
        return capabilities.setLevels(projectId, request);
    }

    /** Every capability, in tree order. */
    @GetMapping("/capabilities")
    public List<CapabilityView> list(@PathVariable UUID projectId) {
        return capabilities.list(projectId);
    }

    @GetMapping("/capabilities/{id}")
    public CapabilityView get(@PathVariable UUID projectId, @PathVariable UUID id) {
        return capabilities.get(projectId, id);
    }

    @PostMapping("/capabilities")
    public ResponseEntity<CapabilityView> create(@PathVariable UUID projectId, @RequestBody CapabilityRequest request) {
        CapabilityView view = capabilities.create(projectId, request);
        return ResponseEntity.created(URI.create("/api/projects/" + projectId + "/capabilities/" + view.id())).body(view);
    }

    @PutMapping("/capabilities/{id}")
    public CapabilityView update(@PathVariable UUID projectId, @PathVariable UUID id, @RequestBody CapabilityRequest request) {
        return capabilities.update(projectId, id, request);
    }

    /** Also deletes every capability below it. The APIs stay. */
    @DeleteMapping("/capabilities/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID projectId, @PathVariable UUID id) {
        capabilities.delete(projectId, id);
        return ResponseEntity.noContent().build();
    }

    /** The API inventory at this capability: its own APIs and those of every capability below it. */
    @GetMapping("/capabilities/{id}/apis")
    public List<CapabilityApi> apis(@PathVariable UUID projectId, @PathVariable UUID id) {
        return capabilities.apis(projectId, id);
    }

    /** Replaces the APIs linked directly to the capability. */
    @PutMapping("/capabilities/{id}/apis")
    public CapabilityView setApis(@PathVariable UUID projectId, @PathVariable UUID id, @RequestBody IdList request) {
        return capabilities.setApis(projectId, id, request);
    }

    @GetMapping("/endpoints/{endpointId}/capabilities")
    public List<CapabilityView> ofEndpoint(@PathVariable UUID projectId, @PathVariable UUID endpointId) {
        return capabilities.ofEndpoint(projectId, endpointId);
    }

    /** Links the API to exactly these capabilities. */
    @PutMapping("/endpoints/{endpointId}/capabilities")
    public List<CapabilityView> setEndpointCapabilities(@PathVariable UUID projectId, @PathVariable UUID endpointId,
                                                        @RequestBody IdList request) {
        return capabilities.setEndpointCapabilities(projectId, endpointId, request);
    }
}
