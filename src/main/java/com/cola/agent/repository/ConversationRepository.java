package com.cola.agent.repository;

import com.cola.agent.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for Conversation entity operations.
 */
@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    List<Conversation> findByProjectIdOrderByTimestampAsc(UUID projectId);

    List<Conversation> findByProjectIdAndRoleOrderByTimestampAsc(UUID projectId, Conversation.Role role);

    long countByProjectId(UUID projectId);

    void deleteByProjectId(UUID projectId);
}
