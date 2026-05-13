package io.github.xiaoailazy.coexistree.chat.service;

import io.github.xiaoailazy.coexistree.config.LlmProperties;
import io.github.xiaoailazy.coexistree.indexer.llm.LlmClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for generating conversation titles with fallback strategy.
 * 
 * <p>Generation strategy:
 * <ol>
 *   <li>If LLM title generation is enabled, attempt to generate title using LLM</li>
 *   <li>If LLM fails or is disabled, fall back to simple truncation at word boundary</li>
 *   <li>If input is empty, return default title "新会话"</li>
 * </ol>
 */
@Slf4j
@Service
public class TitleGenerationService {
    
    private final LlmClient llmClient;
    private final LlmProperties llmProperties;
    
    public TitleGenerationService(LlmClient llmClient, LlmProperties llmProperties) {
        this.llmClient = llmClient;
        this.llmProperties = llmProperties;
    }
    
    /**
     * Generate a conversation title based on the first message content.
     * 
     * @param conversationId the conversation ID (for logging purposes)
     * @param firstMessage the first user message content
     * @return generated title
     */
    public String generateTitle(String conversationId, String firstMessage) {
        // Handle empty input
        if (firstMessage == null || firstMessage.isBlank()) {
            return "新会话";
        }
        
        // Simple mode: truncate at word boundary (max 50 chars)
        String simpleTitle = truncateAtWordBoundary(firstMessage, 50);
        
        // If LLM title generation is enabled and configured, use LLM
        if (llmProperties.isEnabled() && llmClient.isConfigured()) {
            try {
                log.debug("Attempting LLM title generation for conversation: {}", conversationId);
                return generateTitleWithLLM(firstMessage);
            } catch (Exception e) {
                log.warn("LLM title generation failed for conversation {}, using simple title. Error: {}", 
                        conversationId, e.getMessage());
                return simpleTitle;
            }
        }
        
        // Fall back to simple truncation
        log.debug("Using simple title generation for conversation: {}", conversationId);
        return simpleTitle;
    }
    
    /**
     * Truncate text at word boundary to avoid breaking words.
     * 
     * @param text the text to truncate
     * @param maxLength maximum length (default 50)
     * @return truncated text with "..." appended if truncated
     */
    private String truncateAtWordBoundary(String text, int maxLength) {
        if (text == null || text.isEmpty()) {
            return "新会话";
        }
        
        // Normalize whitespace
        text = text.replaceAll("\\s+", " ").trim();
        
        if (text.length() <= maxLength) {
            return text;
        }
        
        String truncated = text.substring(0, maxLength);
        
        // Find last space to avoid breaking words
        int lastSpace = truncated.lastIndexOf(' ');
        if (lastSpace > 0) {
            truncated = truncated.substring(0, lastSpace);
        }
        
        return truncated + "...";
    }
    
    /**
     * Generate title using LLM with a concise prompt.
     * 
     * @param message the first user message
     * @return LLM-generated title
     */
    private String generateTitleWithLLM(String message) {
        String prompt = String.format(
            "请为以下对话生成一个简洁的标题（最多10个中文字符）：\n\n%s",
            message
        );
        
        // Call LLM API with low temperature for stable output
        LlmClient.LlmResponse response = llmClient.chat(prompt, null, 0.3);
        String title = response.content();
        
        // Clean title: remove quotes, newlines, and other unwanted characters
        title = title.replaceAll("[\"'\\n\\r]", "").trim();
        
        // Limit length to 20 characters
        if (title.length() > 20) {
            title = title.substring(0, 20);
        }
        
        // Return cleaned title or default if empty
        return title.isEmpty() ? "新会话" : title;
    }
}
