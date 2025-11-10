package com.cola.agent.repository;

import com.cola.agent.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Project entity operations.
 */
@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    List<Project> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<Project> findByUserIdAndStatus(UUID userId, Project.ProjectStatus status);

    @Query("SELECT p FROM Project p WHERE p.userId = :userId AND " +
           "(LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Project> searchProjects(@Param("userId") UUID userId, @Param("query") String query);

    long countByUserId(UUID userId);

    Optional<Project> findByIdAndUserId(UUID id, UUID userId);
}
