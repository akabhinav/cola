package com.cola.agent.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * BuildLog entity representing compilation and build logs.
 */
@Entity
@Table(name = "build_logs", indexes = {
    @Index(name = "idx_build_project", columnList = "project_id"),
    @Index(name = "idx_build_timestamp", columnList = "timestamp")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuildLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "build_type", length = 50)
    private String buildType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BuildStatus status;

    @Column(columnDefinition = "TEXT")
    private String logs;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    public enum BuildStatus {
        SUCCESS,
        FAILURE,
        IN_PROGRESS
    }

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
