package com.cola.agent.repository;

import com.cola.agent.model.GeneratedFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for GeneratedFile entity operations.
 */
@Repository
public interface GeneratedFileRepository extends JpaRepository<GeneratedFile, UUID> {

    List<GeneratedFile> findByProjectIdOrderByFilePathAsc(UUID projectId);

    Optional<GeneratedFile> findByProjectIdAndFilePath(UUID projectId, String filePath);

    List<GeneratedFile> findByProjectIdAndLanguage(UUID projectId, String language);

    long countByProjectId(UUID projectId);

    void deleteByProjectId(UUID projectId);
}
