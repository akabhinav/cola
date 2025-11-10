package com.cola.agent.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ChatResponse {
    private UUID conversationId;
    private String message;
    private String role;
    private LocalDateTime timestamp;
}
