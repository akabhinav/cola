package com.cola.agent.controller;

import com.cola.agent.dto.*;
import com.cola.agent.service.CommandSuggestionService;
import com.cola.agent.service.NaturalLanguageService;
import com.cola.agent.service.TerminalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

/**
 * Controller for AI-powered terminal operations.
 * Provides command execution, AI suggestions, and natural language translation.
 */
@RestController
@RequestMapping("/api/v1/terminal")
@RequiredArgsConstructor
@Validated
@Slf4j
public class TerminalController {

    private final TerminalService terminalService;
    private final CommandSuggestionService commandSuggestionService;
    private final NaturalLanguageService naturalLanguageService;

    /**
     * Execute a shell command and return the result.
     *
     * POST /api/v1/terminal/execute
     *
     * @param request Command execution request
     * @return Command execution response
     */
    @PostMapping("/execute")
    public ResponseEntity<CommandResponse> executeCommand(@Valid @RequestBody CommandRequest request) {
        log.info("Executing command: {} for project: {}", request.getCommand(), request.getProjectId());

        try {
            CommandResponse response = terminalService.executeCommand(request);
            return ResponseEntity.ok(response);

        } catch (SecurityException e) {
            log.warn("Security violation: {}", e.getMessage());
            CommandResponse errorResponse = CommandResponse.error(
                request.getCommand(),
                "Security violation: " + e.getMessage(),
                -1,
                request.getSessionId()
            );
            return ResponseEntity.status(403).body(errorResponse);

        } catch (Exception e) {
            log.error("Error executing command", e);
            CommandResponse errorResponse = CommandResponse.error(
                request.getCommand(),
                "Error: " + e.getMessage(),
                -1,
                request.getSessionId()
            );
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Stream command execution output in real-time.
     *
     * POST /api/v1/terminal/stream
     *
     * @param request Command execution request
     * @return Stream of command output
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamCommand(@Valid @RequestBody CommandRequest request) {
        log.info("Streaming command: {} for project: {}", request.getCommand(), request.getProjectId());

        return terminalService.streamCommand(request)
            .map(line -> ServerSentEvent.<String>builder()
                .event("output")
                .data(line)
                .build())
            .doOnError(error -> {
                log.error("Error streaming command", error);
            })
            .onErrorResume(error -> {
                return Flux.just(ServerSentEvent.<String>builder()
                    .event("error")
                    .data("Error: " + error.getMessage())
                    .build());
            });
    }

    /**
     * Get AI-powered command suggestions.
     *
     * GET /api/v1/terminal/suggestions
     *
     * @param projectId Project ID
     * @param partial Partial command input
     * @param directory Current directory
     * @return List of command suggestions
     */
    @GetMapping("/suggestions")
    public ResponseEntity<List<CommandSuggestion>> getSuggestions(
            @RequestParam UUID projectId,
            @RequestParam(required = false) String partial,
            @RequestParam(required = false) String directory) {

        try {
            List<CommandSuggestion> suggestions;

            if (partial != null && !partial.isEmpty()) {
                suggestions = commandSuggestionService.getSuggestions(projectId, partial, directory);
            } else {
                // Get contextual suggestions
                List<String> recentCommands = terminalService.getCommandHistory(null);
                suggestions = commandSuggestionService.getContextualSuggestions(projectId, recentCommands);
            }

            return ResponseEntity.ok(suggestions);

        } catch (Exception e) {
            log.error("Error getting suggestions", e);
            return ResponseEntity.ok(List.of());
        }
    }

    /**
     * Get contextual command suggestions based on project state.
     *
     * GET /api/v1/terminal/contextual-suggestions/{projectId}
     *
     * @param projectId Project ID
     * @param sessionId Terminal session ID
     * @return List of contextual suggestions
     */
    @GetMapping("/contextual-suggestions/{projectId}")
    public ResponseEntity<List<CommandSuggestion>> getContextualSuggestions(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String sessionId) {

        try {
            List<String> recentCommands = terminalService.getCommandHistory(sessionId);
            List<CommandSuggestion> suggestions = commandSuggestionService.getContextualSuggestions(
                projectId, recentCommands
            );

            return ResponseEntity.ok(suggestions);

        } catch (Exception e) {
            log.error("Error getting contextual suggestions", e);
            return ResponseEntity.ok(List.of());
        }
    }

    /**
     * Translate natural language to shell command.
     *
     * POST /api/v1/terminal/translate
     *
     * @param request Natural language translation request
     * @return Translated shell command with explanation
     */
    @PostMapping("/translate")
    public ResponseEntity<NaturalLanguageResponse> translateNaturalLanguage(
            @Valid @RequestBody NaturalLanguageRequest request) {

        log.info("Translating natural language: {} for project: {}",
            request.getQuery(), request.getProjectId());

        try {
            NaturalLanguageResponse response = naturalLanguageService.translate(request);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error translating natural language", e);

            NaturalLanguageResponse errorResponse = NaturalLanguageResponse.builder()
                .command("")
                .explanation("Unable to translate query")
                .confidence(0.0)
                .safe(false)
                .warnings(List.of("Translation failed: " + e.getMessage()))
                .build();

            return ResponseEntity.ok(errorResponse);
        }
    }

    /**
     * Get quick command templates for common tasks.
     *
     * GET /api/v1/terminal/quick-commands
     *
     * @param language Project language
     * @param framework Project framework
     * @return List of quick command templates
     */
    @GetMapping("/quick-commands")
    public ResponseEntity<List<NaturalLanguageResponse>> getQuickCommands(
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String framework) {

        try {
            List<NaturalLanguageResponse> commands = naturalLanguageService.getQuickCommands(
                language != null ? language : "generic",
                framework
            );

            return ResponseEntity.ok(commands);

        } catch (Exception e) {
            log.error("Error getting quick commands", e);
            return ResponseEntity.ok(List.of());
        }
    }

    /**
     * Get command history for a session.
     *
     * GET /api/v1/terminal/history
     *
     * @param sessionId Terminal session ID
     * @return List of executed commands
     */
    @GetMapping("/history")
    public ResponseEntity<List<String>> getHistory(
            @RequestParam(required = false) String sessionId) {

        try {
            List<String> history = terminalService.getCommandHistory(sessionId);
            return ResponseEntity.ok(history);

        } catch (Exception e) {
            log.error("Error getting command history", e);
            return ResponseEntity.ok(List.of());
        }
    }

    /**
     * Health check endpoint.
     *
     * GET /api/v1/terminal/health
     *
     * @return Health status
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Terminal service is healthy");
    }
}
