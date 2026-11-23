package dev.specbuilder.generation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GenerationSnapshotRepository extends JpaRepository<GenerationSnapshot, UUID> {

    List<GenerationSnapshot> findByProjectIdAndKindOrderByCreatedAtDesc(UUID projectId, GenerationSnapshot.Kind kind);

    Optional<GenerationSnapshot> findByIdAndProjectId(UUID id, UUID projectId);
}
