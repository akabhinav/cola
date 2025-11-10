package com.cola.agent.repository;

import com.cola.agent.model.Deployment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Deployment entity operations.
 */
@Repository
public interface DeploymentRepository extends JpaRepository<Deployment, UUID> {

    List<Deployment> findByProjectIdOrderByDeployedAtDesc(UUID projectId);

    List<Deployment> findByStatus(Deployment.DeploymentStatus status);

    Optional<Deployment> findByContainerId(String containerId);

    long countByProjectIdAndStatus(UUID projectId, Deployment.DeploymentStatus status);

    void deleteByProjectId(UUID projectId);
}
