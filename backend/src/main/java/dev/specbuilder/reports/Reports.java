package dev.specbuilder.reports;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The shapes of the four reports. Every report covers all projects, or one when a project is chosen. */
public final class Reports {

    private Reports() {
    }

    // ---- 1. Projects ----

    public record ProjectsReport(ProjectTotals totals, List<ProjectRow> projects) {
    }

    public record ProjectTotals(int projects, int endpoints, int restEndpoints, int asyncEndpoints, int objects,
                                int structures, int fields, int fieldDefinitions, int errorCodes, int capabilities,
                                int rules) {
    }

    public record ProjectRow(UUID id, String name, String description, String version, String basePackage,
                             String defaultImplementationStyle, Instant createdAt, Instant updatedAt,
                             int endpoints, int restEndpoints, int asyncEndpoints, int objects, int structures,
                             int fields, int fieldDefinitions, int errorCodes, int capabilities, int rules,
                             int mappedEndpoints) {
    }

    // ---- 2. Endpoints per project ----

    public record EndpointsReport(int total, List<ProjectEndpointCount> perProject, List<EndpointRow> endpoints) {
    }

    public record ProjectEndpointCount(UUID projectId, String projectName, int total, int rest, int async,
                                       int withRules, int mapped) {
    }

    /** {@code method} and {@code path} for REST; {@code operation}, {@code channel} and {@code protocol} for async. */
    public record EndpointRow(UUID projectId, String projectName, UUID id, String name, String kind, String method,
                              String path, String operation, String channel, String protocol, String summary,
                              List<String> tags, String implementationStyle, String requestPayload,
                              List<String> responses, String messagePayload, int parameters, int rules,
                              List<String> security, List<String> capabilities) {
    }

    // ---- 3. Objects ----

    public record ObjectsReport(int total, int objects, int structures, List<ObjectRow> payloads) {
    }

    /** One payload, with what it is made of and what uses it. */
    public record ObjectRow(UUID projectId, String projectName, UUID id, String name, String role, String description,
                            int fields, int requiredFields, int objectFields, List<String> linkedObjects,
                            int usedBy, List<String> usages) {
    }

    // ---- 4. Fields with linked objects ----

    public record FieldsReport(int total, int linked, int linkedObjects, List<LinkedObjectCount> perObject,
                               List<FieldRow> fields) {
    }

    /** An object and the number of fields, in other payloads, that hold it. */
    public record LinkedObjectCount(UUID projectId, String projectName, UUID objectId, String objectName,
                                    String objectRole, int fields, List<String> heldBy) {
    }

    /**
     * One payload field. {@code linkedObject} is the payload it holds (an OBJECT field, or a list of objects);
     * {@code inFieldInventory} says whether the field inventory has a field with the same name.
     */
    public record FieldRow(UUID projectId, String projectName, UUID payloadId, String payloadName, String payloadRole,
                           int position, String name, String type, String itemType, boolean required,
                           UUID linkedObjectId, String linkedObject, String linkedObjectRole, String constraints,
                           String description, boolean inFieldInventory) {
    }

    // ---- The capability map: everything that links capabilities, endpoints, rules, objects and fields ----

    /**
     * One project as a graph, for walking it both ways: down from an L1 capability to the fields of the
     * objects its endpoints use, and up from a field to the L1 capabilities above it.
     */
    public record CapabilityMap(UUID projectId, String projectName, List<String> levelNames,
                                List<MapCapability> capabilities, List<MapEndpoint> endpoints,
                                List<MapPayload> payloads) {
    }

    /** In tree order; {@code endpointIds} are the APIs linked to this capability itself. */
    public record MapCapability(UUID id, UUID parentId, int level, String name, String code, String description,
                                List<UUID> endpointIds) {
    }

    public record MapEndpoint(UUID id, String name, String kind, String method, String path, String operation,
                              String channel, String protocol, String summary, List<MapUse> uses,
                              List<MapRule> rules) {
    }

    /** A payload an endpoint uses, and how: "request body", "response 201", "message", "rule 'Load order'". */
    public record MapUse(UUID payloadId, String how) {
    }

    public record MapRule(UUID id, int position, String name, String type, String typeLabel, String condition,
                          List<UUID> payloadIds, int errorCodes) {
    }

    public record MapPayload(UUID id, String name, String role, String description, List<MapField> fields) {
    }

    /** {@code refPayloadId}: the object an OBJECT field (or a list of objects) holds. */
    public record MapField(String name, String type, String itemType, boolean required, UUID refPayloadId,
                           String constraints, String description) {
    }
}
