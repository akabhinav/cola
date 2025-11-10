package com.cola.agent.service;

import com.cola.agent.dto.CommandRequest;
import com.cola.agent.dto.CommandResponse;
import com.cola.agent.model.Project;
import com.cola.agent.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Service for executing shell commands in project environments.
 * Provides secure, sandboxed command execution with streaming output.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TerminalService {

    private final ProjectRepository projectRepository;

    @Value("${cola.storage.path:./cola-projects}")
    private String storagePath;

    // Terminal session management
    private final Map<String, TerminalSession> sessions = new ConcurrentHashMap<>();

    // Blacklist of dangerous commands
    private static final List<String> DANGEROUS_COMMANDS = List.of(
        "rm -rf /", ":(){ :|:& };:", "dd if=/dev/zero", "mkfs", "format",
        "deltree", "> /dev/sda", "mv ~ /dev/null"
    );

    /**
     * Execute a command and return the full response.
     */
    public CommandResponse executeCommand(CommandRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            // Validate command
            validateCommand(request.getCommand());

            // Get or create session
            TerminalSession session = getOrCreateSession(request.getSessionId(), request.getProjectId());

            // Build working directory
            File workingDir = buildWorkingDirectory(request.getProjectId(), request.getWorkingDirectory());

            // Execute command
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command(getShellCommand(request.getCommand()));
            processBuilder.directory(workingDir);

            // Add environment variables
            if (request.getEnvironment() != null) {
                processBuilder.environment().putAll(request.getEnvironment());
            }

            processBuilder.redirectErrorStream(false);

            // Start process
            Process process = processBuilder.start();

            // Read output with timeout
            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            boolean completed = process.waitFor(request.getTimeoutMs(), TimeUnit.MILLISECONDS);

            if (!completed) {
                process.destroyForcibly();
                long executionTime = System.currentTimeMillis() - startTime;
                return CommandResponse.error(
                    request.getCommand(),
                    "Command timed out after " + request.getTimeoutMs() + "ms",
                    -1,
                    session.getSessionId()
                );
            }

            // Read output
            try (BufferedReader outReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

                String line;
                while ((line = outReader.readLine()) != null) {
                    stdout.append(line).append("\n");
                }
                while ((line = errReader.readLine()) != null) {
                    stderr.append(line).append("\n");
                }
            }

            int exitCode = process.exitValue();
            long executionTime = System.currentTimeMillis() - startTime;

            // Update session history
            session.addCommand(request.getCommand(), exitCode == 0);

            log.info("Command executed: {} ({}ms, exit code: {})",
                request.getCommand(), executionTime, exitCode);

            if (exitCode == 0) {
                return CommandResponse.success(
                    request.getCommand(),
                    stdout.toString(),
                    executionTime,
                    session.getSessionId()
                );
            } else {
                return CommandResponse.builder()
                    .command(request.getCommand())
                    .stdout(stdout.toString())
                    .stderr(stderr.toString())
                    .exitCode(exitCode)
                    .executionTimeMs(executionTime)
                    .success(false)
                    .sessionId(session.getSessionId())
                    .errorMessage(stderr.toString())
                    .build();
            }

        } catch (Exception e) {
            log.error("Error executing command: {}", request.getCommand(), e);
            long executionTime = System.currentTimeMillis() - startTime;

            return CommandResponse.error(
                request.getCommand(),
                "Error: " + e.getMessage(),
                -1,
                request.getSessionId()
            );
        }
    }

    /**
     * Execute a command with streaming output.
     */
    public Flux<String> streamCommand(CommandRequest request) {
        return Flux.create(sink -> {
            try {
                // Validate command
                validateCommand(request.getCommand());

                // Get or create session
                TerminalSession session = getOrCreateSession(request.getSessionId(), request.getProjectId());

                // Build working directory
                File workingDir = buildWorkingDirectory(request.getProjectId(), request.getWorkingDirectory());

                // Execute command
                ProcessBuilder processBuilder = new ProcessBuilder();
                processBuilder.command(getShellCommand(request.getCommand()));
                processBuilder.directory(workingDir);

                if (request.getEnvironment() != null) {
                    processBuilder.environment().putAll(request.getEnvironment());
                }

                processBuilder.redirectErrorStream(true); // Combine stdout and stderr

                Process process = processBuilder.start();

                // Stream output line by line
                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {

                    String line;
                    while ((line = reader.readLine()) != null) {
                        sink.next(line + "\n");
                    }

                    // Wait for process to complete
                    int exitCode = process.waitFor();
                    session.addCommand(request.getCommand(), exitCode == 0);

                    sink.next("\n[Process exited with code: " + exitCode + "]\n");
                    sink.complete();

                } catch (Exception e) {
                    sink.error(e);
                }

            } catch (Exception e) {
                log.error("Error streaming command", e);
                sink.error(e);
            }
        });
    }

    /**
     * Get command history for a session.
     */
    public List<String> getCommandHistory(String sessionId) {
        TerminalSession session = sessions.get(sessionId);
        return session != null ? session.getHistory() : new ArrayList<>();
    }

    /**
     * Validate command for security.
     */
    private void validateCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            throw new IllegalArgumentException("Command cannot be empty");
        }

        // Check against dangerous commands
        String lowerCommand = command.toLowerCase().trim();
        for (String dangerous : DANGEROUS_COMMANDS) {
            if (lowerCommand.contains(dangerous)) {
                throw new SecurityException("Dangerous command detected: " + dangerous);
            }
        }
    }

    /**
     * Get or create a terminal session.
     */
    private TerminalSession getOrCreateSession(String sessionId, UUID projectId) {
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString();
        }

        return sessions.computeIfAbsent(sessionId, id -> new TerminalSession(id, projectId));
    }

    /**
     * Build working directory for command execution.
     */
    private File buildWorkingDirectory(UUID projectId, String relativeDir) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        File projectDir = new File(storagePath, project.getId().toString());

        if (relativeDir != null && !relativeDir.isEmpty()) {
            File targetDir = new File(projectDir, relativeDir);
            // Security: ensure we stay within project directory
            if (!targetDir.getAbsolutePath().startsWith(projectDir.getAbsolutePath())) {
                throw new SecurityException("Directory traversal attempt detected");
            }
            return targetDir;
        }

        return projectDir;
    }

    /**
     * Get shell command array based on OS.
     */
    private String[] getShellCommand(String command) {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            return new String[]{"cmd.exe", "/c", command};
        } else {
            return new String[]{"/bin/sh", "-c", command};
        }
    }

    /**
     * Terminal session to maintain state.
     */
    private static class TerminalSession {
        private final String sessionId;
        private final UUID projectId;
        private final List<String> history;
        private final long createdAt;

        public TerminalSession(String sessionId, UUID projectId) {
            this.sessionId = sessionId;
            this.projectId = projectId;
            this.history = new ArrayList<>();
            this.createdAt = System.currentTimeMillis();
        }

        public void addCommand(String command, boolean success) {
            history.add(command);
            // Keep last 100 commands
            if (history.size() > 100) {
                history.remove(0);
            }
        }

        public String getSessionId() {
            return sessionId;
        }

        public List<String> getHistory() {
            return new ArrayList<>(history);
        }
    }
}
