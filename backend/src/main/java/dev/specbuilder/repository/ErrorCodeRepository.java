package dev.specbuilder.repository;

import dev.specbuilder.domain.ErrorCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ErrorCodeRepository extends JpaRepository<ErrorCode, UUID> {

    List<ErrorCode> findByProjectId(UUID projectId);

    Optional<ErrorCode> findByIdAndProjectId(UUID id, UUID projectId);
}
