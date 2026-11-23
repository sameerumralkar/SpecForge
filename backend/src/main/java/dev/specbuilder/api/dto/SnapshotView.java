package dev.specbuilder.api.dto;

import dev.specbuilder.generation.GenerationSnapshot;

import java.time.Instant;
import java.util.UUID;

public record SnapshotView(UUID id, GenerationSnapshot.Kind kind, GenerationSnapshot.Reason reason, Instant createdAt, int fileCount) {
}
