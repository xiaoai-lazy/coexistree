package io.github.xiaoailazy.coexistree.observability.controller;

import io.github.xiaoailazy.coexistree.observability.dto.RunListItemDto;
import io.github.xiaoailazy.coexistree.observability.dto.RunSummaryDto;
import io.github.xiaoailazy.coexistree.observability.dto.RunTimelineItemDto;
import io.github.xiaoailazy.coexistree.observability.dto.SpanDetailDto;
import io.github.xiaoailazy.coexistree.observability.dto.SpanTreeNodeDto;
import io.github.xiaoailazy.coexistree.observability.entity.SpanEventEntity;
import io.github.xiaoailazy.coexistree.observability.service.RunQueryService;
import io.github.xiaoailazy.coexistree.shared.api.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/observability")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Slf4j
public class RunObservabilityController {

    private final RunQueryService runQueryService;

    public RunObservabilityController(RunQueryService runQueryService) {
        this.runQueryService = runQueryService;
    }

    /**
     * List all runs for a conversation.
     */
    @GetMapping("/conversations/{conversationId}/runs")
    public ApiResponse<List<RunListItemDto>> listRuns(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("[obs-api] listRuns, conv={}, page={}, size={}", conversationId, page, size);
        List<RunListItemDto> runs = runQueryService.listRunsByConversation(conversationId, page, size);
        return ApiResponse.success(runs);
    }

    /**
     * Get summary of a single run.
     */
    @GetMapping("/runs/{runId}/summary")
    public ApiResponse<RunSummaryDto> getRunSummary(@PathVariable String runId) {
        log.info("[obs-api] getRunSummary, runId={}", runId);
        return ApiResponse.success(runQueryService.getRunSummary(runId));
    }

    /**
     * Get span tree for a run.
     */
    @GetMapping("/runs/{runId}/tree")
    public ApiResponse<SpanTreeNodeDto> getRunTree(@PathVariable String runId) {
        log.info("[obs-api] getRunTree, runId={}", runId);
        return ApiResponse.success(runQueryService.getRunTree(runId));
    }

    /**
     * Get timeline of events for a run.
     */
    @GetMapping("/runs/{runId}/timeline")
    public ApiResponse<List<RunTimelineItemDto>> getRunTimeline(@PathVariable String runId) {
        log.info("[obs-api] getRunTimeline, runId={}", runId);
        return ApiResponse.success(runQueryService.getRunTimeline(runId));
    }

    /**
     * Get detail of a specific span within a run.
     */
    @GetMapping("/runs/{runId}/spans/{spanId}")
    public ApiResponse<SpanDetailDto> getSpanDetail(
            @PathVariable String runId,
            @PathVariable String spanId) {
        log.info("[obs-api] getSpanDetail, runId={}, spanId={}", runId, spanId);
        return ApiResponse.success(runQueryService.getRunSpanDetail(runId, spanId));
    }

    /**
     * Get events for a specific span.
     */
    @GetMapping("/runs/{runId}/spans/{spanId}/events")
    public ApiResponse<List<SpanEventEntity>> getSpanEvents(
            @PathVariable String runId,
            @PathVariable String spanId) {
        log.info("[obs-api] getSpanEvents, runId={}, spanId={}", runId, spanId);
        return ApiResponse.success(runQueryService.listRunSpanEvents(runId, spanId));
    }
}
