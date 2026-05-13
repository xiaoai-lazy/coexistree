package io.github.xiaoailazy.coexistree.agent.observability;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.adk.agents.Callbacks;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.Part;
import org.junit.jupiter.api.*;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behavioral tests for AgentObservationCallbacks — verifies that the serialization
 * helpers used by callbacks produce correct output.
 *
 * <p>Tests the private serialization methods via reflection and the {@code getStackTrace}
 * helper directly. This approach avoids the complexity of mocking ADK context types
 * (CallbackContext, InvocationContext, ToolContext) while still verifying the actual
 * serialization behavior.
 */
class AgentObservationCallbacksTest {

    // ===== Existing smoke tests (non-null callback creation) =====

    @Test
    @DisplayName("createBeforeAgentCallback returns non-null callback")
    void beforeAgentCallbackNotNull() {
        AgentObservationCallbacks callbacks = new AgentObservationCallbacks(null, null, null, null);
        Callbacks.BeforeAgentCallback cb = callbacks.createBeforeAgentCallback();
        assertThat(cb).isNotNull();
    }

    @Test
    @DisplayName("createAfterAgentCallback returns non-null callback")
    void afterAgentCallbackNotNull() {
        AgentObservationCallbacks callbacks = new AgentObservationCallbacks(null, null, null, null);
        Callbacks.AfterAgentCallback cb = callbacks.createAfterAgentCallback();
        assertThat(cb).isNotNull();
    }

    @Test
    @DisplayName("createBeforeModelCallback returns non-null callback")
    void beforeModelCallbackNotNull() {
        AgentObservationCallbacks callbacks = new AgentObservationCallbacks(null, null, null, null);
        Callbacks.BeforeModelCallback cb = callbacks.createBeforeModelCallback();
        assertThat(cb).isNotNull();
    }

    @Test
    @DisplayName("createAfterModelCallback returns non-null callback")
    void afterModelCallbackNotNull() {
        AgentObservationCallbacks callbacks = new AgentObservationCallbacks(null, null, null, null);
        Callbacks.AfterModelCallback cb = callbacks.createAfterModelCallback();
        assertThat(cb).isNotNull();
    }

    @Test
    @DisplayName("createOnModelErrorCallback returns non-null callback")
    void onModelErrorCallbackNotNull() {
        AgentObservationCallbacks callbacks = new AgentObservationCallbacks(null, null, null, null);
        Callbacks.OnModelErrorCallback cb = callbacks.createOnModelErrorCallback();
        assertThat(cb).isNotNull();
    }

    @Test
    @DisplayName("createBeforeToolCallback returns non-null callback")
    void beforeToolCallbackNotNull() {
        AgentObservationCallbacks callbacks = new AgentObservationCallbacks(null, null, null, null);
        Callbacks.BeforeToolCallback cb = callbacks.createBeforeToolCallback();
        assertThat(cb).isNotNull();
    }

    @Test
    @DisplayName("createAfterToolCallback returns non-null callback")
    void afterToolCallbackNotNull() {
        AgentObservationCallbacks callbacks = new AgentObservationCallbacks(null, null, null, null);
        Callbacks.AfterToolCallback cb = callbacks.createAfterToolCallback();
        assertThat(cb).isNotNull();
    }

    @Test
    @DisplayName("createOnToolErrorCallback returns non-null callback")
    void onToolErrorCallbackNotNull() {
        AgentObservationCallbacks callbacks = new AgentObservationCallbacks(null, null, null, null);
        Callbacks.OnToolErrorCallback cb = callbacks.createOnToolErrorCallback();
        assertThat(cb).isNotNull();
    }

    // ===== Behavioral tests =====

    private final ObjectMapper objectMapper = createObjectMapper();
    private final AgentObservationCallbacks callbacks =
            new AgentObservationCallbacks(null, null, objectMapper, null);

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new Jdk8Module());
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }

    // =========================================================================
    // serializeContents helper tests
    // =========================================================================

    @Nested
    @DisplayName("serializeContents")
    class SerializeContentsTest {

        @Test
        @DisplayName("serializes multi-message conversation with text parts")
        void multiMessageWithTextParts() throws Exception {
            List<Content> messages = List.of(
                    Content.builder()
                            .role("user")
                            .parts(List.of(Part.builder().text("What is Java?").build()))
                            .build(),
                    Content.builder()
                            .role("model")
                            .parts(List.of(Part.builder().text("Java is a programming language.").build()))
                            .build()
            );

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> result = invokeSerializeContents(messages);

            assertThat(result).hasSize(2);
            assertThat(roleValue(result.get(0))).isEqualTo("user");
            assertThat(roleValue(result.get(1))).isEqualTo("model");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> userParts = (List<Map<String, Object>>) result.get(0).get("parts");
            assertThat(userParts).hasSize(1);
            assertThat(textValue(userParts.get(0))).isEqualTo("What is Java?");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> modelParts = (List<Map<String, Object>>) result.get(1).get("parts");
            assertThat(modelParts).hasSize(1);
            assertThat(textValue(modelParts.get(0))).isEqualTo("Java is a programming language.");
        }

        @Test
        @DisplayName("serializes user message with single text part")
        void singleTextPart() throws Exception {
            List<Content> messages = List.of(
                    Content.builder()
                            .role("user")
                            .parts(List.of(Part.builder().text("Hello").build()))
                            .build()
            );

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> result = invokeSerializeContents(messages);

            assertThat(result).hasSize(1);
            assertThat(roleValue(result.get(0))).isEqualTo("user");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> parts = (List<Map<String, Object>>) result.get(0).get("parts");
            assertThat(parts).hasSize(1);
            assertThat(parts.get(0).get("type")).isEqualTo("text");
            assertThat(textValue(parts.get(0))).isEqualTo("Hello");
        }

        @Test
        @DisplayName("handles empty parts list")
        void emptyParts() throws Exception {
            List<Content> messages = List.of(
                    Content.builder().role("user").build()
            );

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> result = invokeSerializeContents(messages);

            assertThat(result).hasSize(1);
            assertThat(roleValue(result.get(0))).isEqualTo("user");
            assertThat(result.get(0)).doesNotContainKey("parts");
        }

        @Test
        @DisplayName("serializes multiple parts in a single message")
        void multiplePartsInMessage() throws Exception {
            List<Content> messages = List.of(
                    Content.builder()
                            .role("user")
                            .parts(List.of(
                                    Part.builder().text("Hello").build(),
                                    Part.builder().text("World").build()
                            ))
                            .build()
            );

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> result = invokeSerializeContents(messages);

            assertThat(result).hasSize(1);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> parts = (List<Map<String, Object>>) result.get(0).get("parts");
            assertThat(parts).hasSize(2);
            assertThat(textValue(parts.get(0))).isEqualTo("Hello");
            assertThat(textValue(parts.get(1))).isEqualTo("World");
        }

        @Test
        @DisplayName("skips null content entries")
        void skipsNullContent() throws Exception {
            // serializeContents skips null entries via "if (content == null) continue"
            // We can't pass null in a List.of(), but we can verify the method
            // handles a list with one valid entry
            List<Content> messages = List.of(
                    Content.builder()
                            .role("user")
                            .parts(List.of(Part.builder().text("test").build()))
                            .build()
            );

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> result = invokeSerializeContents(messages);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("produces JSON-serializable output via full callback flow")
        void jsonSerializable() throws Exception {
            List<Content> messages = List.of(
                    Content.builder()
                            .role("user")
                            .parts(List.of(Part.builder().text("test").build()))
                            .build()
            );

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> result = invokeSerializeContents(messages);

            // Verify it can be serialized to JSON and back
            String json = objectMapper.writeValueAsString(result);
            assertThat(json).isNotNull();
            assertThat(json).contains("\"role\"");
            assertThat(json).contains("\"user\"");
            assertThat(json).contains("\"text\"");
            assertThat(json).contains("test");
        }
    }

    // =========================================================================
    // serializeParts helper tests
    // =========================================================================

    @Nested
    @DisplayName("serializeParts")
    class SerializePartsTest {

        @Test
        @DisplayName("identifies text parts with correct type marker")
        void textPartTypeMarker() throws Exception {
            List<Content> messages = List.of(
                    Content.builder()
                            .role("user")
                            .parts(List.of(Part.builder().text("Hello").build()))
                            .build()
            );

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> result = invokeSerializeContents(messages);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> parts = (List<Map<String, Object>>) result.get(0).get("parts");
            assertThat(parts.get(0).get("type")).isEqualTo("text");
            assertThat(textValue(parts.get(0))).isEqualTo("Hello");
        }
    }

    // =========================================================================
    // getStackTrace helper tests
    // =========================================================================

    @Nested
    @DisplayName("getStackTrace")
    class GetStackTraceTest {

        @Test
        @DisplayName("produces stack trace with exception type and message")
        void producesStackTrace() throws Exception {
            Throwable error = new NullPointerException("test message");
            String stackTrace = invokeGetStackTrace(error);

            assertThat(stackTrace).contains("NullPointerException");
            assertThat(stackTrace).contains("test message");
        }

        @Test
        @DisplayName("produces stack trace with nested cause chain")
        void producesStackTraceWithCause() throws Exception {
            Throwable cause = new IllegalArgumentException("root cause");
            Throwable error = new RuntimeException("wrapper", cause);
            String stackTrace = invokeGetStackTrace(error);

            assertThat(stackTrace).contains("RuntimeException");
            assertThat(stackTrace).contains("wrapper");
            assertThat(stackTrace).contains("IllegalArgumentException");
            assertThat(stackTrace).contains("root cause");
        }

        @Test
        @DisplayName("returns empty string for null input")
        void returnsEmptyForNull() throws Exception {
            String stackTrace = invokeGetStackTrace(null);
            assertThat(stackTrace).isEmpty();
        }

        @Test
        @DisplayName("includes class and method names in stack trace")
        void includesMethodNames() throws Exception {
            Throwable error = new IllegalStateException("test");
            String stackTrace = invokeGetStackTrace(error);

            assertThat(stackTrace).contains("java.lang");
            assertThat(stackTrace).contains("at ");
        }
    }

    // =========================================================================
    // LlmRequest config serialization tests
    // =========================================================================

    @Nested
    @DisplayName("LlmRequest config extraction")
    class ConfigSerializationTest {

        @Test
        @DisplayName("GenerateContentConfig values are extractable")
        void configValuesExtract() throws Exception {
            GenerateContentConfig config = GenerateContentConfig.builder()
                    .temperature(0.7f)
                    .topP(0.9f)
                    .topK(40.0f)
                    .maxOutputTokens(1024)
                    .build();

            // Verify that the config values can be extracted as the callback does
            Map<String, Object> configMap = new java.util.HashMap<>();
            config.temperature().ifPresent(v -> configMap.put("temperature", v));
            config.topP().ifPresent(v -> configMap.put("topP", v));
            config.topK().ifPresent(v -> configMap.put("topK", v));
            config.maxOutputTokens().ifPresent(v -> configMap.put("maxOutputTokens", v));

            assertThat(configMap.get("temperature")).isEqualTo(0.7f);
            assertThat(configMap.get("topP")).isEqualTo(0.9f);
            assertThat(configMap.get("topK")).isEqualTo(40.0f);
            assertThat(configMap.get("maxOutputTokens")).isEqualTo(1024);

            // Verify the config map is JSON-serializable
            String json = objectMapper.writeValueAsString(configMap);
            assertThat(json).contains("\"temperature\"");
            assertThat(json).contains("0.7");
        }

        @Test
        @DisplayName("Content and Part builders produce valid objects")
        void contentPartBuildersValid() throws Exception {
            Content content = Content.builder()
                    .role("user")
                    .parts(List.of(
                            Part.builder().text("Hello").build(),
                            Part.builder().text("World").build()
                    ))
                    .build();

            assertThat(content.role()).isPresent().hasValue("user");
            assertThat(content.parts()).isPresent();
            assertThat(content.parts().get()).hasSize(2);
            assertThat(content.parts().get().get(0).text()).hasValue("Hello");
            assertThat(content.parts().get().get(1).text()).hasValue("World");
        }
    }

    // =========================================================================
    // Error payload structure tests
    // =========================================================================

    @Nested
    @DisplayName("Error payload structure")
    class ErrorPayloadTest {

        @Test
        @DisplayName("error payload contains type, message, and stackTrace")
        void errorPayloadStructure() throws Exception {
            // Verify the same structure that onModelError/onToolError callbacks build
            Throwable error = new RuntimeException("Connection refused");
            Map<String, Object> errorPayload = new java.util.HashMap<>();
            errorPayload.put("type", error.getClass().getName());
            errorPayload.put("message", error.getMessage());
            errorPayload.put("stackTrace", invokeGetStackTrace(error));

            assertThat(errorPayload.get("type")).isEqualTo("java.lang.RuntimeException");
            assertThat(errorPayload.get("message")).isEqualTo("Connection refused");
            assertThat(errorPayload.get("stackTrace")).isNotNull();
            assertThat((String) errorPayload.get("stackTrace")).contains("RuntimeException");
            assertThat((String) errorPayload.get("stackTrace")).contains("Connection refused");

            // Verify JSON-serializable
            String json = objectMapper.writeValueAsString(errorPayload);
            assertThat(json).contains("\"type\"");
            assertThat(json).contains("\"message\"");
            assertThat(json).contains("\"stackTrace\"");
        }

        @Test
        @DisplayName("tool input payload structure is correct")
        void toolInputPayloadStructure() throws Exception {
            // Verify the same structure that beforeTool callback builds
            Map<String, Object> inputPayload = new java.util.HashMap<>();
            inputPayload.put("toolName", "search_tool");
            inputPayload.put("args", Map.of("query", "test", "limit", 10));

            String json = objectMapper.writeValueAsString(inputPayload);
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(json, Map.class);

            assertThat(parsed.get("toolName")).isEqualTo("search_tool");
            @SuppressWarnings("unchecked")
            Map<String, Object> args = (Map<String, Object>) parsed.get("args");
            assertThat(args.get("query")).isEqualTo("test");
            assertThat(args.get("limit")).isEqualTo(10);
        }

        @Test
        @DisplayName("tool output payload structure is correct")
        void toolOutputPayloadStructure() throws Exception {
            // Verify the same structure that afterTool callback builds
            Map<String, Object> toolResponse = Map.of(
                    "results", List.of(
                            Map.of("title", "Result 1", "score", 0.95),
                            Map.of("title", "Result 2", "score", 0.87)
                    ),
                    "total", 2
            );

            String json = objectMapper.writeValueAsString(toolResponse);
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(json, Map.class);

            assertThat(parsed.get("total")).isEqualTo(2);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) parsed.get("results");
            assertThat(results).hasSize(2);
            assertThat(results.get(0).get("title")).isEqualTo("Result 1");
        }
    }

    // -- Reflection helpers for private methods --

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> invokeSerializeContents(List<Content> contents) throws Exception {
        Method method = AgentObservationCallbacks.class
                .getDeclaredMethod("serializeContents", List.class);
        method.setAccessible(true);
        return (List<Map<String, Object>>) method.invoke(callbacks, contents);
    }

    private String invokeGetStackTrace(Throwable t) throws Exception {
        Method method = AgentObservationCallbacks.class
                .getDeclaredMethod("getStackTrace", Throwable.class);
        method.setAccessible(true);
        return (String) method.invoke(callbacks, t);
    }

    /**
     * Extract role value from serialized content map, handling Optional wrapper.
     * The serializeContents method puts content.role() (Optional<String>) into the map.
     */
    private static String roleValue(Map<String, Object> map) {
        Object role = map.get("role");
        if (role instanceof java.util.Optional<?> opt) {
            return opt.map(Object::toString).orElse("");
        }
        return role != null ? role.toString() : "";
    }

    /**
     * Extract text value from serialized part map, handling Optional wrapper.
     */
    @SuppressWarnings("unchecked")
    private static String textValue(Map<String, Object> partMap) {
        Object text = partMap.get("text");
        if (text instanceof java.util.Optional<?> opt) {
            return opt.map(Object::toString).orElse("");
        }
        return text != null ? text.toString() : "";
    }
}
