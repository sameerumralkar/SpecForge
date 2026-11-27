package dev.specbuilder.repository;

import dev.specbuilder.domain.FieldDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FieldDefinitionRepository extends JpaRepository<FieldDefinition, UUID> {

    List<FieldDefinition> findByProjectId(UUID projectId);

    Optional<FieldDefinition> findByIdAndProjectId(UUID id, UUID projectId);
}
