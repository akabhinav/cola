package com.cola.agent.service;

import com.cola.agent.dto.CommandSuggestion;
import com.cola.agent.model.Project;
import com.cola.agent.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI-powered command suggestion service.
 * Provides Warp AI-style intelligent command suggestions based on context.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommandSuggestionService {

    private final ChatModel chatModel;
    private final ProjectRepository projectRepository;
    private final TerminalService terminalService;

    /**
     * Get command suggestions based on partial input and context.
     */
    @Cacheable(value = "commandSuggestions", key = "#projectId + ':' + #partialCommand")
    public List<CommandSuggestion> getSuggestions(UUID projectId, String partialCommand, String currentDirectory) {
        try {
            Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

            String prompt = buildSuggestionPrompt(project, partialCommand, currentDirectory);

            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(SUGGESTION_SYSTEM_PROMPT));
            messages.add(new UserMessage(prompt));

            ChatResponse response = chatModel.call(new Prompt(messages));
            String content = response.getResult().getOutput().getContent();

            return parseSuggestions(content);

        } catch (Exception e) {
            log.error("Error getting command suggestions", e);
            return getFallbackSuggestions(partialCommand);
        }
    }

    /**
     * Get contextual suggestions for the current project state.
     */
    public List<CommandSuggestion> getContextualSuggestions(UUID projectId, List<String> recentCommands) {
        try {
            Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

            List<CommandSuggestion> suggestions = new ArrayList<>();

            // Add project-type specific suggestions
            suggestions.addAll(getProjectTypeSuggestions(project));

            // Add workflow suggestions based on recent commands
            if (!recentCommands.isEmpty()) {
                suggestions.addAll(getWorkflowSuggestions(recentCommands));
            }

            return suggestions;

        } catch (Exception e) {
            log.error("Error getting contextual suggestions", e);
            return new ArrayList<>();
        }
    }

    /**
     * Build suggestion prompt with context.
     */
    private String buildSuggestionPrompt(Project project, String partialCommand, String currentDirectory) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Project: ").append(project.getName()).append("\n");
        prompt.append("Language: ").append(project.getLanguage()).append("\n");
        if (project.getFramework() != null) {
            prompt.append("Framework: ").append(project.getFramework()).append("\n");
        }
        if (currentDirectory != null) {
            prompt.append("Current Directory: ").append(currentDirectory).append("\n");
        }
        prompt.append("\nPartial Command: ").append(partialCommand).append("\n\n");
        prompt.append("Suggest 3-5 relevant shell commands that complete or extend this partial command.\n");
        prompt.append("Format each suggestion as:\n");
        prompt.append("COMMAND: <full command>\n");
        prompt.append("DESCRIPTION: <what it does>\n");
        prompt.append("CONFIDENCE: <0.0-1.0>\n");
        prompt.append("CATEGORY: <git|build|test|file|docker|npm|etc>\n");
        prompt.append("---\n");

        return prompt.toString();
    }

    /**
     * Parse AI response into CommandSuggestion objects.
     */
    private List<CommandSuggestion> parseSuggestions(String response) {
        List<CommandSuggestion> suggestions = new ArrayList<>();

        String[] blocks = response.split("---");
        Pattern commandPattern = Pattern.compile("COMMAND:\\s*(.+)");
        Pattern descPattern = Pattern.compile("DESCRIPTION:\\s*(.+)");
        Pattern confidencePattern = Pattern.compile("CONFIDENCE:\\s*([0-9.]+)");
        Pattern categoryPattern = Pattern.compile("CATEGORY:\\s*(.+)");

        for (String block : blocks) {
            try {
                Matcher cmdMatcher = commandPattern.matcher(block);
                Matcher descMatcher = descPattern.matcher(block);
                Matcher confMatcher = confidencePattern.matcher(block);
                Matcher catMatcher = categoryPattern.matcher(block);

                if (cmdMatcher.find() && descMatcher.find()) {
                    String command = cmdMatcher.group(1).trim();
                    String description = descMatcher.group(1).trim();
                    double confidence = confMatcher.find() ?
                        Double.parseDouble(confMatcher.group(1).trim()) : 0.8;
                    String category = catMatcher.find() ?
                        catMatcher.group(1).trim() : "general";

                    suggestions.add(CommandSuggestion.builder()
                        .command(command)
                        .description(description)
                        .confidence(confidence)
                        .category(category)
                        .dangerous(isDangerous(command))
                        .build());
                }
            } catch (Exception e) {
                log.debug("Failed to parse suggestion block: {}", block);
            }
        }

        return suggestions;
    }

    /**
     * Get project-type specific suggestions.
     */
    private List<CommandSuggestion> getProjectTypeSuggestions(Project project) {
        List<CommandSuggestion> suggestions = new ArrayList<>();

        switch (project.getLanguage().toLowerCase()) {
            case "java":
                if ("Spring Boot".equalsIgnoreCase(project.getFramework())) {
                    suggestions.add(CommandSuggestion.of(
                        "mvn spring-boot:run",
                        "Run the Spring Boot application",
                        0.9
                    ));
                }
                suggestions.add(CommandSuggestion.of(
                    "mvn clean install",
                    "Build the project and install to local repository",
                    0.9
                ));
                suggestions.add(CommandSuggestion.of(
                    "mvn test",
                    "Run all tests",
                    0.85
                ));
                break;

            case "python":
                suggestions.add(CommandSuggestion.of(
                    "python -m venv venv",
                    "Create a virtual environment",
                    0.85
                ));
                suggestions.add(CommandSuggestion.of(
                    "pip install -r requirements.txt",
                    "Install dependencies",
                    0.9
                ));
                suggestions.add(CommandSuggestion.of(
                    "pytest",
                    "Run tests with pytest",
                    0.85
                ));
                break;

            case "javascript":
            case "typescript":
                suggestions.add(CommandSuggestion.of(
                    "npm install",
                    "Install dependencies",
                    0.9
                ));
                suggestions.add(CommandSuggestion.of(
                    "npm run dev",
                    "Start development server",
                    0.85
                ));
                suggestions.add(CommandSuggestion.of(
                    "npm test",
                    "Run tests",
                    0.85
                ));
                break;

            case "go":
                suggestions.add(CommandSuggestion.of(
                    "go build",
                    "Compile the project",
                    0.9
                ));
                suggestions.add(CommandSuggestion.of(
                    "go test ./...",
                    "Run all tests",
                    0.85
                ));
                break;
        }

        // Common git commands
        suggestions.add(CommandSuggestion.of(
            "git status",
            "Show working tree status",
            0.8
        ));

        return suggestions;
    }

    /**
     * Get workflow suggestions based on recent commands.
     */
    private List<CommandSuggestion> getWorkflowSuggestions(List<String> recentCommands) {
        List<CommandSuggestion> suggestions = new ArrayList<>();

        String lastCommand = recentCommands.get(recentCommands.size() - 1);

        // Workflow patterns
        if (lastCommand.contains("git add")) {
            suggestions.add(CommandSuggestion.of(
                "git commit -m \"Your commit message\"",
                "Commit staged changes",
                0.95
            ));
        } else if (lastCommand.contains("git commit")) {
            suggestions.add(CommandSuggestion.of(
                "git push",
                "Push commits to remote",
                0.95
            ));
        } else if (lastCommand.contains("npm install") || lastCommand.contains("mvn install")) {
            suggestions.add(CommandSuggestion.of(
                lastCommand.contains("npm") ? "npm run build" : "mvn package",
                "Build the project",
                0.9
            ));
        } else if (lastCommand.contains("build") || lastCommand.contains("compile")) {
            suggestions.add(CommandSuggestion.of(
                lastCommand.contains("npm") ? "npm test" : "mvn test",
                "Run tests",
                0.85
            ));
        }

        return suggestions;
    }

    /**
     * Get fallback suggestions when AI fails.
     */
    private List<CommandSuggestion> getFallbackSuggestions(String partialCommand) {
        List<CommandSuggestion> suggestions = new ArrayList<>();

        if (partialCommand.startsWith("git")) {
            suggestions.add(CommandSuggestion.of("git status", "Show working tree status", 0.7));
            suggestions.add(CommandSuggestion.of("git add .", "Stage all changes", 0.7));
            suggestions.add(CommandSuggestion.of("git commit -m \"message\"", "Commit changes", 0.7));
        } else if (partialCommand.startsWith("npm")) {
            suggestions.add(CommandSuggestion.of("npm install", "Install dependencies", 0.7));
            suggestions.add(CommandSuggestion.of("npm run dev", "Start dev server", 0.7));
            suggestions.add(CommandSuggestion.of("npm test", "Run tests", 0.7));
        } else if (partialCommand.startsWith("mvn")) {
            suggestions.add(CommandSuggestion.of("mvn clean install", "Clean and build", 0.7));
            suggestions.add(CommandSuggestion.of("mvn test", "Run tests", 0.7));
            suggestions.add(CommandSuggestion.of("mvn spring-boot:run", "Run Spring Boot app", 0.7));
        }

        return suggestions;
    }

    /**
     * Check if command is potentially dangerous.
     */
    private boolean isDangerous(String command) {
        String lower = command.toLowerCase();
        return lower.contains("rm -rf") ||
               lower.contains("format") ||
               lower.contains("deltree") ||
               lower.contains("dd if=") ||
               lower.contains("> /dev/");
    }

    private static final String SUGGESTION_SYSTEM_PROMPT = """
        You are an expert shell command assistant. Your role is to suggest relevant and safe shell commands.

        Rules:
        1. Suggest commands that are relevant to the project type and context
        2. Provide clear descriptions of what each command does
        3. Assign realistic confidence scores (0.0 to 1.0)
        4. Categorize commands appropriately (git, build, test, file, docker, npm, etc.)
        5. Avoid suggesting dangerous commands (rm -rf, format, etc.)
        6. Consider the current directory and recent commands
        7. Suggest logical next steps in common workflows

        Format your response exactly as requested, with each suggestion separated by '---'.
        """;
}
