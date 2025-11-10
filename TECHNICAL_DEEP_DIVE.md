# COLA Technical Deep Dive - Building World-Class Features

## 🎯 Critical Feature Implementation Guide

This document provides detailed technical implementation strategies for the most critical features needed to compete with Warp AI and Cursor.

---

## 1. Advanced Context Management with RAG

### Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        User Query                            │
└───────────────────────────┬─────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────┐
│                    Query Processor                           │
│  - Intent classification                                     │
│  - Entity extraction                                         │
│  - Query embedding                                           │
└───────────────────────────┬─────────────────────────────────┘
                            │
        ┌───────────────────┼───────────────────┐
        │                   │                   │
        ▼                   ▼                   ▼
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│   Vector     │    │     AST      │    │    Symbol    │
│   Search     │    │   Search     │    │    Table     │
│  (Semantic)  │    │  (Syntax)    │    │  (Names)     │
└──────┬───────┘    └──────┬───────┘    └──────┬───────┘
       │                   │                   │
       └───────────────────┼───────────────────┘
                           │
┌──────────────────────────▼───────────────────────────────────┐
│                   Context Ranker                              │
│  - Relevance scoring                                          │
│  - Recency weighting                                          │
│  - Dependency importance                                      │
└──────────────────────────┬───────────────────────────────────┘
                           │
┌──────────────────────────▼───────────────────────────────────┐
│                   Context Assembler                           │
│  - Token budget management                                    │
│  - Hierarchical summarization                                 │
│  - Context compression                                        │
└──────────────────────────┬───────────────────────────────────┘
                           │
┌──────────────────────────▼───────────────────────────────────┐
│                      LLM Call                                 │
│  with optimized context                                       │
└───────────────────────────────────────────────────────────────┘
```

### Implementation Details

#### 1.1 Code Indexing Service

```java
@Service
@Slf4j
public class CodeIndexingService {

    private final VectorStoreService vectorStore;
    private final ASTParserService astParser;
    private final EmbeddingService embeddingService;

    /**
     * Index entire codebase for semantic search
     */
    public void indexProject(UUID projectId) {
        // 1. Parse all files with TreeSitter
        List<CodeFile> files = loadProjectFiles(projectId);

        for (CodeFile file : files) {
            // Parse to AST
            ASTNode ast = astParser.parse(file.getContent(), file.getLanguage());

            // Extract meaningful chunks
            List<CodeChunk> chunks = extractChunks(ast, file);

            // Generate embeddings
            for (CodeChunk chunk : chunks) {
                float[] embedding = embeddingService.embed(chunk.getContent());

                // Store in vector database
                vectorStore.upsert(VectorRecord.builder()
                    .id(chunk.getId())
                    .embedding(embedding)
                    .metadata(Map.of(
                        "projectId", projectId,
                        "filePath", file.getPath(),
                        "chunkType", chunk.getType(), // function, class, etc.
                        "startLine", chunk.getStartLine(),
                        "endLine", chunk.getEndLine(),
                        "symbols", chunk.getSymbols()
                    ))
                    .build());
            }
        }

        log.info("Indexed {} files for project {}", files.size(), projectId);
    }

    /**
     * Extract semantically meaningful code chunks
     */
    private List<CodeChunk> extractChunks(ASTNode ast, CodeFile file) {
        List<CodeChunk> chunks = new ArrayList<>();

        // Extract functions/methods
        ast.findAll(NodeType.FUNCTION_DECLARATION).forEach(node -> {
            chunks.add(CodeChunk.builder()
                .type(ChunkType.FUNCTION)
                .content(node.getText())
                .name(node.getName())
                .docstring(node.getDocstring())
                .symbols(extractSymbols(node))
                .dependencies(extractDependencies(node))
                .build());
        });

        // Extract classes
        ast.findAll(NodeType.CLASS_DECLARATION).forEach(node -> {
            chunks.add(CodeChunk.builder()
                .type(ChunkType.CLASS)
                .content(node.getText())
                .name(node.getName())
                .methods(node.getMethods())
                .properties(node.getProperties())
                .build());
        });

        // Extract imports/exports
        chunks.add(extractImports(ast, file));

        return chunks;
    }
}
```

#### 1.2 Semantic Search Service

```java
@Service
public class SemanticSearchService {

    private final VectorStoreService vectorStore;
    private final EmbeddingService embeddingService;

    /**
     * Search codebase using natural language
     */
    public List<CodeSearchResult> search(UUID projectId, String query, int topK) {
        // Generate query embedding
        float[] queryEmbedding = embeddingService.embed(query);

        // Vector similarity search
        List<VectorSearchResult> results = vectorStore.similaritySearch(
            queryEmbedding,
            topK * 2, // Get more results for re-ranking
            Map.of("projectId", projectId)
        );

        // Re-rank with hybrid approach
        return rerank(results, query)
            .stream()
            .limit(topK)
            .collect(Collectors.toList());
    }

    /**
     * Hybrid re-ranking: combine vector similarity with other signals
     */
    private List<CodeSearchResult> rerank(List<VectorSearchResult> results, String query) {
        return results.stream()
            .map(result -> {
                double score = result.getSimilarityScore();

                // Boost recent files
                score *= calculateRecencyBoost(result.getMetadata());

                // Boost files with keyword matches
                score *= calculateKeywordBoost(result.getContent(), query);

                // Boost frequently accessed files
                score *= calculatePopularityBoost(result.getMetadata());

                return new CodeSearchResult(result, score);
            })
            .sorted(Comparator.comparingDouble(CodeSearchResult::getScore).reversed())
            .collect(Collectors.toList());
    }
}
```

#### 1.3 Context Assembly

```java
@Service
public class ContextAssemblyService {

    private final SemanticSearchService searchService;
    private final DependencyGraphService dependencyGraph;
    private final TokenCounterService tokenCounter;

    private static final int MAX_CONTEXT_TOKENS = 100_000;
    private static final int RESERVED_RESPONSE_TOKENS = 4096;

    /**
     * Build optimal context for LLM call
     */
    public Context buildContext(UUID projectId, String userQuery, List<String> recentFiles) {
        int availableTokens = MAX_CONTEXT_TOKENS - RESERVED_RESPONSE_TOKENS;

        // 1. Always include recent conversation context (high priority)
        List<Message> conversationHistory = getRecentConversation(projectId);
        int conversationTokens = tokenCounter.count(conversationHistory);
        availableTokens -= conversationTokens;

        // 2. Search for relevant code
        List<CodeSearchResult> relevant = searchService.search(projectId, userQuery, 20);

        // 3. Expand with dependencies
        Set<CodeChunk> expandedContext = new LinkedHashSet<>();
        for (CodeSearchResult result : relevant) {
            if (availableTokens <= 0) break;

            CodeChunk chunk = result.getChunk();
            int chunkTokens = tokenCounter.count(chunk.getContent());

            if (chunkTokens <= availableTokens) {
                expandedContext.add(chunk);
                availableTokens -= chunkTokens;

                // Add dependencies if space allows
                List<CodeChunk> deps = dependencyGraph.getDependencies(chunk);
                for (CodeChunk dep : deps) {
                    int depTokens = tokenCounter.count(dep.getContent());
                    if (depTokens <= availableTokens) {
                        expandedContext.add(dep);
                        availableTokens -= depTokens;
                    }
                }
            }
        }

        // 4. Build structured context
        return Context.builder()
            .conversationHistory(conversationHistory)
            .relevantCode(expandedContext)
            .projectMetadata(getProjectMetadata(projectId))
            .totalTokens(MAX_CONTEXT_TOKENS - availableTokens)
            .build();
    }
}
```

### Technology Stack for RAG

```yaml
Required Dependencies:
  - Pinecone/Weaviate/Qdrant: Vector database
  - TreeSitter: Multi-language AST parsing
  - OpenAI Embeddings API: text-embedding-3-large
  - Redis: Caching layer for embeddings
  - PostgreSQL: Metadata storage

Performance Targets:
  - Embedding generation: < 100ms per chunk
  - Vector search: < 50ms
  - Context assembly: < 200ms
  - Total retrieval time: < 500ms
```

---

## 2. Real-time Code Completion

### Architecture

```java
@RestController
@RequestMapping("/api/v1/completion")
public class CodeCompletionController {

    private final CodeCompletionService completionService;

    /**
     * Stream code completions in real-time
     */
    @PostMapping(value = "/complete", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<CompletionChunk> complete(@RequestBody CompletionRequest request) {
        return completionService.streamCompletion(request)
            .timeout(Duration.ofSeconds(5))
            .onErrorResume(e -> Flux.just(CompletionChunk.error(e.getMessage())));
    }
}

@Service
public class CodeCompletionService {

    private final ChatModel fastModel; // Claude Haiku or GPT-3.5 Turbo
    private final ContextAssemblyService contextService;
    private final CompletionCacheService cacheService;

    /**
     * Generate code completions with streaming
     */
    public Flux<CompletionChunk> streamCompletion(CompletionRequest request) {
        // Check cache first
        Optional<String> cached = cacheService.get(request);
        if (cached.isPresent()) {
            return Flux.just(CompletionChunk.fromString(cached.get()));
        }

        // Build context
        Context context = contextService.buildCompletionContext(
            request.getProjectId(),
            request.getFilePath(),
            request.getCursorPosition(),
            request.getPrefix(),
            request.getSuffix()
        );

        // Build prompt
        String prompt = buildCompletionPrompt(context, request);

        // Stream from model
        return fastModel.stream(new Prompt(prompt))
            .map(response -> {
                String content = response.getResult().getOutput().getContent();
                return CompletionChunk.builder()
                    .text(content)
                    .isComplete(false)
                    .build();
            })
            .doOnComplete(() -> {
                // Cache result
                cacheService.put(request, completedText);
            });
    }

    /**
     * Build optimal completion prompt
     */
    private String buildCompletionPrompt(Context context, CompletionRequest request) {
        return String.format("""
            You are an expert code completion engine. Generate natural, idiomatic code.

            Language: %s
            Framework: %s

            File: %s

            Relevant context:
            ```
            %s
            ```

            Current file prefix (before cursor):
            ```
            %s
            ```

            Current file suffix (after cursor):
            ```
            %s
            ```

            Generate the most likely code completion. Consider:
            - Variable names and types in scope
            - Imported modules
            - Code style and patterns
            - Language idioms

            Only generate the completion, no explanations:
            """,
            request.getLanguage(),
            context.getFramework(),
            request.getFilePath(),
            context.getRelevantCode(),
            request.getPrefix(),
            request.getSuffix()
        );
    }
}
```

### Optimizations for Speed

```java
@Service
public class CompletionCacheService {

    private final RedisTemplate<String, String> redis;
    private static final Duration TTL = Duration.ofMinutes(30);

    /**
     * Generate cache key from request context
     */
    private String getCacheKey(CompletionRequest request) {
        // Hash of prefix + suffix + relevant context
        String contextSignature = DigestUtils.sha256Hex(
            request.getPrefix() + "|" + request.getSuffix()
        );

        return String.format("completion:%s:%s:%s",
            request.getProjectId(),
            request.getFilePath(),
            contextSignature
        );
    }

    /**
     * Implement prefix matching for partial cache hits
     */
    public List<String> getPrefixMatches(CompletionRequest request) {
        String pattern = String.format("completion:%s:%s:*",
            request.getProjectId(),
            request.getFilePath()
        );

        return redis.keys(pattern).stream()
            .map(key -> redis.opsForValue().get(key))
            .filter(Objects::nonNull)
            .filter(completion -> completion.startsWith(request.getPrefix()))
            .collect(Collectors.toList());
    }
}
```

### Multi-Line Completions

```java
/**
 * Generate multi-line completions with better context
 */
public Flux<CompletionChunk> multiLineCompletion(CompletionRequest request) {
    // Analyze AST to determine what should be completed
    ASTNode currentNode = astParser.parseAt(
        request.getFileContent(),
        request.getCursorPosition()
    );

    CompletionType type = determineCompletionType(currentNode);

    return switch (type) {
        case FUNCTION_BODY -> generateFunctionCompletion(request, currentNode);
        case CLASS_MEMBERS -> generateClassMembersCompletion(request, currentNode);
        case IMPORT_STATEMENTS -> generateImportsCompletion(request, currentNode);
        case TEST_CASES -> generateTestCompletion(request, currentNode);
        default -> standardCompletion(request);
    };
}
```

---

## 3. Terminal Integration (Warp-like Experience)

### Architecture

```typescript
// Frontend: Terminal Component
class AITerminal extends Component {
    private xterm: Terminal;
    private wsConnection: WebSocket;
    private commandHistory: CommandHistory;

    constructor() {
        // XTerm.js setup
        this.xterm = new Terminal({
            theme: colaTheme,
            fontSize: 14,
            fontFamily: 'JetBrains Mono, monospace',
            cursorBlink: true,
            allowTransparency: true
        });

        // Add-ons
        this.xterm.loadAddon(new FitAddon());
        this.xterm.loadAddon(new WebLinksAddon());
        this.xterm.loadAddon(new SearchAddon());

        // WebSocket connection
        this.setupWebSocket();
    }

    /**
     * AI-powered command suggestions
     */
    async getSuggestions(partialCommand: string): Promise<Suggestion[]> {
        // Send to backend for AI analysis
        const response = await fetch('/api/v1/terminal/suggest', {
            method: 'POST',
            body: JSON.stringify({
                partialCommand,
                currentDirectory: this.currentDir,
                recentCommands: this.commandHistory.recent(10),
                projectContext: this.projectId
            })
        });

        return response.json();
    }

    /**
     * Explain command before execution
     */
    async explainCommand(command: string): Promise<string> {
        const response = await fetch('/api/v1/terminal/explain', {
            method: 'POST',
            body: JSON.stringify({ command })
        });

        return response.text();
    }

    /**
     * Natural language to command translation
     */
    async translateNaturalLanguage(description: string): Promise<string[]> {
        const response = await fetch('/api/v1/terminal/translate', {
            method: 'POST',
            body: JSON.stringify({ description })
        });

        return response.json(); // Array of possible commands
    }
}
```

### Backend: Terminal Service

```java
@Service
public class TerminalService {

    private final ChatModel model;
    private final CommandExecutor executor;

    /**
     * Generate command suggestions
     */
    public List<CommandSuggestion> suggestCommands(CommandContext context) {
        String prompt = String.format("""
            Given the partial command and context, suggest completions.

            Partial command: %s
            Current directory: %s
            Recent commands: %s
            Project type: %s

            Provide 5 most likely completions with explanations:
            """,
            context.getPartialCommand(),
            context.getCurrentDirectory(),
            context.getRecentCommands(),
            context.getProjectType()
        );

        String response = model.call(new Prompt(prompt))
            .getResult()
            .getOutput()
            .getContent();

        return parseCommandSuggestions(response);
    }

    /**
     * Natural language to shell command
     */
    public List<String> translateToCommand(String naturalLanguage, CommandContext context) {
        String prompt = String.format("""
            Translate this natural language description to shell command(s):

            Description: "%s"

            Context:
            - OS: %s
            - Shell: %s
            - Current directory: %s
            - Project type: %s

            Provide the exact command(s) to execute:
            """,
            naturalLanguage,
            context.getOperatingSystem(),
            context.getShellType(),
            context.getCurrentDirectory(),
            context.getProjectType()
        );

        String response = model.call(new Prompt(prompt))
            .getResult()
            .getOutput()
            .getContent();

        return extractCommands(response);
    }

    /**
     * Execute command with safety checks
     */
    public ExecutionResult executeCommand(String command, CommandContext context) {
        // Safety checks
        if (isDangerousCommand(command)) {
            return ExecutionResult.blocked("Dangerous command blocked: " + command);
        }

        // Execute in isolated environment
        return executor.execute(command, context);
    }

    /**
     * Detect dangerous commands
     */
    private boolean isDangerousCommand(String command) {
        List<String> dangerousPatterns = Arrays.asList(
            "rm -rf /", ":(){ :|:& };:", "dd if=/dev/random",
            "mkfs", "format", "> /dev/sda"
        );

        return dangerousPatterns.stream()
            .anyMatch(pattern -> command.toLowerCase().contains(pattern));
    }
}
```

### Command Workflows (Like Warp)

```java
@Entity
public class CommandWorkflow {
    @Id
    private UUID id;
    private String name;
    private String description;

    @ElementCollection
    @OrderColumn
    private List<CommandStep> steps;

    private Map<String, String> parameters; // Template variables

    @Data
    public static class CommandStep {
        private String command;
        private String description;
        private boolean requiresConfirmation;
        private List<String> expectedOutputPatterns;
    }
}

@Service
public class WorkflowExecutionService {

    /**
     * Execute workflow with parameter substitution
     */
    public Flux<WorkflowStepResult> executeWorkflow(
        UUID workflowId,
        Map<String, String> parameters
    ) {
        CommandWorkflow workflow = workflowRepository.findById(workflowId)
            .orElseThrow();

        return Flux.fromIterable(workflow.getSteps())
            .concatMap(step -> {
                // Substitute parameters
                String command = substituteParameters(step.getCommand(), parameters);

                // Request confirmation if needed
                if (step.isRequiresConfirmation()) {
                    // Send confirmation request to frontend
                    return waitForConfirmation(command)
                        .flatMap(confirmed -> {
                            if (confirmed) {
                                return executeStep(command, step);
                            } else {
                                return Mono.just(WorkflowStepResult.skipped());
                            }
                        });
                } else {
                    return executeStep(command, step);
                }
            });
    }
}
```

---

## 4. Git Intelligence

### Smart Commit Messages

```java
@Service
public class GitIntelligenceService {

    private final ChatModel model;
    private final GitRepository gitRepo;

    /**
     * Generate commit message from staged changes
     */
    public CommitSuggestion generateCommitMessage(UUID projectId) {
        // Get staged changes
        String diff = gitRepo.getStagedDiff(projectId);

        // Analyze file changes
        List<FileChange> changes = analyzeDiff(diff);

        // Build context-aware prompt
        String prompt = buildCommitPrompt(changes, diff);

        // Generate commit message
        String message = model.call(new Prompt(prompt))
            .getResult()
            .getOutput()
            .getContent();

        // Parse conventional commit format
        return parseCommitMessage(message);
    }

    private String buildCommitPrompt(List<FileChange> changes, String diff) {
        return String.format("""
            Generate a commit message following Conventional Commits format.

            Files changed:
            %s

            Detailed diff:
            ```
            %s
            ```

            Rules:
            1. Use format: <type>(<scope>): <description>
            2. Types: feat, fix, docs, style, refactor, test, chore
            3. Description: imperative mood, no period, max 72 chars
            4. Body: explain what and why (optional)
            5. Footer: breaking changes, issues closed (optional)

            Generate the commit message:
            """,
            formatFileChanges(changes),
            truncateDiff(diff, 5000) // Limit tokens
        );
    }

    /**
     * Analyze diff to understand change types
     */
    private List<FileChange> analyzeDiff(String diff) {
        List<FileChange> changes = new ArrayList<>();

        // Parse diff
        String[] files = diff.split("diff --git");

        for (String fileDiff : files) {
            if (fileDiff.trim().isEmpty()) continue;

            FileChange change = FileChange.builder()
                .path(extractFilePath(fileDiff))
                .additions(countAdditions(fileDiff))
                .deletions(countDeletions(fileDiff))
                .changeType(determineChangeType(fileDiff))
                .build();

            changes.add(change);
        }

        return changes;
    }

    private ChangeType determineChangeType(String fileDiff) {
        if (fileDiff.contains("new file mode")) {
            return ChangeType.NEW_FILE;
        } else if (fileDiff.contains("deleted file mode")) {
            return ChangeType.DELETED_FILE;
        } else if (fileDiff.contains("rename from")) {
            return ChangeType.RENAMED;
        } else {
            // Analyze content to determine if it's refactor, feature, or fix
            return analyzeContentChange(fileDiff);
        }
    }
}
```

### Pull Request Generation

```java
@Service
public class PRGenerationService {

    /**
     * Generate comprehensive PR description
     */
    public PullRequestDescription generatePRDescription(
        String baseBranch,
        String headBranch,
        UUID projectId
    ) {
        // Get all commits in branch
        List<Commit> commits = gitRepo.getCommits(baseBranch, headBranch);

        // Get full diff
        String diff = gitRepo.getDiff(baseBranch, headBranch);

        // Analyze changes
        ChangeAnalysis analysis = analyzeChanges(commits, diff);

        // Generate PR description
        String prompt = String.format("""
            Generate a comprehensive Pull Request description.

            Commits:
            %s

            Files changed: %d
            Additions: +%d
            Deletions: -%d

            Key changes:
            %s

            Generate a PR description with:
            ## Summary
            [Brief overview]

            ## Changes
            [Detailed list of changes]

            ## Test Plan
            [How to test these changes]

            ## Screenshots (if UI changes)
            [Mention if screenshots are needed]

            ## Checklist
            - [ ] Tests added/updated
            - [ ] Documentation updated
            - [ ] Breaking changes noted

            ## Related Issues
            [Any related issues]
            """,
            formatCommits(commits),
            analysis.getFilesChanged(),
            analysis.getAdditions(),
            analysis.getDeletions(),
            analysis.getKeyChanges()
        );

        String description = model.call(new Prompt(prompt))
            .getResult()
            .getOutput()
            .getContent();

        return PullRequestDescription.builder()
            .title(generatePRTitle(commits))
            .description(description)
            .labels(suggestLabels(analysis))
            .reviewers(suggestReviewers(analysis))
            .build();
    }
}
```

---

## 5. Test Generation

```java
@Service
public class TestGenerationService {

    private final ChatModel codeGenerationModel;
    private final ASTParserService astParser;

    /**
     * Generate unit tests for a function/method
     */
    public GeneratedTest generateUnitTest(CodeFunction function) {
        // Analyze function to determine test cases
        FunctionAnalysis analysis = analyzeFunction(function);

        String prompt = String.format("""
            Generate comprehensive unit tests for this function.

            Language: %s
            Framework: %s

            Function:
            ```%s
            %s
            ```

            Function analysis:
            - Parameters: %s
            - Return type: %s
            - Side effects: %s
            - Dependencies: %s

            Generate tests covering:
            1. Happy path scenarios
            2. Edge cases (null, empty, boundary values)
            3. Error conditions
            4. Integration points

            Use %s testing framework.
            Include setup, assertions, and teardown.
            Add descriptive test names.

            Generate the test code:
            """,
            function.getLanguage(),
            function.getFramework(),
            function.getLanguage(),
            function.getCode(),
            analysis.getParameters(),
            analysis.getReturnType(),
            analysis.getSideEffects(),
            analysis.getDependencies(),
            getTestFramework(function.getLanguage())
        );

        String testCode = codeGenerationModel.call(new Prompt(prompt))
            .getResult()
            .getOutput()
            .getContent();

        return GeneratedTest.builder()
            .code(testCode)
            .framework(getTestFramework(function.getLanguage()))
            .coverage(estimateCoverage(testCode, function))
            .testCases(extractTestCases(testCode))
            .build();
    }

    /**
     * Analyze function to determine test scenarios
     */
    private FunctionAnalysis analyzeFunction(CodeFunction function) {
        ASTNode ast = astParser.parse(function.getCode(), function.getLanguage());

        return FunctionAnalysis.builder()
            .parameters(extractParameters(ast))
            .returnType(extractReturnType(ast))
            .sideEffects(detectSideEffects(ast))
            .dependencies(extractDependencies(ast))
            .complexity(calculateComplexity(ast))
            .edgeCases(identifyEdgeCases(ast))
            .build();
    }

    /**
     * Identify edge cases automatically
     */
    private List<EdgeCase> identifyEdgeCases(ASTNode ast) {
        List<EdgeCase> edgeCases = new ArrayList<>();

        // Check for null handling
        if (!hasNullChecks(ast)) {
            edgeCases.add(EdgeCase.NULL_INPUT);
        }

        // Check for empty collection handling
        if (hasCollectionParameters(ast) && !hasEmptyChecks(ast)) {
            edgeCases.add(EdgeCase.EMPTY_COLLECTION);
        }

        // Check for boundary conditions
        if (hasNumericParameters(ast)) {
            edgeCases.add(EdgeCase.MIN_VALUE);
            edgeCases.add(EdgeCase.MAX_VALUE);
            edgeCases.add(EdgeCase.ZERO);
            edgeCases.add(EdgeCase.NEGATIVE);
        }

        return edgeCases;
    }
}
```

---

## 6. Performance Metrics & Monitoring

### Key Metrics to Track

```java
@Service
public class PerformanceMetricsService {

    private final MeterRegistry meterRegistry;

    // Code Generation Metrics
    public void recordCodeGeneration(Duration duration, boolean success) {
        Timer.builder("cola.code.generation")
            .tag("success", String.valueOf(success))
            .register(meterRegistry)
            .record(duration);
    }

    // Completion Metrics
    public void recordCompletion(Duration latency, boolean accepted) {
        Timer.builder("cola.completion.latency")
            .tag("accepted", String.valueOf(accepted))
            .register(meterRegistry)
            .record(latency);
    }

    // Context Retrieval Metrics
    public void recordContextRetrieval(int chunks, Duration duration) {
        Counter.builder("cola.context.chunks")
            .register(meterRegistry)
            .increment(chunks);

        Timer.builder("cola.context.retrieval")
            .register(meterRegistry)
            .record(duration);
    }

    // AI Model Metrics
    public void recordModelCall(String model, int tokens, Duration duration, double cost) {
        Counter.builder("cola.ai.tokens")
            .tag("model", model)
            .register(meterRegistry)
            .increment(tokens);

        Counter.builder("cola.ai.cost")
            .tag("model", model)
            .register(meterRegistry)
            .increment(cost);
    }
}
```

---

## 7. Quick Implementation Priorities

### Week 1-2: RAG Foundation
1. Integrate Pinecone/Weaviate
2. Implement code chunking with TreeSitter
3. Generate embeddings for existing projects
4. Build semantic search API

### Week 3-4: Code Completion
1. Implement fast completion endpoint
2. Add caching layer with Redis
3. Build prompt optimization
4. Frontend integration

### Week 5-6: Terminal
1. XTerm.js integration
2. WebSocket command execution
3. AI command suggestions
4. Natural language translation

### Week 7-8: Git Intelligence
1. Commit message generation
2. PR description generation
3. Code review assistance

### Week 9-10: Testing
1. Test generation from functions
2. Edge case identification
3. Framework integration

---

## Technology Stack Summary

```yaml
Backend:
  - Spring Boot 3.2+
  - Spring AI (multi-model)
  - TreeSitter (AST parsing)
  - Redis (caching)
  - PostgreSQL (metadata)
  - Pinecone/Weaviate (vectors)

Frontend:
  - React (complex UI)
  - XTerm.js (terminal)
  - Monaco Editor (code)
  - WebSocket (real-time)

AI/ML:
  - Claude 3.5 Sonnet (primary)
  - Claude 3 Haiku (fast completions)
  - GPT-4 Turbo (fallback)
  - OpenAI Embeddings (vectors)

Infrastructure:
  - Docker + K8s
  - Prometheus + Grafana
  - ELK Stack
  - Terraform
```

---

This technical deep dive provides the implementation foundation for building world-class features. Each section can be expanded into full implementation with the provided code patterns.
