package dev.specbuilder.domain;

import jakarta.persistence.*;

import java.util.UUID;

/**
 * One entry of the project's field inventory: a ready-made field (name, type, constraints, error codes)
 * that is copied into a payload when it is added there. It holds plain fields only; objects live in the
 * object inventory.
 */
@Entity
@Table(name = "field_definition")
public class FieldDefinition {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Embedded
    private PayloadField field = new PayloadField();

    protected FieldDefinition() {
    }

    public FieldDefinition(Project project) {
        this.project = project;
    }

    public UUID getId() { return id; }
    public Project getProject() { return project; }
    public PayloadField getField() { return field; }
    public void setField(PayloadField field) { this.field = field; }
}
