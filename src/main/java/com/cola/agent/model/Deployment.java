package com.cola.agent.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Deployment entity representing deployed containers/applications.
 */
@Entity
@Table(name = "deployments", indexes = {
    @Index(name = "idx_deployment_project", columnList = "project_id"),
    @Index(name = "idx_deployment_container", columnList = "container_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Deployment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "container_id", length = 255)
    private String containerId;

    @Column(name = "image_name", length = 255)
    private String imageName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DeploymentStatus status = DeploymentStatus.DEPLOYING;

    @CreationTimestamp
    @Column(name = "deployed_at", nullable = false, updatable = false)
    private LocalDateTime deployedAt;

    @Column
    private Integer port;

    @Column(name = "endpoint_url", length = 500)
    private String endpointUrl;

    public enum DeploymentStatus {
        DEPLOYING,
        RUNNING,
        STOPPED,
        FAILED
    }
}
