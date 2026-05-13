package io.github.xiaoailazy.coexistree.observability.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xiaoailazy.coexistree.observability.dto.RunListItemDto;
import io.github.xiaoailazy.coexistree.observability.dto.RunSummaryDto;
import io.github.xiaoailazy.coexistree.observability.dto.RunTimelineItemDto;
import io.github.xiaoailazy.coexistree.observability.dto.SpanDetailDto;
import io.github.xiaoailazy.coexistree.observability.dto.SpanTreeNodeDto;
import io.github.xiaoailazy.coexistree.observability.entity.ConversationRunEntity;
import io.github.xiaoailazy.coexistree.observability.entity.ObservationSpanEntity;
import io.github.xiaoailazy.coexistree.observability.entity.SpanEventEntity;
import io.github.xiaoailazy.coexistree.observability.repository.ConversationRunRepository;
import io.github.xiaoailazy.coexistree.observability.repository.ObservationSpanRepository;
import io.github.xiaoailazy.coexistree.observability.repository.SpanEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RunQueryServiceTest {

    @Mock private ConversationRunRepository runRepository;
    @Mock private ObservationSpanRepository spanRepository;
    @Mock private SpanEventRepository spanEventRepository;

    private RunQueryService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String RUN_ID = "run-test-001";
    private static final String CONV_ID = "conv-001";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 4, 23, 10, 0);

    @BeforeEach
    void setUp() {
        service = new RunQueryService(runRepository, spanRepository, spanEventRepository, objectMapper);
    }

    // =========================================================================
    // listRunsByConversation
    // =========================================================================

    @Nested
    @DisplayName("listRunsByConversation")
    class ListRunsByConversation {

        @Test
        @DisplayName("returns paginated runs with span counts")
        void paginatedWithSpans() {
            ConversationRunEntity run = buildRun();
            when(runRepository.findByConversationIdOrderByStartedAtDesc(CONV_ID))
                    .thenReturn(List.of(run));
            when(spanRepository.findByRunIdOrderByStartedAtAsc(RUN_ID))
                    .thenReturn(List.of(buildSpan("span-1", null, "run", 0, 0),
                                        buildSpan("span-2", "span-1", "model", 2, 1)));

            List<RunListItemDto> result = service.listRunsByConversation(CONV_ID, 0, 10);

            assertThat(result).hasSize(1);
            RunListItemDto dto = result.get(0);
            assertThat(dto.runId()).isEqualTo(RUN_ID);
            assertThat(dto.status()).isEqualTo("success");
            assertThat(dto.spanCount()).isEqualTo(2);
            assertThat(dto.requestPreview()).isEqualTo("hello");
            assertThat(dto.durationMs()).isEqualTo(5000L);
        }

        @Test
        @DisplayName("returns empty when page exceeds data")
        void emptyOnOversizedPage() {
            when(runRepository.findByConversationIdOrderByStartedAtDesc(CONV_ID))
                    .thenReturn(List.of(buildRun()));

            List<RunListItemDto> result = service.listRunsByConversation(CONV_ID, 5, 10);

            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    // getRunSummary
    // =========================================================================

    @Nested
    @DisplayName("getRunSummary")
    class GetRunSummary {

        @Test
        @DisplayName("aggregates span type counts correctly")
        void aggregatesCounts() {
            when(runRepository.findByRunId(RUN_ID))
                    .thenReturn(Optional.of(buildRun()));
            when(spanRepository.findByRunIdOrderByStartedAtAsc(RUN_ID))
                    .thenReturn(List.of(
                            buildSpan("span-root", null, "run", 0, 0),
                            buildSpan("span-agent1", "span-root", "agent", 1, 1),
                            buildSpan("span-agent2", "span-agent1", "agent", 2, 2),
                            buildSpan("span-model", "span-agent2", "model", 3, 3),
                            buildSpan("span-tool", "span-agent2", "tool", 3, 4)
                    ));

            RunSummaryDto dto = service.getRunSummary(RUN_ID);

            assertThat(dto.runId()).isEqualTo(RUN_ID);
            assertThat(dto.totalSpanCount()).isEqualTo(5);
            assertThat(dto.agentSpanCount()).isEqualTo(2);
            assertThat(dto.modelCallCount()).isEqualTo(1);
            assertThat(dto.toolCallCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("throws when run not found")
        void runNotFound() {
            when(runRepository.findByRunId(RUN_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getRunSummary(RUN_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Run not found");
        }
    }

    // =========================================================================
    // getRunTree
    // =========================================================================

    @Nested
    @DisplayName("getRunTree")
    class GetRunTree {

        @Test
        @DisplayName("builds three-level nested tree correctly")
        void threeLevelTree() {
            List<ObservationSpanEntity> spans = new ArrayList<>();
            spans.add(buildSpan("span-root", null, "run", 0, 0));
            spans.add(buildSpan("span-agent", "span-root", "agent", 1, 1));
            spans.add(buildSpan("span-model", "span-agent", "model", 2, 2));
            spans.add(buildSpan("span-tool", "span-agent", "tool", 2, 3));

            when(spanRepository.findByRunIdOrderByStartedAtAsc(RUN_ID)).thenReturn(spans);

            SpanTreeNodeDto tree = service.getRunTree(RUN_ID);

            assertThat(tree).isNotNull();
            assertThat(tree.spanId()).isEqualTo("span-root");
            assertThat(tree.children()).hasSize(1);

            SpanTreeNodeDto agentNode = tree.children().get(0);
            assertThat(agentNode.spanId()).isEqualTo("span-agent");
            assertThat(agentNode.children()).hasSize(2);

            List<String> childIds = agentNode.children().stream()
                    .map(SpanTreeNodeDto::spanId)
                    .toList();
            assertThat(childIds).containsExactlyInAnyOrder("span-model", "span-tool");
        }

        @Test
        @DisplayName("returns null for empty run")
        void emptyRun() {
            when(spanRepository.findByRunIdOrderByStartedAtAsc(RUN_ID)).thenReturn(List.of());

            SpanTreeNodeDto tree = service.getRunTree(RUN_ID);

            assertThat(tree).isNull();
        }
    }

    // =========================================================================
    // getRunTimeline
    // =========================================================================

    @Nested
    @DisplayName("getRunTimeline")
    class GetRunTimeline {

        @Test
        @DisplayName("converts span_events to timeline items in sequence order")
        void fromEvents() {
            List<SpanEventEntity> events = new ArrayList<>();
            events.add(buildEvent(1, "stream_started"));
            events.add(buildEvent(2, "tool_decision"));
            events.add(buildEvent(3, "stream_finished"));

            when(spanEventRepository.findByRunIdOrderBySequenceNoAsc(RUN_ID)).thenReturn(events);

            List<RunTimelineItemDto> timeline = service.getRunTimeline(RUN_ID);

            assertThat(timeline).hasSize(3);
            assertThat(timeline.get(0).eventType()).isEqualTo("stream_started");
            assertThat(timeline.get(0).sequenceNo()).isEqualTo(1L);
            assertThat(timeline.get(2).eventType()).isEqualTo("stream_finished");
        }

        @Test
        @DisplayName("returns empty timeline when no events")
        void emptyEvents() {
            when(spanEventRepository.findByRunIdOrderBySequenceNoAsc(RUN_ID)).thenReturn(List.of());

            List<RunTimelineItemDto> timeline = service.getRunTimeline(RUN_ID);

            assertThat(timeline).isEmpty();
        }
    }

    // =========================================================================
    // getRunSpanDetail
    // =========================================================================

    @Nested
    @DisplayName("getRunSpanDetail")
    class GetRunSpanDetail {

        @Test
        @DisplayName("returns span detail with parsed JSON payloads")
        void withParsedPayloads() {
            ObservationSpanEntity span = buildSpan("span-model", "span-root", "model", 2, 0);
            span.setOutputPayload("{\"content\":\"hello\",\"contentPreview\":\"hello\"}");
            span.setAttributes("{\"tokenInput\":10,\"tokenOutput\":5}");
            when(spanRepository.findBySpanId("span-model")).thenReturn(Optional.of(span));

            SpanDetailDto dto = service.getRunSpanDetail(RUN_ID, "span-model");

            assertThat(dto.spanId()).isEqualTo("span-model");
            assertThat(dto.spanType()).isEqualTo("model");
            assertThat(dto.modelName()).isEqualTo("doubao-seed-2-0-mini");
            assertThat(dto.outputPayload()).isNotNull();
            assertThat(dto.outputPayload().get("content")).isEqualTo("hello");
            assertThat(dto.attributes().get("tokenInput")).isEqualTo(10);
        }

        @Test
        @DisplayName("throws when span does not belong to the given run")
        void spanNotInRun() {
            ObservationSpanEntity span = buildSpan("span-x", null, "agent", 0, 0);
            span.setRunId("run-other");
            when(spanRepository.findBySpanId("span-x")).thenReturn(Optional.of(span));

            assertThatThrownBy(() -> service.getRunSpanDetail(RUN_ID, "span-x"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("does not belong to run");
        }

        @Test
        @DisplayName("throws when span not found")
        void spanNotFound() {
            when(spanRepository.findBySpanId("span-x")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getRunSpanDetail(RUN_ID, "span-x"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Span not found");
        }
    }

    // =========================================================================
    // listRunSpanEvents
    // =========================================================================

    @Nested
    @DisplayName("listRunSpanEvents")
    class ListRunSpanEvents {

        @Test
        @DisplayName("returns events for a span in sequence order")
        void eventsForSpan() {
            List<SpanEventEntity> events = new ArrayList<>();
            SpanEventEntity e1 = new SpanEventEntity();
            e1.setId(1L); e1.setSpanId("span-x"); e1.setEventType("stream_started"); e1.setSequenceNo(1L);
            SpanEventEntity e2 = new SpanEventEntity();
            e2.setId(2L); e2.setSpanId("span-x"); e2.setEventType("stream_finished"); e2.setSequenceNo(2L);
            events.add(e1);
            events.add(e2);

            when(spanEventRepository.findBySpanIdOrderBySequenceNoAsc("span-x")).thenReturn(events);

            List<SpanEventEntity> result = service.listRunSpanEvents(RUN_ID, "span-x");

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getEventType()).isEqualTo("stream_started");
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private ConversationRunEntity buildRun() {
        var r = new ConversationRunEntity();
        r.setId(1L);
        r.setRunId(RUN_ID);
        r.setTraceId(RUN_ID);
        r.setConversationId(CONV_ID);
        r.setRootAgentName("root-agent");
        r.setRequestText("hello");
        r.setFinalAnswer("world");
        r.setStatus("success");
        r.setStartedAt(NOW);
        r.setFinishedAt(NOW.plusSeconds(5));
        r.setDurationMs(5000L);
        r.setCorrelationId("corr-001");
        r.setCreatedAt(NOW);
        return r;
    }

    private ObservationSpanEntity buildSpan(String spanId, String parent, String type, int depth, long startOffsetSec) {
        var s = new ObservationSpanEntity();
        s.setId(100L);
        s.setSpanId(spanId);
        s.setParentSpanId(parent);
        s.setRunId(RUN_ID);
        s.setConversationId(CONV_ID);
        s.setSpanType(type);
        s.setSpanName(type + ":" + spanId);
        s.setAgentName("root-agent");
        s.setStatus("success");
        s.setStartedAt(NOW.plusSeconds(startOffsetSec));
        s.setFinishedAt(NOW.plusSeconds(startOffsetSec + 2));
        s.setDurationMs(2000L);
        s.setDepth(depth);
        s.setCreatedAt(NOW);
        if ("model".equals(type)) {
            s.setModelName("doubao-seed-2-0-mini");
        }
        if ("tool".equals(type)) {
            s.setToolName("search");
        }
        return s;
    }

    private SpanEventEntity buildEvent(int seq, String type) {
        var e = new SpanEventEntity();
        e.setId((long) seq);
        e.setRunId(RUN_ID);
        e.setSpanId("span-x");
        e.setConversationId(CONV_ID);
        e.setEventType(type);
        e.setEventName(type);
        e.setPayload("{\"key\":\"value\"}");
        e.setSequenceNo((long) seq);
        e.setOccurredAt(NOW.plusSeconds(seq));
        e.setCreatedAt(NOW);
        return e;
    }
}
