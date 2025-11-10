package com.cola.agent.websocket;

import com.cola.agent.dto.ChatRequest;
import com.cola.agent.dto.ChatResponse;
import com.cola.agent.service.AgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * WebSocket controller for real-time agent communication.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class AgentWebSocketController {

    private final AgentService agentService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat")
    @SendTo("/topic/messages")
    public ChatResponse handleChatMessage(
            @Payload ChatRequest request,
            SimpMessageHeaderAccessor headerAccessor) {

        log.info("Received WebSocket chat message for project {}", request.getProjectId());

        try {
            // Get user ID from session attributes
            UUID userId = getUserIdFromSession(headerAccessor);

            // Process the message
            String response = agentService.chat(request.getProjectId(), request.getMessage(), userId);

            return ChatResponse.builder()
                .message(response)
                .role("ASSISTANT")
                .timestamp(LocalDateTime.now())
                .build();

        } catch (Exception e) {
            log.error("Error processing WebSocket message", e);
            return ChatResponse.builder()
                .message("Error: " + e.getMessage())
                .role("SYSTEM")
                .timestamp(LocalDateTime.now())
                .build();
        }
    }

    @MessageMapping("/generate")
    public void handleCodeGeneration(
            @Payload ChatRequest request,
            SimpMessageHeaderAccessor headerAccessor) {

        log.info("Received code generation request for project {}", request.getProjectId());

        try {
            UUID userId = getUserIdFromSession(headerAccessor);

            // Stream code generation to the user
            agentService.generateCodeStreaming(request.getProjectId(), request.getMessage(), userId)
                .subscribe(
                    chunk -> {
                        // Send each chunk to the specific user
                        messagingTemplate.convertAndSendToUser(
                            userId.toString(),
                            "/queue/code",
                            chunk
                        );
                    },
                    error -> {
                        log.error("Error during code generation", error);
                        messagingTemplate.convertAndSendToUser(
                            userId.toString(),
                            "/queue/code",
                            "ERROR: " + error.getMessage()
                        );
                    },
                    () -> {
                        log.info("Code generation completed for project {}", request.getProjectId());
                        messagingTemplate.convertAndSendToUser(
                            userId.toString(),
                            "/queue/code",
                            "[DONE]"
                        );
                    }
                );

        } catch (Exception e) {
            log.error("Error initiating code generation", e);
        }
    }

    /**
     * Send log updates to connected clients.
     */
    public void sendLogUpdate(UUID projectId, String log) {
        messagingTemplate.convertAndSend("/topic/logs/" + projectId, log);
    }

    /**
     * Send project status updates to connected clients.
     */
    public void sendStatusUpdate(UUID projectId, String status) {
        messagingTemplate.convertAndSend("/topic/status/" + projectId, status);
    }

    private UUID getUserIdFromSession(SimpMessageHeaderAccessor headerAccessor) {
        // Extract user ID from session attributes
        // In production, this would be extracted from JWT or session
        Object userIdObj = headerAccessor.getSessionAttributes().get("userId");
        if (userIdObj != null) {
            return UUID.fromString(userIdObj.toString());
        }
        // For development/testing, return a default user ID
        // TODO: Replace with proper authentication
        return UUID.fromString("00000000-0000-0000-0000-000000000000");
    }
}
