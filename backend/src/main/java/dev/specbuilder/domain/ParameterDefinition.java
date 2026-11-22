package dev.specbuilder.domain;

import jakarta.persistence.*;

/** A path, query or header parameter of a REST endpoint, or a message header of an async endpoint. */
@Embeddable
public class ParameterDefinition {

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ParameterLocation location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FieldType type;

    @Column(nullable = false)
    private boolean required;

    @Column(length = 2000)
    private String description;

    public ParameterDefinition() {
    }

    public ParameterDefinition(String name, ParameterLocation location, FieldType type, boolean required, String description) {
        this.name = name;
        this.location = location;
        this.type = type;
        this.required = required;
        this.description = description;
    }

    public ParameterDefinition copy() {
        return new ParameterDefinition(name, location, type, required, description);
    }

    public String getName() { return name; }
    public ParameterLocation getLocation() { return location; }
    public FieldType getType() { return type; }
    public boolean isRequired() { return required; }
    public String getDescription() { return description; }
}
