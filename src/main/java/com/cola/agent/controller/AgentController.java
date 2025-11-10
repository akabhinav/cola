package com.cola.agent.controller;

import com.cola.agent.dto.ApiResponse;
import com.cola.agent.dto.ChatRequest;
import com.cola.agent.dto.ChatResponse;
import com.cola.agent.dto.CodeGenerationRequest;
import com.cola.agent.service.AgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * REST controller for AI agent interactions.
 */
@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
@Tag(name = "Agent", description = "AI Agent interaction APIs")
public class AgentController {

    private final AgentService agentService;

    @PostMapping("/chat")
    @Operation(summary = "Send a message to the AI agent")
    public ResponseEntity<ApiResponse<ChatResponse>> chat(
            @Valid @RequestBody ChatRequest request,
            @RequestHeader("X-User-Id") UUID userId) {

        String response = agentService.chat(request.getProjectId(), request.getMessage(), userId);

        ChatResponse chatResponse = ChatResponse.builder()
            .message(response)
            .role("ASSISTANT")
            .timestamp(LocalDateTime.now())
            .build();

        return ResponseEntity.ok(ApiResponse.success(chatResponse));
    }

    @PostMapping("/plan")
    @Operation(summary = "Generate a project implementation plan")
    public ResponseEntity<ApiResponse<String>> generatePlan(
            @RequestParam UUID projectId,
            @RequestBody String requirements,
            @RequestHeader("X-User-Id") UUID userId) {

        String plan = agentService.generatePlan(projectId, requirements, userId);
        return ResponseEntity.ok(ApiResponse.success("Plan generated successfully", plan));
    }

    @PostMapping(value = "/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Generate code with streaming response")
    public Flux<String> generateCode(
            @Valid @RequestBody CodeGenerationRequest request,
            @RequestHeader("X-User-Id") UUID userId) {

        return agentService.generateCodeStreaming(request.getProjectId(), request.getRequirements(), userId);
    }

    @PostMapping("/fix")
    @Operation(summary = "Fix compilation errors")
    public ResponseEntity<ApiResponse<String>> fixErrors(
            @RequestParam UUID projectId,
            @RequestParam String code,
            @RequestParam String errors,
            @RequestHeader("X-User-Id") UUID userId) {

        String fixedCode = agentService.fixCompilationErrors(projectId, code, errors, userId);
        return ResponseEntity.ok(ApiResponse.success("Errors fixed", fixedCode));
    }
}
