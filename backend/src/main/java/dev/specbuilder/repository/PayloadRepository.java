package dev.specbuilder.repository;

import dev.specbuilder.domain.Payload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayloadRepository extends JpaRepository<Payload, UUID> {

    List<Payload> findByProjectId(UUID projectId);

    Optional<Payload> findByIdAndProjectId(UUID id, UUID projectId);

    long countByProjectId(UUID projectId);

    /** Rows of [payload id, payload name, field name] for fields that point at {@code refId}. */
    @Query("select p.id, p.name, f.name from Payload p join p.fields f where f.refPayload.id = :refId")
    List<Object[]> findFieldReferences(UUID refId);
}
