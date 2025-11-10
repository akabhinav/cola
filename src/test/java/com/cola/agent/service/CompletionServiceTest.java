package com.cola.agent.service;

import com.cola.agent.dto.CompletionChunk;
import com.cola.agent.dto.CompletionRequest;
import com.cola.agent.dto.CompletionResponse;
import com.cola.agent.rag.service.ContextAssemblyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for CompletionService.
 */
@ExtendWith(MockitoExtension.class)
class CompletionServiceTest {

    @Mock
    private ChatModel fastCompletionModel;

    @Mock
    private ContextAssemblyService contextAssemblyService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private CompletionService completionService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        completionService = new CompletionService(fastCompletionModel, contextAssemblyService, redisTemplate);
    }

    @Test
    void testComplete_ReturnsCompletion() {
        // Given
        CompletionRequest request = CompletionRequest.builder()
            .projectId(UUID.randomUUID())
            .filePath("Calculator.java")
            .language("java")
            .cursorPosition(CompletionRequest.CursorPosition.builder()
                .line(5)
                .column(20)
                .build())
            .prefix("public class Calculator {\n    public int add(int a, int b) {\n        ")
            .suffix("\n    }\n}")
            .multiLine(false)
            .build();

        // Mock cache miss
        when(valueOperations.get(anyString())).thenReturn(null);

        // Mock AI response
        ChatResponse mockResponse = mock(ChatResponse.class);
        when(mockResponse.getResult()).thenReturn(mock(org.springframework.ai.chat.model.Generation.class));
        when(mockResponse.getResult().getOutput()).thenReturn(mock(org.springframework.ai.chat.messages.AssistantMessage.class));
        when(mockResponse.getResult().getOutput().getContent()).thenReturn("return a + b;");
        when(fastCompletionModel.call(any(Prompt.class))).thenReturn(mockResponse);

        // When
        CompletionResponse response = completionService.complete(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getCompletion()).isEqualTo("return a + b;");
        assertThat(response.getSource()).isEqualTo(CompletionResponse.CompletionSource.MODEL);
        assertThat(response.getLatencyMs()).isGreaterThan(0);

        // Verify cache was written
        verify(valueOperations, times(1)).set(anyString(), anyString(), any());
    }

    @Test
    void testComplete_ReturnsCachedResult() {
        // Given
        CompletionRequest request = CompletionRequest.builder()
            .projectId(UUID.randomUUID())
            .filePath("Calculator.java")
            .language("java")
            .cursorPosition(CompletionRequest.CursorPosition.builder()
                .line(5)
                .column(20)
                .build())
            .prefix("public class Calculator {\n    public int add(int a, int b) {\n        ")
            .suffix("\n    }\n}")
            .build();

        // Mock cache hit
        when(valueOperations.get(anyString())).thenReturn("return a + b;");

        // When
        CompletionResponse response = completionService.complete(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getCompletion()).isEqualTo("return a + b;");
        assertThat(response.getSource()).isEqualTo(CompletionResponse.CompletionSource.CACHE);
        assertThat(response.getLatencyMs()).isLessThan(50); // Cache should be fast

        // Verify AI was not called
        verify(fastCompletionModel, never()).call(any(Prompt.class));
    }

    @Test
    void testComplete_HandlesErrors() {
        // Given
        CompletionRequest request = CompletionRequest.builder()
            .projectId(UUID.randomUUID())
            .filePath("Calculator.java")
            .language("java")
            .cursorPosition(CompletionRequest.CursorPosition.builder()
                .line(5)
                .column(20)
                .build())
            .prefix("public class Calculator {")
            .build();

        // Mock cache miss
        when(valueOperations.get(anyString())).thenReturn(null);

        // Mock AI error
        when(fastCompletionModel.call(any(Prompt.class)))
            .thenThrow(new RuntimeException("AI service error"));

        // When
        CompletionResponse response = completionService.complete(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getCompletion()).isEmpty();
        assertThat(response.getSource()).isEqualTo(CompletionResponse.CompletionSource.FALLBACK);
    }

    @Test
    void testStreamCompletion_StreamsChunks() {
        // Given
        CompletionRequest request = CompletionRequest.builder()
            .projectId(UUID.randomUUID())
            .filePath("test.py")
            .language("python")
            .cursorPosition(CompletionRequest.CursorPosition.builder()
                .line(1)
                .column(1)
                .build())
            .prefix("def calculate(")
            .build();

        // Mock cache miss
        when(valueOperations.get(anyString())).thenReturn(null);

        // Mock streaming response
        ChatResponse chunk1 = mock(ChatResponse.class);
        when(chunk1.getResult()).thenReturn(mock(org.springframework.ai.chat.model.Generation.class));
        when(chunk1.getResult().getOutput()).thenReturn(mock(org.springframework.ai.chat.messages.AssistantMessage.class));
        when(chunk1.getResult().getOutput().getContent()).thenReturn("x, ");

        ChatResponse chunk2 = mock(ChatResponse.class);
        when(chunk2.getResult()).thenReturn(mock(org.springframework.ai.chat.model.Generation.class));
        when(chunk2.getResult().getOutput()).thenReturn(mock(org.springframework.ai.chat.messages.AssistantMessage.class));
        when(chunk2.getResult().getOutput().getContent()).thenReturn("y):");

        when(fastCompletionModel.stream(any(Prompt.class)))
            .thenReturn(Flux.just(chunk1, chunk2));

        // When
        Flux<CompletionChunk> stream = completionService.streamCompletion(request);

        // Then
        StepVerifier.create(stream)
            .expectNextMatches(chunk -> chunk.getText().equals("x, ") && chunk.getSequence() == 0)
            .expectNextMatches(chunk -> chunk.getText().equals("y):") && chunk.getSequence() == 1)
            .expectNextMatches(CompletionChunk::isComplete)
            .verifyComplete();
    }

    @Test
    void testStreamCompletion_ReturnsCachedImmediately() {
        // Given
        CompletionRequest request = CompletionRequest.builder()
            .projectId(UUID.randomUUID())
            .filePath("test.py")
            .language("python")
            .cursorPosition(CompletionRequest.CursorPosition.builder()
                .line(1)
                .column(1)
                .build())
            .prefix("def calculate(")
            .build();

        // Mock cache hit
        when(valueOperations.get(anyString())).thenReturn("x, y):");

        // When
        Flux<CompletionChunk> stream = completionService.streamCompletion(request);

        // Then
        StepVerifier.create(stream)
            .expectNextMatches(chunk -> chunk.getText().equals("x, y):") && chunk.isComplete())
            .verifyComplete();

        // Verify streaming was not used
        verify(fastCompletionModel, never()).stream(any(Prompt.class));
    }
}
