package dev.specbuilder.domain;

import jakarta.persistence.*;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A functional capability: what the business does, at one of eight levels (L1, the broadest, to L8).
 * Capabilities form a tree; each one lists the APIs (endpoints) that realise it directly. The API
 * inventory of a capability is its own APIs plus those of everything below it.
 */
@Entity
@Table(name = "capability")
public class Capability {

    public static final int MAX_LEVEL = 8;

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /** Null for an L1 capability. */
    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false)
    private int level;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 40)
    private String code;

    @Column(length = 4000)
    private String description;

    @ElementCollection
    @CollectionTable(name = "capability_endpoint", joinColumns = @JoinColumn(name = "capability_id"))
    @Column(name = "endpoint_id", nullable = false)
    private Set<UUID> endpointIds = new LinkedHashSet<>();

    protected Capability() {
    }

    public Capability(Project project) {
        this.project = project;
    }

    public UUID getId() { return id; }
    public Project getProject() { return project; }
    public UUID getParentId() { return parentId; }
    public void setParentId(UUID parentId) { this.parentId = parentId; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Set<UUID> getEndpointIds() { return endpointIds; }

    public void setEndpointIds(java.util.Collection<UUID> ids) {
        endpointIds.clear();
        endpointIds.addAll(ids);
    }
}
