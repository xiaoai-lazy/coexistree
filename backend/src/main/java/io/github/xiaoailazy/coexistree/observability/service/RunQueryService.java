package io.github.xiaoailazy.coexistree.observability.service;

import com.fasterxml.jackson.core.type.TypeReference;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class RunQueryService {

    private static final Logger log = LoggerFactory.getLogger(RunQueryService.class);

    private final ConversationRunRepository runRepository;
    private final ObservationSpanRepository spanRepository;
    private final SpanEventRepository spanEventRepository;
    private final ObjectMapper objectMapper;

    public RunQueryService(ConversationRunRepository runRepository,
                           ObservationSpanRepository spanRepository,
                           SpanEventRepository spanEventRepository,
                           ObjectMapper objectMapper) {
        this.runRepository = runRepository;
        this.spanRepository = spanRepository;
        this.spanEventRepository = spanEventRepository;
        this.objectMapper = objectMapper;
    }

    public List<RunListItemDto> listRunsByConversation(String conversationId, int page, int size) {
        List<ConversationRunEntity> runs = runRepository
                .findByConversationIdOrderByStartedAtDesc(conversationId);

        int start = page * size;
        if (start >= runs.size()) return List.of();
        int end = Math.min(start + size, runs.size());

        List<RunListItemDto> result = new ArrayList<>();
        for (int i = start; i < end; i++) {
            ConversationRunEntity run = runs.get(i);
            int spanCount = spanRepository.findByRunIdOrderByStartedAtAsc(run.getRunId()).size();
            result.add(toListItemDto(run, spanCount));
        }
        return result;
    }

    public RunSummaryDto getRunSummary(String runId) {
        ConversationRunEntity run = runRepository.findByRunId(runId)
                .orElseThrow(() -> new IllegalArgumentException("Run not found: " + runId));

        List<ObservationSpanEntity> spans = spanRepository.findByRunIdOrderByStartedAtAsc(runId);

        int modelCount = 0, toolCount = 0, agentCount = 0;
        for (ObservationSpanEntity span : spans) {
            switch (span.getSpanType()) {
                case "model" -> modelCount++;
                case "tool" -> toolCount++;
                case "agent" -> agentCount++;
            }
        }

        log.info("[query] getRunSummary, runId={}, status={}, totalSpans={}, models={}, tools={}, agents={}",
                runId, run.getStatus(), spans.size(), modelCount, toolCount, agentCount);

        return new RunSummaryDto(
                run.getRunId(),
                run.getTraceId(),
                run.getConversationId(),
                run.getStatus(),
                run.getRootAgentName(),
                run.getRequestText(),
                run.getFinalAnswer(),
                run.getDurationMs(),
                spans.size(),
                modelCount,
                toolCount,
                agentCount,
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getCorrelationId()
        );
    }

    public SpanTreeNodeDto getRunTree(String runId) {
        List<ObservationSpanEntity> spans = spanRepository.findByRunIdOrderByStartedAtAsc(runId);
        if (spans.isEmpty()) {
            log.warn("[query] getRunTree: no spans found for runId={}", runId);
            return null;
        }

        // Build tree from flat list
        Map<String, SpanTreeNodeDto> nodeMap = new LinkedHashMap<>();
        String rootId = null;

        for (ObservationSpanEntity span : spans) {
            SpanTreeNodeDto node = toTreeNode(span, new ArrayList<>());
            nodeMap.put(span.getSpanId(), node);
            if (rootId == null || span.getStartedAt().isBefore(
                    nodeMap.get(rootId).startedAt())) {
                rootId = span.getSpanId();
            }
        }

        // Attach children to parents
        for (ObservationSpanEntity span : spans) {
            String parentSpanId = span.getParentSpanId();
            if (parentSpanId != null && nodeMap.containsKey(parentSpanId)) {
                nodeMap.get(parentSpanId).children().add(nodeMap.get(span.getSpanId()));
            }
        }

        log.info("[query] getRunTree, runId={}, totalSpans={}, rootSpanId={}",
                runId, spans.size(), rootId);

        return nodeMap.get(rootId);
    }

    public List<RunTimelineItemDto> getRunTimeline(String runId) {
        List<SpanEventEntity> events = spanEventRepository.findByRunIdOrderBySequenceNoAsc(runId);
        log.info("[query] getRunTimeline, runId={}, totalEvents={}", runId, events.size());

        List<RunTimelineItemDto> timeline = new ArrayList<>();

        for (SpanEventEntity event : events) {
            timeline.add(new RunTimelineItemDto(
                    event.getEventType(),
                    event.getSpanId(),
                    null, // span type can be looked up but is optional for timeline
                    event.getEventName(),
                    truncate(event.getPayload(), 200),
                    event.getOccurredAt(),
                    event.getSequenceNo()
            ));
        }

        return timeline;
    }

    public SpanDetailDto getRunSpanDetail(String runId, String spanId) {
        ObservationSpanEntity span = spanRepository.findBySpanId(spanId)
                .orElseThrow(() -> new IllegalArgumentException("Span not found: " + spanId));

        if (!span.getRunId().equals(runId)) {
            throw new IllegalArgumentException("Span " + spanId + " does not belong to run " + runId);
        }

        log.info("[query] getRunSpanDetail, runId={}, spanId={}, type={}, status={}",
                runId, spanId, span.getSpanType(), span.getStatus());

        return new SpanDetailDto(
                span.getSpanId(),
                span.getParentSpanId(),
                span.getRunId(),
                span.getSpanType(),
                span.getSpanName(),
                span.getAgentName(),
                span.getToolName(),
                span.getModelName(),
                span.getModelVersion(),
                span.getStatus(),
                parseJson(span.getInputPayload()),
                parseJson(span.getOutputPayload()),
                parseJson(span.getAttributes()),
                parseJson(span.getErrorPayload()),
                span.getDurationMs(),
                span.getDepth(),
                span.getStartedAt(),
                span.getFinishedAt()
        );
    }

    public List<SpanEventEntity> listRunSpanEvents(String runId, String spanId) {
        return spanEventRepository.findBySpanIdOrderBySequenceNoAsc(spanId);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private RunListItemDto toListItemDto(ConversationRunEntity run, int spanCount) {
        return new RunListItemDto(
                run.getRunId(),
                run.getStatus(),
                truncate(run.getRequestText(), 100),
                truncate(run.getFinalAnswer(), 100),
                run.getDurationMs(),
                run.getStartedAt(),
                spanCount
        );
    }

    private SpanTreeNodeDto toTreeNode(ObservationSpanEntity span, List<SpanTreeNodeDto> children) {
        return new SpanTreeNodeDto(
                span.getSpanId(),
                span.getParentSpanId(),
                span.getSpanType(),
                span.getSpanName(),
                span.getAgentName(),
                span.getToolName(),
                span.getModelName(),
                span.getModelVersion(),
                span.getStatus(),
                parseJson(span.getInputPayload()),
                parseJson(span.getOutputPayload()),
                parseJson(span.getAttributes()),
                span.getDurationMs(),
                span.getDepth(),
                children,
                span.getStartedAt(),
                span.getFinishedAt()
        );
    }

    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[query] parseJson failed, returning raw: {}", json.length() > 100 ? json.substring(0, 100) + "..." : json);
            return Map.of("raw", json);
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
