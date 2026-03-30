package io.github.xiaoailazy.coexistree.indexer.llm;

import com.volcengine.ark.runtime.model.responses.common.ResponsesThinking;
import com.volcengine.ark.runtime.model.responses.constant.ResponsesConstants;
import com.volcengine.ark.runtime.model.responses.content.OutputContentItem;
import com.volcengine.ark.runtime.model.responses.content.OutputContentItemText;
import com.volcengine.ark.runtime.model.responses.item.BaseItem;
import com.volcengine.ark.runtime.model.responses.item.ItemOutputMessage;
import com.volcengine.ark.runtime.model.responses.request.CreateResponsesRequest;
import com.volcengine.ark.runtime.model.responses.request.ResponsesInput;
import com.volcengine.ark.runtime.model.responses.response.ResponseObject;
import com.volcengine.ark.runtime.service.ArkService;
import com.volcengine.ark.runtime.model.responses.event.outputtext.OutputTextDeltaEvent;
import com.volcengine.ark.runtime.model.responses.event.reasoningsummary.ReasoningSummaryTextDeltaEvent;
import com.volcengine.ark.runtime.model.responses.event.response.ResponseCompletedEvent;
import io.github.xiaoailazy.coexistree.config.LlmProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Component
public class LlmClient {

    private final LlmProperties llmProperties;
    private final ArkService arkService;

    public LlmClient(LlmProperties llmProperties) {
        this.llmProperties = llmProperties;
        this.arkService = ArkService.builder()
                .apiKey(llmProperties.apiKey())
                .baseUrl(llmProperties.baseUrl())
                .build();
        log.info("LlmClient 初始化完成, baseUrl={}, defaultModel={}",
                llmProperties.baseUrl(), llmProperties.defaultModel());
    }

    public LlmResponse chat(String prompt, String model, Double temperature) {
        return chat(prompt, model, temperature, null);
    }

    public LlmResponse chat(String prompt, String model, Double temperature, String previousResponseId) {
        String resolvedModel = resolveModel(model);
        log.debug("开始调用LLM, model={}, promptLength={}, previousResponseId={}",
                resolvedModel, prompt.length(), previousResponseId);

        CreateResponsesRequest.Builder requestBuilder = CreateResponsesRequest.builder()
                .model(resolvedModel)
                .input(ResponsesInput.builder().stringValue(prompt).build())
                .temperature(temperature)
                .thinking(ResponsesThinking.builder()
                        .type(ResponsesConstants.THINKING_TYPE_DISABLED)
                        .build());

        if (previousResponseId != null && !previousResponseId.isBlank()) {
            requestBuilder.previousResponseId(previousResponseId);
        }

        CreateResponsesRequest request = requestBuilder.build();

        long startTime = System.currentTimeMillis();
        ResponseObject response = arkService.createResponse(request);
        long elapsed = System.currentTimeMillis() - startTime;

        String result = extractText(response);
        String responseId = response.getId();
        log.debug("LLM调用完成, 耗时{}ms, 响应长度={}, responseId={}", elapsed, result.length(), responseId);

        return new LlmResponse(responseId, result);
    }

    public String chatStream(String prompt, String model, Double temperature,
                             String previousResponseId,
                             Consumer<String> onThinking, Consumer<String> onText) {
        String resolvedModel = resolveModel(model);
        log.debug("开始流式调用LLM, model={}, promptLength={}, previousResponseId={}",
                resolvedModel, prompt.length(), previousResponseId);

        CreateResponsesRequest.Builder requestBuilder = CreateResponsesRequest.builder()
                .model(resolvedModel)
                .stream(true)
                .input(ResponsesInput.builder().stringValue(prompt).build())
                .temperature(temperature)
                .thinking(ResponsesThinking.builder()
                        .type(ResponsesConstants.THINKING_TYPE_ENABLED)
                        .build());

        if (previousResponseId != null && !previousResponseId.isBlank()) {
            requestBuilder.previousResponseId(previousResponseId);
        }

        CreateResponsesRequest request = requestBuilder.build();

        long startTime = System.currentTimeMillis();
        StringBuilder thinkingBuilder = new StringBuilder();
        StringBuilder textBuilder = new StringBuilder();
        String[] responseIdHolder = new String[1];

        arkService.streamResponse(request)
                .doOnError(e -> log.error("流式调用LLM失败", e))
                .blockingForEach(event -> {
                    if (event instanceof ReasoningSummaryTextDeltaEvent deltaEvent) {
                        String delta = deltaEvent.getDelta();
                        if (delta != null && !delta.isEmpty()) {
                            thinkingBuilder.append(delta);
                            if (onThinking != null) {
                                onThinking.accept(delta);
                            }
                        }
                    } else if (event instanceof OutputTextDeltaEvent deltaEvent) {
                        String delta = deltaEvent.getDelta();
                        if (delta != null && !delta.isEmpty()) {
                            textBuilder.append(delta);
                            if (onText != null) {
                                onText.accept(delta);
                            }
                        }
                    } else if (event instanceof ResponseCompletedEvent completedEvent) {
                        responseIdHolder[0] = completedEvent.getResponse().getId();
                        log.debug("流式调用完成, responseId={}", responseIdHolder[0]);
                    }
                });

        long elapsed = System.currentTimeMillis() - startTime;
        log.debug("流式LLM调用完成, 耗时{}ms, 思考长度={}, 回答长度={}, responseId={}",
                elapsed, thinkingBuilder.length(), textBuilder.length(), responseIdHolder[0]);

        return responseIdHolder[0];
    }

    public String defaultModel() {
        return llmProperties.defaultModel();
    }

    public boolean isConfigured() {
        return true;
    }

    @PreDestroy
    public void shutdown() {
        log.info("关闭LlmClient");
        arkService.shutdownExecutor();
    }

    String extractText(ResponseObject response) {
        if (response == null || response.getOutput() == null) {
            log.warn("LLM响应为空");
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (BaseItem item : response.getOutput()) {
            if (!(item instanceof ItemOutputMessage outputMessage)) {
                continue;
            }
            List<OutputContentItem> contentItems = outputMessage.getContent();
            if (contentItems == null) {
                continue;
            }
            for (OutputContentItem contentItem : contentItems) {
                if (contentItem instanceof OutputContentItemText textItem
                        && textItem.getText() != null
                        && !textItem.getText().isBlank()) {
                    if (!builder.isEmpty()) {
                        builder.append('\n');
                    }
                    builder.append(textItem.getText().trim());
                }
            }
        }
        return builder.toString().trim();
    }

    private String resolveModel(String model) {
        return (model != null && !model.isBlank()) ? model : llmProperties.defaultModel();
    }

    public record LlmResponse(String responseId, String content) {}
}
