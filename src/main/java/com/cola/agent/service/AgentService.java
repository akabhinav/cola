package com.cola.agent.service;

import com.cola.agent.model.Conversation;
import com.cola.agent.model.Project;
import com.cola.agent.repository.ConversationRepository;
import com.cola.agent.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for AI agent interactions and code generation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentService {

    private final ChatModel anthropicChatModel;

    @Qualifier("codeGenerationChatModel")
    private final ChatModel codeGenerationChatModel;

    private final ConversationRepository conversationRepository;
    private final ProjectRepository projectRepository;

    private static final String SYSTEM_PROMPT = """
        You are an expert software architect and developer assistant. Your role is to:
        1. Understand user requirements thoroughly
        2. Ask clarifying questions when needed
        3. Plan software architecture and implementation
        4. Generate production-quality code following best practices
        5. Provide clear explanations and documentation

        Always follow these principles:
        - SOLID principles
        - Clean code standards
        - Proper error handling
        - Comprehensive testing
        - Security best practices
        """;

    /**
     * Send a message to the AI agent and get a response.
     */
    @Transactional
    public String chat(UUID projectId, String userMessage, UUID userId) {
        log.info("Processing chat message for project {} from user {}", projectId, userId);

        // Validate project ownership
        Project project = projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new IllegalArgumentException("Project not found or access denied"));

        // Save user message
        saveConversation(projectId, userMessage, Conversation.Role.USER);

        // Get conversation history
        List<Conversation> history = conversationRepository.findByProjectIdOrderByTimestampAsc(projectId);

        // Build messages with context
        List<Message> messages = buildMessagesFromHistory(history);
        messages.add(new UserMessage(userMessage));

        // Call AI
        Prompt prompt = new Prompt(messages);
        ChatResponse response = anthropicChatModel.call(prompt);
        String assistantMessage = response.getResult().getOutput().getContent();

        // Save assistant response
        saveConversation(projectId, assistantMessage, Conversation.Role.ASSISTANT);

        log.info("Chat response generated for project {}", projectId);
        return assistantMessage;
    }

    /**
     * Generate code based on requirements with streaming support.
     */
    public Flux<String> generateCodeStreaming(UUID projectId, String requirements, UUID userId) {
        log.info("Generating code for project {} with streaming", projectId);

        Project project = projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new IllegalArgumentException("Project not found or access denied"));

        String codePrompt = buildCodeGenerationPrompt(requirements, project);

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        messages.add(new UserMessage(codePrompt));

        Prompt prompt = new Prompt(messages);

        // Use streaming API
        return codeGenerationChatModel.stream(prompt)
            .map(response -> response.getResult().getOutput().getContent());
    }

    /**
     * Generate a project plan based on requirements.
     */
    @Transactional
    public String generatePlan(UUID projectId, String requirements, UUID userId) {
        log.info("Generating project plan for project {}", projectId);

        Project project = projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new IllegalArgumentException("Project not found or access denied"));

        String planningPrompt = buildPlanningPrompt(requirements, project);

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        messages.add(new UserMessage(planningPrompt));

        Prompt prompt = new Prompt(messages);
        ChatResponse response = anthropicChatModel.call(prompt);
        String plan = response.getResult().getOutput().getContent();

        // Update project status
        project.setStatus(Project.ProjectStatus.PLANNING);
        projectRepository.save(project);

        // Save the plan in conversation
        saveConversation(projectId, requirements, Conversation.Role.USER);
        saveConversation(projectId, plan, Conversation.Role.ASSISTANT);

        return plan;
    }

    /**
     * Analyze and fix compilation errors.
     */
    @Transactional
    public String fixCompilationErrors(UUID projectId, String code, String errors, UUID userId) {
        log.info("Fixing compilation errors for project {}", projectId);

        projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new IllegalArgumentException("Project not found or access denied"));

        String fixPrompt = buildErrorFixingPrompt(code, errors);

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        messages.add(new UserMessage(fixPrompt));

        Prompt prompt = new Prompt(messages);
        ChatResponse response = codeGenerationChatModel.call(prompt);

        return response.getResult().getOutput().getContent();
    }

    private void saveConversation(UUID projectId, String message, Conversation.Role role) {
        Conversation conversation = Conversation.builder()
            .project(projectRepository.findById(projectId).orElseThrow())
            .message(message)
            .role(role)
            .timestamp(LocalDateTime.now())
            .build();
        conversationRepository.save(conversation);
    }

    private List<Message> buildMessagesFromHistory(List<Conversation> history) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));

        // Add last 10 messages to maintain context without exceeding token limits
        int startIndex = Math.max(0, history.size() - 10);
        for (int i = startIndex; i < history.size(); i++) {
            Conversation conv = history.get(i);
            if (conv.getRole() == Conversation.Role.USER) {
                messages.add(new UserMessage(conv.getMessage()));
            } else if (conv.getRole() == Conversation.Role.ASSISTANT) {
                messages.add(new AssistantMessage(conv.getMessage()));
            }
        }

        return messages;
    }

    private String buildCodeGenerationPrompt(String requirements, Project project) {
        return String.format("""
            Generate production-quality code based on the following requirements:

            Project: %s
            Language: %s
            Framework: %s

            Requirements:
            %s

            Please provide:
            1. Complete, executable code
            2. Proper error handling
            3. Unit tests
            4. Documentation
            5. Follow %s best practices and SOLID principles
            """,
            project.getName(),
            project.getLanguage(),
            project.getFramework(),
            requirements,
            project.getLanguage());
    }

    private String buildPlanningPrompt(String requirements, Project project) {
        return String.format("""
            Analyze the following requirements and create a detailed implementation plan:

            Project: %s
            Language: %s
            Framework: %s

            Requirements:
            %s

            Provide:
            1. Project structure and file organization
            2. Module breakdown with responsibilities
            3. Technology stack recommendations
            4. Implementation order and dependencies
            5. Estimated complexity and potential risks
            6. Testing strategy

            Format your response as a structured plan with clear sections.
            """,
            project.getName(),
            project.getLanguage(),
            project.getFramework(),
            requirements);
    }

    private String buildErrorFixingPrompt(String code, String errors) {
        return String.format("""
            The following code has compilation errors. Please analyze and fix them:

            Code:
            ```
            %s
            ```

            Errors:
            ```
            %s
            ```

            Please provide:
            1. Root cause analysis of each error
            2. Fixed code that resolves all issues
            3. Explanation of the fixes
            4. Prevention tips for similar errors

            Ensure the fixed code maintains functionality and follows best practices.
            """,
            code,
            errors);
    }
}
