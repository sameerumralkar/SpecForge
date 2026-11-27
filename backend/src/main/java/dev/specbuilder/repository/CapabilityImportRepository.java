package dev.specbuilder.repository;

import dev.specbuilder.domain.CapabilityImport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CapabilityImportRepository extends JpaRepository<CapabilityImport, UUID> {

    List<CapabilityImport> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    Optional<CapabilityImport> findByIdAndProjectId(UUID id, UUID projectId);
}
