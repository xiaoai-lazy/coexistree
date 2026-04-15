package io.github.xiaoailazy.coexistree.review.service.detector.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xiaoailazy.coexistree.review.enums.EvaluationCategory;
import io.github.xiaoailazy.coexistree.review.enums.RiskLevel;
import io.github.xiaoailazy.coexistree.review.model.EvaluationReport;
import io.github.xiaoailazy.coexistree.review.service.detector.ConflictDetector;
import io.github.xiaoailazy.coexistree.review.service.detector.DetectionResult;
import io.github.xiaoailazy.coexistree.knowledge.model.SystemKnowledgeTree;
import io.github.xiaoailazy.coexistree.indexer.llm.LlmClient;
import io.github.xiaoailazy.coexistree.shared.util.LlmCallContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 功能冲突检测器实现
 */
@Slf4j
@Component
public class ConflictDetectorImpl implements ConflictDetector {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public ConflictDetectorImpl(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public DetectionResult detect(String requirementContent, SystemKnowledgeTree systemTree, String previousResponseId) {
        String prompt = buildPrompt(requirementContent, systemTree);

        LlmCallContext.set("CONFLICT_DETECTION", null, null, null);
        try {
            LlmClient.LlmResponse response = llmClient.chat(prompt, null, 0.3, previousResponseId);
            EvaluationReport report = parseResult(response.content());
            return DetectionResult.of(report, response.responseId());
        } catch (Exception e) {
            log.error("功能冲突检测失败", e);
            return DetectionResult.of(createErrorReport(), null);
        } finally {
            LlmCallContext.clear();
        }
    }

    private String buildPrompt(String requirementContent, SystemKnowledgeTree systemTree) {
        String treeSummary = summarizeTree(systemTree);

        return """
                作为需求分析专家，请对比需求中的功能点与系统知识树中的现有功能，识别潜在冲突。

                ## 需求文档
                ```
                %s
                ```

                ## 现有功能树摘要
                ```
                %s
                ```

                ## 检测任务
                请识别以下类型的冲突：
                1. **功能重复** - 需求功能已存在
                2. **功能矛盾** - 需求功能与现有功能逻辑冲突
                3. **功能覆盖** - 需求功能是现有功能的子集

                ## 输出格式（JSON）
                {
                  "riskLevel": "HIGH|MEDIUM|LOW|NONE",
                  "summary": "总体评估结论",
                  "items": [
                    {
                      "name": "冲突名称",
                      "description": "详细描述",
                      "severity": "high|medium|low",
                      "suggestion": "解决建议"
                    }
                  ]
                }

                风险等级定义：
                - HIGH: 存在严重功能重复或矛盾，必须解决
                - MEDIUM: 存在潜在冲突，建议关注
                - LOW: 轻微重叠或边界模糊
                - NONE: 无冲突

                请只输出 JSON，不要其他内容。
                """.formatted(requirementContent, treeSummary);
    }

    private String summarizeTree(SystemKnowledgeTree tree) {
        // 简化知识树为文本摘要
        StringBuilder sb = new StringBuilder();
        if (tree != null && tree.getStructure() != null) {
            for (var rootNode : tree.getStructure()) {
                appendNode(sb, rootNode, 0);
            }
        }
        return sb.toString();
    }

    private void appendNode(StringBuilder sb, io.github.xiaoailazy.coexistree.indexer.model.TreeNode node, int depth) {
        if (node == null) return;

        String indent = "  ".repeat(depth);
        sb.append(indent).append("- ").append(node.getTitle()).append("\n");

        if (node.getNodes() != null) {
            for (var child : node.getNodes()) {
                appendNode(sb, child, depth + 1);
            }
        }
    }

    private EvaluationReport parseResult(String content) {
        try {
            String json = extractJson(content);
            JsonNode node = objectMapper.readTree(json);

            RiskLevel riskLevel = RiskLevel.valueOf(node.get("riskLevel").asText());
            String summary = node.has("summary") ? node.get("summary").asText() : "";

            List<EvaluationReport.EvaluationItem> items = new ArrayList<>();
            if (node.has("items") && node.get("items").isArray()) {
                for (JsonNode item : node.get("items")) {
                    items.add(new EvaluationReport.EvaluationItem(
                            item.get("name").asText(),
                            item.get("description").asText(),
                            item.has("severity") ? item.get("severity").asText() : "medium",
                            item.has("suggestion") ? item.get("suggestion").asText() : null
                    ));
                }
            }

            return new EvaluationReport(
                    EvaluationCategory.CONFLICT,
                    riskLevel,
                    "功能冲突检测",
                    null,
                    summary,
                    items,
                    null
            );

        } catch (Exception e) {
            log.error("解析冲突检测结果失败: {}", content, e);
            return createErrorReport();
        }
    }

    private String extractJson(String content) {
        String trimmed = content.trim();

        if (trimmed.startsWith("{")) {
            return trimmed;
        }

        // 提取代码块
        int start = trimmed.indexOf("```json");
        if (start != -1) {
            int end = trimmed.indexOf("```", start + 7);
            if (end != -1) {
                return trimmed.substring(start + 7, end).trim();
            }
        }

        // 查找第一个 { 和最后一个 }
        start = trimmed.indexOf("{");
        int end = trimmed.lastIndexOf("}");
        if (start != -1 && end != -1 && end > start) {
            return trimmed.substring(start, end + 1);
        }

        return trimmed;
    }

    private EvaluationReport createErrorReport() {
        return new EvaluationReport(
                EvaluationCategory.CONFLICT,
                RiskLevel.NONE,
                "功能冲突检测",
                null,
                "检测过程出现异常，请稍后重试",
                List.of(),
                null
        );
    }
}
