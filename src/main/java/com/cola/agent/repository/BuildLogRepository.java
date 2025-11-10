package com.cola.agent.repository;

import com.cola.agent.model.BuildLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for BuildLog entity operations.
 */
@Repository
public interface BuildLogRepository extends JpaRepository<BuildLog, UUID> {

    List<BuildLog> findByProjectIdOrderByTimestampDesc(UUID projectId);

    List<BuildLog> findByProjectIdAndStatus(UUID projectId, BuildLog.BuildStatus status);

    long countByProjectIdAndStatus(UUID projectId, BuildLog.BuildStatus status);

    void deleteByProjectId(UUID projectId);
}
