package com.cola.agent.controller;

import com.cola.agent.dto.CompletionChunk;
import com.cola.agent.dto.CompletionRequest;
import com.cola.agent.dto.CompletionResponse;
import com.cola.agent.service.CompletionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * Controller for fast code completions with streaming support.
 */
@RestController
@RequestMapping("/api/v1/completion")
@RequiredArgsConstructor
@Validated
@Slf4j
public class CompletionController {

    private final CompletionService completionService;

    /**
     * Generate code completion (non-streaming).
     *
     * This endpoint returns the full completion once generated.
     * Use this for simple integrations or when streaming is not needed.
     *
     * @param request Completion request with code context
     * @return Complete response with metadata
     */
    @PostMapping("/complete")
    public ResponseEntity<CompletionResponse> complete(@Valid @RequestBody CompletionRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            log.info("Completion request for {} at {}:{}",
                request.getFilePath(),
                request.getCursorPosition().getLine(),
                request.getCursorPosition().getColumn()
            );

            CompletionResponse response = completionService.complete(request);

            log.info("Completion returned: {} chars, {}ms, source: {}",
                response.getCompletion().length(),
                response.getLatencyMs(),
                response.getSource()
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error generating completion", e);

            long latency = System.currentTimeMillis() - startTime;
            CompletionResponse errorResponse = CompletionResponse.builder()
                .completion("")
                .source(CompletionResponse.CompletionSource.FALLBACK)
                .latencyMs(latency)
                .confidence(0.0)
                .build();

            return ResponseEntity.ok(errorResponse);
        }
    }

    /**
     * Stream code completion in real-time (Server-Sent Events).
     *
     * This endpoint streams completion chunks as they are generated,
     * providing a real-time feel similar to Cursor/GitHub Copilot.
     *
     * The stream emits:
     * - "chunk" events with partial completions
     * - "complete" event when done
     * - "error" event if something fails
     *
     * @param request Completion request with code context
     * @return Flux of Server-Sent Events with completion chunks
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<CompletionChunk>> streamCompletion(
            @Valid @RequestBody CompletionRequest request) {

        log.info("Streaming completion request for {} at {}:{}",
            request.getFilePath(),
            request.getCursorPosition().getLine(),
            request.getCursorPosition().getColumn()
        );

        return completionService.streamCompletion(request)
            .map(chunk -> {
                // Determine event type based on chunk state
                String eventType = chunk.isComplete() ? "complete" :
                                 chunk.getError() != null ? "error" : "chunk";

                return ServerSentEvent.<CompletionChunk>builder()
                    .event(eventType)
                    .data(chunk)
                    .build();
            })
            .doOnComplete(() -> {
                log.info("Completion stream completed for {}", request.getFilePath());
            })
            .doOnError(error -> {
                log.error("Error in completion stream", error);
            })
            .onErrorResume(error -> {
                // Send error event and complete the stream gracefully
                CompletionChunk errorChunk = CompletionChunk.error(error.getMessage());
                return Flux.just(
                    ServerSentEvent.<CompletionChunk>builder()
                        .event("error")
                        .data(errorChunk)
                        .build()
                );
            });
    }

    /**
     * Health check endpoint for completion service.
     *
     * @return Simple status response
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Completion service is healthy");
    }
}
