package com.cola.agent.rag.service;

import com.cola.agent.rag.model.CodeChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for chunking code into semantically meaningful pieces.
 * Currently uses regex-based parsing. Can be enhanced with TreeSitter later.
 */
@Service
@Slf4j
public class CodeChunkingService {

    @Value("${cola.rag.chunking.max-chunk-size:1000}")
    private int maxChunkSize;

    @Value("${cola.rag.chunking.overlap-size:100}")
    private int overlapSize;

    @Value("${cola.rag.chunking.min-chunk-size:100}")
    private int minChunkSize;

    /**
     * Chunk a code file into meaningful pieces.
     */
    public List<CodeChunk> chunkFile(UUID projectId, String filePath, String content, String language) {
        List<CodeChunk> chunks = new ArrayList<>();

        if (content == null || content.trim().isEmpty()) {
            return chunks;
        }

        log.debug("Chunking file: {} (language: {})", filePath, language);

        // Extract functions and classes based on language
        chunks.addAll(extractStructures(projectId, filePath, content, language));

        // If no structures found or file is small, create chunk for entire file
        if (chunks.isEmpty() || getApproximateTokens(content) < maxChunkSize) {
            chunks.add(createFullFileChunk(projectId, filePath, content, language));
        }

        log.debug("Created {} chunks for file {}", chunks.size(), filePath);
        return chunks;
    }

    /**
     * Extract functions, classes, and other structures from code.
     */
    private List<CodeChunk> extractStructures(UUID projectId, String filePath, String content, String language) {
        List<CodeChunk> chunks = new ArrayList<>();

        switch (language.toLowerCase()) {
            case "java":
            case "kotlin":
                chunks.addAll(extractJavaStructures(projectId, filePath, content, language));
                break;
            case "python":
                chunks.addAll(extractPythonStructures(projectId, filePath, content, language));
                break;
            case "javascript":
            case "typescript":
                chunks.addAll(extractJavaScriptStructures(projectId, filePath, content, language));
                break;
            case "go":
                chunks.addAll(extractGoStructures(projectId, filePath, content, language));
                break;
            default:
                // For unsupported languages, use generic chunking
                chunks.addAll(genericChunking(projectId, filePath, content, language));
        }

        return chunks;
    }

    /**
     * Extract Java/Kotlin classes and methods.
     */
    private List<CodeChunk> extractJavaStructures(UUID projectId, String filePath, String content, String language) {
        List<CodeChunk> chunks = new ArrayList<>();
        String[] lines = content.split("\n");

        // Regex for class declaration
        Pattern classPattern = Pattern.compile("(public|private|protected)?\\s*(static)?\\s*class\\s+(\\w+)");

        // Regex for method declaration
        Pattern methodPattern = Pattern.compile("(public|private|protected)?\\s*(static)?\\s*[\\w<>\\[\\],\\s]+\\s+(\\w+)\\s*\\([^)]*\\)");

        int currentLine = 0;
        for (String line : lines) {
            currentLine++;

            // Check for class
            Matcher classMatcher = classPattern.matcher(line);
            if (classMatcher.find()) {
                String className = classMatcher.group(3);
                String classCode = extractBlock(lines, currentLine - 1);

                if (getApproximateTokens(classCode) >= minChunkSize) {
                    chunks.add(CodeChunk.builder()
                        .projectId(projectId)
                        .filePath(filePath)
                        .content(classCode)
                        .type(CodeChunk.ChunkType.CLASS)
                        .language(language)
                        .name(className)
                        .startLine(currentLine)
                        .endLine(currentLine + countLines(classCode))
                        .timestamp(System.currentTimeMillis())
                        .build());
                }
            }

            // Check for method
            Matcher methodMatcher = methodPattern.matcher(line);
            if (methodMatcher.find() && !line.trim().endsWith(";")) {
                String methodName = methodMatcher.group(3);
                String methodCode = extractBlock(lines, currentLine - 1);

                if (getApproximateTokens(methodCode) >= minChunkSize) {
                    chunks.add(CodeChunk.builder()
                        .projectId(projectId)
                        .filePath(filePath)
                        .content(methodCode)
                        .type(CodeChunk.ChunkType.METHOD)
                        .language(language)
                        .name(methodName)
                        .startLine(currentLine)
                        .endLine(currentLine + countLines(methodCode))
                        .timestamp(System.currentTimeMillis())
                        .build());
                }
            }
        }

        return chunks;
    }

    /**
     * Extract Python functions and classes.
     */
    private List<CodeChunk> extractPythonStructures(UUID projectId, String filePath, String content, String language) {
        List<CodeChunk> chunks = new ArrayList<>();
        String[] lines = content.split("\n");

        Pattern classPattern = Pattern.compile("^class\\s+(\\w+)");
        Pattern functionPattern = Pattern.compile("^def\\s+(\\w+)");

        int currentLine = 0;
        for (String line : lines) {
            currentLine++;

            Matcher classMatcher = classPattern.matcher(line.trim());
            if (classMatcher.find()) {
                String className = classMatcher.group(1);
                String classCode = extractPythonBlock(lines, currentLine - 1);

                if (getApproximateTokens(classCode) >= minChunkSize) {
                    chunks.add(CodeChunk.builder()
                        .projectId(projectId)
                        .filePath(filePath)
                        .content(classCode)
                        .type(CodeChunk.ChunkType.CLASS)
                        .language(language)
                        .name(className)
                        .startLine(currentLine)
                        .endLine(currentLine + countLines(classCode))
                        .timestamp(System.currentTimeMillis())
                        .build());
                }
            }

            Matcher functionMatcher = functionPattern.matcher(line.trim());
            if (functionMatcher.find()) {
                String functionName = functionMatcher.group(1);
                String functionCode = extractPythonBlock(lines, currentLine - 1);

                if (getApproximateTokens(functionCode) >= minChunkSize) {
                    chunks.add(CodeChunk.builder()
                        .projectId(projectId)
                        .filePath(filePath)
                        .content(functionCode)
                        .type(CodeChunk.ChunkType.FUNCTION)
                        .language(language)
                        .name(functionName)
                        .startLine(currentLine)
                        .endLine(currentLine + countLines(functionCode))
                        .timestamp(System.currentTimeMillis())
                        .build());
                }
            }
        }

        return chunks;
    }

    /**
     * Extract JavaScript/TypeScript functions and classes.
     */
    private List<CodeChunk> extractJavaScriptStructures(UUID projectId, String filePath, String content, String language) {
        List<CodeChunk> chunks = new ArrayList<>();
        String[] lines = content.split("\n");

        Pattern classPattern = Pattern.compile("(export\\s+)?(default\\s+)?class\\s+(\\w+)");
        Pattern functionPattern = Pattern.compile("(export\\s+)?(async\\s+)?function\\s+(\\w+)");
        Pattern arrowFunctionPattern = Pattern.compile("(const|let|var)\\s+(\\w+)\\s*=\\s*(async\\s+)?\\([^)]*\\)\\s*=>");

        int currentLine = 0;
        for (String line : lines) {
            currentLine++;

            Matcher classMatcher = classPattern.matcher(line);
            if (classMatcher.find()) {
                String className = classMatcher.group(3);
                String classCode = extractBlock(lines, currentLine - 1);

                if (getApproximateTokens(classCode) >= minChunkSize) {
                    chunks.add(CodeChunk.builder()
                        .projectId(projectId)
                        .filePath(filePath)
                        .content(classCode)
                        .type(CodeChunk.ChunkType.CLASS)
                        .language(language)
                        .name(className)
                        .startLine(currentLine)
                        .endLine(currentLine + countLines(classCode))
                        .timestamp(System.currentTimeMillis())
                        .build());
                }
            }

            Matcher functionMatcher = functionPattern.matcher(line);
            if (functionMatcher.find()) {
                String functionName = functionMatcher.group(3);
                String functionCode = extractBlock(lines, currentLine - 1);

                if (getApproximateTokens(functionCode) >= minChunkSize) {
                    chunks.add(CodeChunk.builder()
                        .projectId(projectId)
                        .filePath(filePath)
                        .content(functionCode)
                        .type(CodeChunk.ChunkType.FUNCTION)
                        .language(language)
                        .name(functionName)
                        .startLine(currentLine)
                        .endLine(currentLine + countLines(functionCode))
                        .timestamp(System.currentTimeMillis())
                        .build());
                }
            }
        }

        return chunks;
    }

    /**
     * Extract Go functions and structs.
     */
    private List<CodeChunk> extractGoStructures(UUID projectId, String filePath, String content, String language) {
        // Similar implementation for Go
        return genericChunking(projectId, filePath, content, language);
    }

    /**
     * Generic chunking for unsupported languages.
     */
    private List<CodeChunk> genericChunking(UUID projectId, String filePath, String content, String language) {
        List<CodeChunk> chunks = new ArrayList<>();
        String[] lines = content.split("\n");

        StringBuilder currentChunk = new StringBuilder();
        int startLine = 1;
        int currentLine = 0;

        for (String line : lines) {
            currentLine++;
            currentChunk.append(line).append("\n");

            if (getApproximateTokens(currentChunk.toString()) >= maxChunkSize) {
                chunks.add(CodeChunk.builder()
                    .projectId(projectId)
                    .filePath(filePath)
                    .content(currentChunk.toString())
                    .type(CodeChunk.ChunkType.FULL_FILE)
                    .language(language)
                    .startLine(startLine)
                    .endLine(currentLine)
                    .timestamp(System.currentTimeMillis())
                    .build());

                // Start new chunk with overlap
                String[] chunkLines = currentChunk.toString().split("\n");
                currentChunk = new StringBuilder();
                int overlapLines = Math.min(overlapSize / 20, chunkLines.length); // ~20 chars per line
                for (int i = chunkLines.length - overlapLines; i < chunkLines.length; i++) {
                    currentChunk.append(chunkLines[i]).append("\n");
                }
                startLine = currentLine - overlapLines + 1;
            }
        }

        // Add remaining content
        if (currentChunk.length() > 0) {
            chunks.add(CodeChunk.builder()
                .projectId(projectId)
                .filePath(filePath)
                .content(currentChunk.toString())
                .type(CodeChunk.ChunkType.FULL_FILE)
                .language(language)
                .startLine(startLine)
                .endLine(currentLine)
                .timestamp(System.currentTimeMillis())
                .build());
        }

        return chunks;
    }

    /**
     * Create a chunk for the entire file.
     */
    private CodeChunk createFullFileChunk(UUID projectId, String filePath, String content, String language) {
        return CodeChunk.builder()
            .projectId(projectId)
            .filePath(filePath)
            .content(content)
            .type(CodeChunk.ChunkType.FULL_FILE)
            .language(language)
            .startLine(1)
            .endLine(countLines(content))
            .timestamp(System.currentTimeMillis())
            .build();
    }

    /**
     * Extract a code block starting from a line (for languages with braces).
     */
    private String extractBlock(String[] lines, int startIndex) {
        StringBuilder block = new StringBuilder();
        int braceCount = 0;
        boolean started = false;

        for (int i = startIndex; i < lines.length; i++) {
            String line = lines[i];
            block.append(line).append("\n");

            for (char c : line.toCharArray()) {
                if (c == '{') {
                    braceCount++;
                    started = true;
                } else if (c == '}') {
                    braceCount--;
                }
            }

            if (started && braceCount == 0) {
                break;
            }
        }

        return block.toString();
    }

    /**
     * Extract a Python block based on indentation.
     */
    private String extractPythonBlock(String[] lines, int startIndex) {
        StringBuilder block = new StringBuilder();
        int baseIndent = getIndentLevel(lines[startIndex]);

        block.append(lines[startIndex]).append("\n");

        for (int i = startIndex + 1; i < lines.length; i++) {
            String line = lines[i];

            if (line.trim().isEmpty()) {
                block.append(line).append("\n");
                continue;
            }

            int currentIndent = getIndentLevel(line);
            if (currentIndent <= baseIndent) {
                break;
            }

            block.append(line).append("\n");
        }

        return block.toString();
    }

    /**
     * Get indentation level of a line.
     */
    private int getIndentLevel(String line) {
        int spaces = 0;
        for (char c : line.toCharArray()) {
            if (c == ' ') spaces++;
            else if (c == '\t') spaces += 4;
            else break;
        }
        return spaces;
    }

    /**
     * Get approximate token count (rough estimate: 1 token ≈ 4 characters).
     */
    private int getApproximateTokens(String text) {
        return text.length() / 4;
    }

    /**
     * Count lines in text.
     */
    private int countLines(String text) {
        return text.split("\n").length;
    }
}
