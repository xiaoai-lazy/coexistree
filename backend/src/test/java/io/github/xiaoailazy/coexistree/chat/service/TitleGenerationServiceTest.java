package io.github.xiaoailazy.coexistree.chat.service;

import io.github.xiaoailazy.coexistree.config.LlmProperties;
import io.github.xiaoailazy.coexistree.indexer.llm.LlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TitleGenerationServiceTest {

    @Mock
    private LlmClient llmClient;

    @Mock
    private LlmProperties llmProperties;

    private TitleGenerationService titleGenerationService;

    @BeforeEach
    void setUp() {
        titleGenerationService = new TitleGenerationService(llmClient, llmProperties);
    }

    @Test
    void generateTitle_withEmptyMessage_shouldReturnDefaultTitle() {
        // When
        String title = titleGenerationService.generateTitle("conv-123", "");

        // Then
        assertThat(title).isEqualTo("新会话");
    }

    @Test
    void generateTitle_withNullMessage_shouldReturnDefaultTitle() {
        // When
        String title = titleGenerationService.generateTitle("conv-123", null);

        // Then
        assertThat(title).isEqualTo("新会话");
    }

    @Test
    void generateTitle_withShortMessage_shouldReturnFullMessage() {
        // Given
        String message = "Hello World";
        when(llmProperties.isEnabled()).thenReturn(false);

        // When
        String title = titleGenerationService.generateTitle("conv-123", message);

        // Then
        assertThat(title).isEqualTo("Hello World");
    }

    @Test
    void generateTitle_withLongMessage_shouldTruncateAtWordBoundary() {
        // Given
        String message = "This is a very long message that exceeds the maximum length of fifty characters and should be truncated";
        when(llmProperties.isEnabled()).thenReturn(false);

        // When
        String title = titleGenerationService.generateTitle("conv-123", message);

        // Then
        assertThat(title).endsWith("...");
        assertThat(title).startsWith("This is a very long message that exceeds the");
        // The truncation should happen at word boundary, so it should be less than 50 + 3 ("...")
        assertThat(title.length()).isLessThanOrEqualTo(53);
        // Should not break words
        assertThat(title).doesNotEndWith("maxi...");
    }

    @Test
    void generateTitle_withMessageAtBoundary_shouldNotAddEllipsis() {
        // Given
        String message = "This is exactly fifty characters in this message!";
        when(llmProperties.isEnabled()).thenReturn(false);

        // When
        String title = titleGenerationService.generateTitle("conv-123", message);

        // Then
        assertThat(title).isEqualTo(message);
        assertThat(title).doesNotEndWith("...");
    }

    @Test
    void generateTitle_withMultipleSpaces_shouldNormalizeWhitespace() {
        // Given
        String message = "Hello    World    with    multiple    spaces";
        when(llmProperties.isEnabled()).thenReturn(false);

        // When
        String title = titleGenerationService.generateTitle("conv-123", message);

        // Then
        assertThat(title).isEqualTo("Hello World with multiple spaces");
    }

    @Test
    void generateTitle_withLLMEnabled_shouldUseLLMGeneration() {
        // Given
        String message = "What is the weather like today?";
        String llmGeneratedTitle = "天气查询";
        
        when(llmProperties.isEnabled()).thenReturn(true);
        when(llmClient.isConfigured()).thenReturn(true);
        when(llmClient.chat(anyString(), isNull(), eq(0.3)))
                .thenReturn(new LlmClient.LlmResponse("resp-123", llmGeneratedTitle, null));

        // When
        String title = titleGenerationService.generateTitle("conv-123", message);

        // Then
        assertThat(title).isEqualTo(llmGeneratedTitle);
    }

    @Test
    void generateTitle_withLLMEnabledButFails_shouldFallbackToSimpleTitle() {
        // Given
        String message = "What is the weather like today?";
        
        when(llmProperties.isEnabled()).thenReturn(true);
        when(llmClient.isConfigured()).thenReturn(true);
        when(llmClient.chat(anyString(), isNull(), eq(0.3)))
                .thenThrow(new RuntimeException("LLM service unavailable"));

        // When
        String title = titleGenerationService.generateTitle("conv-123", message);

        // Then
        assertThat(title).isEqualTo("What is the weather like today?");
    }

    @Test
    void generateTitle_withLLMDisabled_shouldUseSimpleTitle() {
        // Given
        String message = "What is the weather like today?";
        when(llmProperties.isEnabled()).thenReturn(false);

        // When
        String title = titleGenerationService.generateTitle("conv-123", message);

        // Then
        assertThat(title).isEqualTo("What is the weather like today?");
    }

    @Test
    void generateTitle_withLLMReturningEmptyTitle_shouldReturnDefaultTitle() {
        // Given
        String message = "What is the weather like today?";
        
        when(llmProperties.isEnabled()).thenReturn(true);
        when(llmClient.isConfigured()).thenReturn(true);
        when(llmClient.chat(anyString(), isNull(), eq(0.3)))
                .thenReturn(new LlmClient.LlmResponse("resp-123", "", null));

        // When
        String title = titleGenerationService.generateTitle("conv-123", message);

        // Then
        assertThat(title).isEqualTo("新会话");
    }

    @Test
    void generateTitle_withLLMReturningLongTitle_shouldTruncateTo20Chars() {
        // Given
        String message = "What is the weather like today?";
        String longTitle = "这是一个非常非常非常长的标题超过了二十个字符的限制";
        
        when(llmProperties.isEnabled()).thenReturn(true);
        when(llmClient.isConfigured()).thenReturn(true);
        when(llmClient.chat(anyString(), isNull(), eq(0.3)))
                .thenReturn(new LlmClient.LlmResponse("resp-123", longTitle, null));

        // When
        String title = titleGenerationService.generateTitle("conv-123", message);

        // Then
        assertThat(title).hasSize(20);
        assertThat(title).isEqualTo(longTitle.substring(0, 20));
    }

    @Test
    void generateTitle_withLLMReturningTitleWithQuotes_shouldCleanQuotes() {
        // Given
        String message = "What is the weather like today?";
        String titleWithQuotes = "\"天气查询\"";
        
        when(llmProperties.isEnabled()).thenReturn(true);
        when(llmClient.isConfigured()).thenReturn(true);
        when(llmClient.chat(anyString(), isNull(), eq(0.3)))
                .thenReturn(new LlmClient.LlmResponse("resp-123", titleWithQuotes, null));

        // When
        String title = titleGenerationService.generateTitle("conv-123", message);

        // Then
        assertThat(title).isEqualTo("天气查询");
        assertThat(title).doesNotContain("\"");
    }

    @Test
    void generateTitle_withLLMReturningTitleWithNewlines_shouldCleanNewlines() {
        // Given
        String message = "What is the weather like today?";
        String titleWithNewlines = "天气\n查询\r\n";
        
        when(llmProperties.isEnabled()).thenReturn(true);
        when(llmClient.isConfigured()).thenReturn(true);
        when(llmClient.chat(anyString(), isNull(), eq(0.3)))
                .thenReturn(new LlmClient.LlmResponse("resp-123", titleWithNewlines, null));

        // When
        String title = titleGenerationService.generateTitle("conv-123", message);

        // Then
        assertThat(title).isEqualTo("天气查询");
        assertThat(title).doesNotContain("\n");
        assertThat(title).doesNotContain("\r");
    }
}
