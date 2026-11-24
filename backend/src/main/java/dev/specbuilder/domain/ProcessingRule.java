package dev.specbuilder.domain;

import dev.specbuilder.domain.rule.RuleType;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One step in an endpoint's ordered rule chain. The type-specific settings are stored as JSON. */
@Entity
@Table(name = "processing_rule")
public class ProcessingRule {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "endpoint_id", nullable = false)
    private Endpoint endpoint;

    @Column(nullable = false)
    private int ordinal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RuleType type;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "condition_expression", length = 500)
    private String condition;

    @Column(nullable = false, length = 20000)
    private String config;

    /** Ids of the error codes this step can fail with, in order. */
    @Convert(converter = StringListConverter.class)
    @Column(name = "error_codes", length = 2000)
    private List<String> errorCodes = new ArrayList<>();

    protected ProcessingRule() {
    }

    public ProcessingRule(Endpoint endpoint) {
        this.endpoint = endpoint;
    }

    public UUID getId() { return id; }
    public Endpoint getEndpoint() { return endpoint; }
    public int getOrdinal() { return ordinal; }
    public void setOrdinal(int ordinal) { this.ordinal = ordinal; }
    public RuleType getType() { return type; }
    public void setType(RuleType type) { this.type = type; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }
    public String getConfig() { return config; }
    public void setConfig(String config) { this.config = config; }
    public List<UUID> getErrorCodeIds() { return ErrorCodeIds.read(errorCodes); }
    public void setErrorCodeIds(List<UUID> ids) { this.errorCodes = ErrorCodeIds.write(ids); }
}
