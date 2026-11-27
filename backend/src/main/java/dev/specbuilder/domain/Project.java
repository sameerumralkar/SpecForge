package dev.specbuilder.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "project")
public class Project {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "base_package", nullable = false)
    private String basePackage;

    @Column(nullable = false, length = 50)
    private String version;

    @Column(length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_implementation_style", nullable = false, length = 30)
    private ImplementationStyle defaultImplementationStyle = ImplementationStyle.IMPLEMENTATION_CLASS;

    @Column(name = "allow_circular_references", nullable = false)
    private boolean allowCircularReferences;

    /** The names of capability levels L1 to L8; empty means the defaults. */
    @Convert(converter = StringListConverter.class)
    @Column(name = "capability_levels", length = 2000)
    private List<String> capabilityLevels = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    /** Marks the project as changed; called whenever anything inside it changes. */
    public List<String> getCapabilityLevels() { return capabilityLevels == null ? List.of() : capabilityLevels; }

    public void setCapabilityLevels(List<String> levels) { this.capabilityLevels = new ArrayList<>(levels); }

    public void touch() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBasePackage() { return basePackage; }
    public void setBasePackage(String basePackage) { this.basePackage = basePackage; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public ImplementationStyle getDefaultImplementationStyle() { return defaultImplementationStyle; }
    public void setDefaultImplementationStyle(ImplementationStyle style) { this.defaultImplementationStyle = style; }
    public boolean isAllowCircularReferences() { return allowCircularReferences; }
    public void setAllowCircularReferences(boolean allow) { this.allowCircularReferences = allow; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
