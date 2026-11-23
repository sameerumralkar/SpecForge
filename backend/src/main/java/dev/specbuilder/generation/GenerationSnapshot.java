package dev.specbuilder.generation;

import dev.specbuilder.domain.Project;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** Generated files as they were at one moment, kept so later output can be compared with them. */
@Entity
@Table(name = "generation_snapshot")
public class GenerationSnapshot {

    public enum Kind { CODE, SPECS }

    public enum Reason { DOWNLOAD, MANUAL }

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Kind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Reason reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "file_count", nullable = false)
    private int fileCount;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    /** Base64 of gzipped JSON: {path: content}. */
    @Column(nullable = false, length = 1000000)
    private String content;

    protected GenerationSnapshot() {
    }

    public GenerationSnapshot(Project project, Kind kind, Reason reason, int fileCount, String contentHash, String content) {
        this.project = project;
        this.kind = kind;
        this.reason = reason;
        this.createdAt = Instant.now();
        this.fileCount = fileCount;
        this.contentHash = contentHash;
        this.content = content;
    }

    public UUID getId() { return id; }
    public Project getProject() { return project; }
    public Kind getKind() { return kind; }
    public Reason getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
    public int getFileCount() { return fileCount; }
    public String getContentHash() { return contentHash; }
    public String getContent() { return content; }
}
