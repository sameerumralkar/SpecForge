package dev.specbuilder.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A capability file read into a draft: the tree it holds (as JSON nodes), reviewed and edited, then applied
 * to the project's capabilities, or discarded.
 */
@Entity
@Table(name = "capability_import")
public class CapabilityImport {

    public enum Status { DRAFT, APPLIED, DISCARDED }

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "file_name", nullable = false, length = 300)
    private String fileName;

    @Column(nullable = false, length = 20)
    private String format;

    @Column(length = 200)
    private String sheet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    /** Imported top-level capabilities go under this existing capability; null: they become L1. */
    @Column(name = "target_parent_id")
    private UUID targetParentId;

    @Column(nullable = false, length = 1000000)
    private String nodes = "[]";

    @Column(length = 100000)
    private String warnings;

    @Column(length = 4000)
    private String result;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected CapabilityImport() {
    }

    public CapabilityImport(Project project, String fileName, String format) {
        this.project = project;
        this.fileName = fileName;
        this.format = format;
    }

    public void touch() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public Project getProject() { return project; }
    public String getFileName() { return fileName; }
    public String getFormat() { return format; }
    public String getSheet() { return sheet; }
    public void setSheet(String sheet) { this.sheet = sheet; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public UUID getTargetParentId() { return targetParentId; }
    public void setTargetParentId(UUID targetParentId) { this.targetParentId = targetParentId; }
    public String getNodes() { return nodes; }
    public void setNodes(String nodes) { this.nodes = nodes; }
    public String getWarnings() { return warnings; }
    public void setWarnings(String warnings) { this.warnings = warnings; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
