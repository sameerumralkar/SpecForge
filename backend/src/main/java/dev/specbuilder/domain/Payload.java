package dev.specbuilder.domain;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A reusable schema. Other payloads, endpoints and rules refer to it by id, so renaming it never breaks a reference. */
@Entity
@Table(name = "payload")
public class Payload {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 4000)
    private String description;

    @Column(length = 20000)
    private String example;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PayloadRole role = PayloadRole.STRUCTURE;

    @ElementCollection
    @CollectionTable(name = "payload_field", joinColumns = @JoinColumn(name = "payload_id"))
    @OrderColumn(name = "ordinal")
    private List<PayloadField> fields = new ArrayList<>();

    protected Payload() {
    }

    public Payload(Project project) {
        this.project = project;
    }

    public UUID getId() { return id; }
    public Project getProject() { return project; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getExample() { return example; }
    public void setExample(String example) { this.example = example; }
    public PayloadRole getRole() { return role; }
    public void setRole(PayloadRole role) { this.role = role; }
    public List<PayloadField> getFields() { return fields; }

    public void replaceFields(List<PayloadField> newFields) {
        fields.clear();
        fields.addAll(newFields);
    }
}
