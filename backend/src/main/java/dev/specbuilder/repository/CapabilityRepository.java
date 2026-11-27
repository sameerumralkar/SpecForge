package dev.specbuilder.repository;

import dev.specbuilder.domain.Capability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CapabilityRepository extends JpaRepository<Capability, UUID> {

    List<Capability> findByProjectId(UUID projectId);

    Optional<Capability> findByIdAndProjectId(UUID id, UUID projectId);
}
