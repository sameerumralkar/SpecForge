package dev.specbuilder.domain;

import jakarta.persistence.*;

import java.util.UUID;

/**
 * One entry of the project's error-code inventory: a stable code such as {@code PAY-001}, the message
 * callers see, and the HTTP status. Fields and rules link to it by id, so editing the code changes it everywhere.
 */
@Entity
@Table(name = "error_code")
public class ErrorCode {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 500)
    private String message;

    /** Null means the category's default. */
    @Column(name = "http_status")
    private Integer httpStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ErrorCategory category = ErrorCategory.VALIDATION;

    @Column(length = 2000)
    private String description;

    protected ErrorCode() {
    }

    public ErrorCode(Project project) {
        this.project = project;
    }

    public UUID getId() { return id; }
    public Project getProject() { return project; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Integer getHttpStatus() { return httpStatus; }
    public void setHttpStatus(Integer httpStatus) { this.httpStatus = httpStatus; }
    public ErrorCategory getCategory() { return category; }
    public void setCategory(ErrorCategory category) { this.category = category; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    /** The status callers get: the code's own, or its category's default. */
    public int effectiveStatus() {
        return httpStatus != null ? httpStatus : category.defaultStatus();
    }
}
