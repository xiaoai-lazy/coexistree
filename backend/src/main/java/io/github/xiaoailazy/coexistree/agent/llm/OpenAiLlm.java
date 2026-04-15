package io.github.xiaoailazy.coexistree.agent.llm;

import com.openai.client.OpenAIClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseStreamEvent;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.BaseLlmConnection;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Blob;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI adapter for Google ADK's BaseLlm interface.
 * Wraps the existing OpenAIClient to work with ADK's agent framework.
 */
@Slf4j
public class OpenAiLlm extends BaseLlm {

    private final OpenAIClient client;

    public OpenAiLlm(String model, OpenAIClient client) {
        super(model);
        this.client = client;
    }

    @Override
    public Flowable<LlmResponse> generateContent(LlmRequest request, boolean streaming) {
        return streaming ? generateStreaming(request) : generateBlocking(request);
    }

    @Override
    public BaseLlmConnection connect(LlmRequest request) {
        return new OpenAiLlmConnection(this, request);
    }

    private Flowable<LlmResponse> generateStreaming(LlmRequest request) {
        return Flowable.create(emitter -> {
            String prompt = buildPrompt(request);
            log.debug("OpenAiLlm streaming generateContent, model={}, promptLength={}", model(), prompt.length());

            ResponseCreateParams params = ResponseCreateParams.builder()
                    .model(model())
                    .input(prompt)
                    .build();

            try (StreamResponse<ResponseStreamEvent> stream = client.responses().createStreaming(params)) {
                stream.stream().forEach(event -> {
                    if (event.isOutputTextDelta()) {
                        String delta = event.asOutputTextDelta().delta();
                        emitter.onNext(LlmResponse.builder()
                                .content(Content.fromParts(Part.fromText(delta)))
                                .build());
                    }
                });
                emitter.onComplete();
            } catch (Exception e) {
                log.error("OpenAiLlm streaming generateContent failed, model={}", model(), e);
                emitter.onError(e);
            }
        }, BackpressureStrategy.BUFFER);
    }

    private Flowable<LlmResponse> generateBlocking(LlmRequest request) {
        return Flowable.create(emitter -> {
            String prompt = buildPrompt(request);
            log.debug("OpenAiLlm blocking generateContent, model={}, promptLength={}", model(), prompt.length());

            ResponseCreateParams params = ResponseCreateParams.builder()
                    .model(model())
                    .input(prompt)
                    .build();

            try {
                var response = client.responses().create(params);
                StringBuilder text = new StringBuilder();
                for (var item : response.output()) {
                    if (item.isMessage()) {
                        for (var content : item.asMessage().content()) {
                            if (content.isOutputText()) {
                                text.append(content.asOutputText().text());
                            } else if (content.isRefusal()) {
                                text.append(content.asRefusal().refusal());
                            }
                        }
                    }
                }
                emitter.onNext(LlmResponse.builder()
                        .content(Content.fromParts(Part.fromText(text.toString())))
                        .build());
                emitter.onComplete();
            } catch (Exception e) {
                log.error("OpenAiLlm blocking generateContent failed, model={}", model(), e);
                emitter.onError(e);
            }
        }, BackpressureStrategy.BUFFER);
    }

    private String buildPrompt(LlmRequest request) {
        if (request.contents().isEmpty()) {
            return "";
        }

        StringBuilder prompt = new StringBuilder();
        for (Content content : request.contents()) {
            String text = content.text();
            if (text != null && !text.isBlank()) {
                prompt.append(text).append("\n");
            }
        }
        return prompt.toString().trim();
    }

    /**
     * Persistent connection implementation for streaming conversations.
     */
    private class OpenAiLlmConnection implements BaseLlmConnection {
        private final OpenAiLlm llm;
        private final List<Content> conversationHistory = new ArrayList<>();
        private volatile boolean closed = false;

        OpenAiLlmConnection(OpenAiLlm llm, LlmRequest request) {
            this.llm = llm;
        }

        @Override
        public Completable sendHistory(List<Content> history) {
            synchronized (conversationHistory) {
                conversationHistory.clear();
                if (history != null) {
                    conversationHistory.addAll(history);
                }
            }
            return Completable.complete();
        }

        @Override
        public Completable sendContent(Content content) {
            if (content != null) {
                synchronized (conversationHistory) {
                    conversationHistory.add(content);
                }
            }
            return Completable.complete();
        }

        @Override
        public Completable sendRealtime(Blob blob) {
            log.warn("OpenAiLlm sendRealtime not supported, ignoring");
            return Completable.complete();
        }

        @Override
        public Flowable<LlmResponse> receive() {
            return Flowable.create(emitter -> {
                if (closed) {
                    emitter.onComplete();
                    return;
                }
                try {
                    List<Content> snapshot;
                    synchronized (conversationHistory) {
                        snapshot = new ArrayList<>(conversationHistory);
                    }
                    LlmRequest request = LlmRequest.builder()
                            .contents(snapshot)
                            .build();
                    llm.generateStreaming(request).subscribe(
                            emitter::onNext,
                            emitter::onError,
                            emitter::onComplete
                    );
                } catch (Exception e) {
                    emitter.onError(e);
                }
            }, BackpressureStrategy.BUFFER);
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public void close(Throwable error) {
            closed = true;
            log.warn("OpenAiLlmConnection closed with error", error);
        }
    }
}
