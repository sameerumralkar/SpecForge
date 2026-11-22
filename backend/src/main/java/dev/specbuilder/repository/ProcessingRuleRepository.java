package dev.specbuilder.repository;

import dev.specbuilder.domain.ProcessingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ProcessingRuleRepository extends JpaRepository<ProcessingRule, UUID> {

    @Query("select r from ProcessingRule r join fetch r.endpoint e where e.project.id = :projectId")
    List<ProcessingRule> findByProjectId(UUID projectId);
}
