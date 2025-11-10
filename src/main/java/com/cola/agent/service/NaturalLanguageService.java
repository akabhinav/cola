package com.cola.agent.service;

import com.cola.agent.dto.CommandSuggestion;
import com.cola.agent.dto.NaturalLanguageRequest;
import com.cola.agent.dto.NaturalLanguageResponse;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Natural language to shell command translation service.
 * Converts human-readable queries into executable shell commands.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NaturalLanguageService {

    private final ChatModel chatModel;
    private final ProjectRepository projectRepository;

    /**
     * Translate natural language query to shell command.
     */
    @Cacheable(value = "nlCommands", key = "#request.query + ':' + #request.shell")
    public NaturalLanguageResponse translate(NaturalLanguageRequest request) {
        try {
            Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

            String prompt = buildTranslationPrompt(request, project);

            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(NL_SYSTEM_PROMPT));
            messages.add(new UserMessage(prompt));

            ChatResponse response = chatModel.call(new Prompt(messages));
            String content = response.getResult().getOutput().getContent();

            return parseResponse(content, request);

        } catch (Exception e) {
            log.error("Error translating natural language command", e);
            return createErrorResponse(request.getQuery());
        }
    }

    /**
     * Get quick command suggestions for common tasks.
     */
    public List<NaturalLanguageResponse> getQuickCommands(String language, String framework) {
        List<NaturalLanguageResponse> commands = new ArrayList<>();

        // Common tasks
        commands.add(NaturalLanguageResponse.builder()
            .command("git status")
            .explanation("Show the current status of your git repository")
            .confidence(1.0)
            .safe(true)
            .build());

        commands.add(NaturalLanguageResponse.builder()
            .command("git add . && git commit -m \"Update\" && git push")
            .explanation("Stage all changes, commit them, and push to remote")
            .confidence(0.95)
            .safe(true)
            .warnings(List.of("Make sure you review changes before committing everything"))
            .build());

        // Language-specific commands
        if ("java".equalsIgnoreCase(language)) {
            commands.add(NaturalLanguageResponse.builder()
                .command("mvn clean install")
                .explanation("Clean the project, compile it, run tests, and install to local repository")
                .confidence(1.0)
                .safe(true)
                .build());
        } else if ("python".equalsIgnoreCase(language)) {
            commands.add(NaturalLanguageResponse.builder()
                .command("pip install -r requirements.txt && python main.py")
                .explanation("Install dependencies and run the main Python script")
                .confidence(0.95)
                .safe(true)
                .build());
        } else if ("javascript".equalsIgnoreCase(language) || "typescript".equalsIgnoreCase(language)) {
            commands.add(NaturalLanguageResponse.builder()
                .command("npm install && npm run dev")
                .explanation("Install dependencies and start the development server")
                .confidence(0.95)
                .safe(true)
                .build());
        }

        return commands;
    }

    /**
     * Build translation prompt with context.
     */
    private String buildTranslationPrompt(NaturalLanguageRequest request, Project project) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("User Query: ").append(request.getQuery()).append("\n\n");
        prompt.append("Context:\n");
        prompt.append("- Shell: ").append(request.getShell()).append("\n");
        prompt.append("- OS: ").append(request.getOs()).append("\n");
        prompt.append("- Project Language: ").append(project.getLanguage()).append("\n");
        if (project.getFramework() != null) {
            prompt.append("- Framework: ").append(project.getFramework()).append("\n");
        }
        if (request.getWorkingDirectory() != null) {
            prompt.append("- Working Directory: ").append(request.getWorkingDirectory()).append("\n");
        }

        prompt.append("\nProvide the shell command(s) that accomplish this task.\n");
        prompt.append("Use the following format:\n\n");
        prompt.append("COMMAND:\n<the shell command>\n\n");
        prompt.append("EXPLANATION:\n<clear explanation of what it does>\n\n");
        prompt.append("STEPS:\n- <step 1>\n- <step 2>\n...\n\n");
        prompt.append("WARNINGS:\n- <warning 1 if any>\n- <warning 2 if any>\n...\n\n");
        prompt.append("SAFE: <yes or no>\n");
        prompt.append("CONFIDENCE: <0.0-1.0>\n");

        if (request.isIncludeExamples()) {
            prompt.append("\nALTERNATIVES (optional):\n");
            prompt.append("<alternative command 1>\n<alternative command 2>\n");
        }

        return prompt.toString();
    }

    /**
     * Parse AI response into NaturalLanguageResponse.
     */
    private NaturalLanguageResponse parseResponse(String content, NaturalLanguageRequest request) {
        NaturalLanguageResponse.NaturalLanguageResponseBuilder builder = NaturalLanguageResponse.builder();

        // Extract command
        Pattern commandPattern = Pattern.compile("COMMAND:\\s*\n(.+?)(?=\n\n|$)", Pattern.DOTALL);
        Matcher commandMatcher = commandPattern.matcher(content);
        if (commandMatcher.find()) {
            builder.command(commandMatcher.group(1).trim());
        }

        // Extract explanation
        Pattern explPattern = Pattern.compile("EXPLANATION:\\s*\n(.+?)(?=\n\n|$)", Pattern.DOTALL);
        Matcher explMatcher = explPattern.matcher(content);
        if (explMatcher.find()) {
            builder.explanation(explMatcher.group(1).trim());
        }

        // Extract steps
        Pattern stepsPattern = Pattern.compile("STEPS:\\s*\n(.+?)(?=\n\n|$)", Pattern.DOTALL);
        Matcher stepsMatcher = stepsPattern.matcher(content);
        if (stepsMatcher.find()) {
            String stepsText = stepsMatcher.group(1).trim();
            List<String> steps = new ArrayList<>();
            for (String step : stepsText.split("\n")) {
                if (step.trim().startsWith("-")) {
                    steps.add(step.trim().substring(1).trim());
                }
            }
            builder.steps(steps);
        }

        // Extract warnings
        Pattern warningsPattern = Pattern.compile("WARNINGS:\\s*\n(.+?)(?=\n\n|$)", Pattern.DOTALL);
        Matcher warningsMatcher = warningsPattern.matcher(content);
        List<String> warnings = new ArrayList<>();
        if (warningsMatcher.find()) {
            String warningsText = warningsMatcher.group(1).trim();
            for (String warning : warningsText.split("\n")) {
                if (warning.trim().startsWith("-")) {
                    warnings.add(warning.trim().substring(1).trim());
                }
            }
        }
        builder.warnings(warnings);

        // Extract safety
        Pattern safePattern = Pattern.compile("SAFE:\\s*(.+)", Pattern.CASE_INSENSITIVE);
        Matcher safeMatcher = safePattern.matcher(content);
        if (safeMatcher.find()) {
            String safeValue = safeMatcher.group(1).trim().toLowerCase();
            builder.safe(safeValue.equals("yes") || safeValue.equals("true"));
        } else {
            builder.safe(true);
        }

        // Extract confidence
        Pattern confPattern = Pattern.compile("CONFIDENCE:\\s*([0-9.]+)");
        Matcher confMatcher = confPattern.matcher(content);
        if (confMatcher.find()) {
            builder.confidence(Double.parseDouble(confMatcher.group(1).trim()));
        } else {
            builder.confidence(0.8);
        }

        // Extract alternatives
        Pattern altPattern = Pattern.compile("ALTERNATIVES.*?:\n(.+?)(?=\n\n|$)", Pattern.DOTALL);
        Matcher altMatcher = altPattern.matcher(content);
        if (altMatcher.find() && request.isIncludeExamples()) {
            String altsText = altMatcher.group(1).trim();
            List<CommandSuggestion> alternatives = new ArrayList<>();
            String[] altLines = altsText.split("\n");
            for (String alt : altLines) {
                String trimmed = alt.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("-")) {
                    alternatives.add(CommandSuggestion.of(trimmed, "Alternative approach", 0.7));
                }
            }
            builder.alternatives(alternatives);
        }

        return builder.build();
    }

    /**
     * Create error response.
     */
    private NaturalLanguageResponse createErrorResponse(String query) {
        return NaturalLanguageResponse.builder()
            .command("")
            .explanation("Unable to translate query: " + query)
            .confidence(0.0)
            .safe(false)
            .warnings(List.of("Translation failed. Please try rephrasing your query."))
            .build();
    }

    private static final String NL_SYSTEM_PROMPT = """
        You are an expert system for translating natural language into shell commands.

        Your responsibilities:
        1. Understand user intent from natural language descriptions
        2. Generate accurate, safe, and idiomatic shell commands
        3. Provide clear explanations of what the command does
        4. Break down complex commands into understandable steps
        5. Warn about potentially dangerous operations
        6. Suggest safer alternatives when appropriate
        7. Consider the user's OS, shell type, and project context

        Guidelines:
        - Prefer widely-used, standard commands
        - Use safe options by default (e.g., -i for interactive confirmations)
        - Explain any flags or options used
        - Warn about commands that modify or delete data
        - Consider portability across different systems
        - Use pipe chains and command composition when appropriate
        - Provide working directory context when needed

        Safety rules:
        - Mark commands as unsafe if they delete files, modify system settings, or have irreversible effects
        - Always warn before suggesting rm, format, dd, or similar dangerous commands
        - Suggest --dry-run or -n flags when available
        - Recommend backups before destructive operations

        Format your response exactly as specified in the prompt.
        """;
}
