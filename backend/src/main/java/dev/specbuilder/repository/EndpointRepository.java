package dev.specbuilder.repository;

import dev.specbuilder.domain.Endpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EndpointRepository extends JpaRepository<Endpoint, UUID> {

    List<Endpoint> findByProjectId(UUID projectId);

    Optional<Endpoint> findByIdAndProjectId(UUID id, UUID projectId);

    long countByProjectId(UUID projectId);

    @Query("select e from Endpoint e where e.requestPayload.id = :payloadId or e.messagePayload.id = :payloadId")
    List<Endpoint> findUsingAsBodyOrMessage(UUID payloadId);

    /** Rows of [endpoint id, endpoint name, status code] for responses that use {@code payloadId}. */
    @Query("select e.id, e.name, r.statusCode from Endpoint e join e.responses r where r.payload.id = :payloadId")
    List<Object[]> findResponseReferences(UUID payloadId);
}
