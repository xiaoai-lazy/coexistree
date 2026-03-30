package io.github.xiaoailazy.coexistree.review.service.detector.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xiaoailazy.coexistree.review.enums.EvaluationCategory;
import io.github.xiaoailazy.coexistree.review.enums.RiskLevel;
import io.github.xiaoailazy.coexistree.review.model.EvaluationReport;
import io.github.xiaoailazy.coexistree.review.service.detector.DetectionResult;
import io.github.xiaoailazy.coexistree.review.service.detector.ModuleImpactAnalyzer;
import io.github.xiaoailazy.coexistree.knowledge.model.SystemKnowledgeTree;
import io.github.xiaoailazy.coexistree.indexer.llm.LlmClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 模块影响分析器实现
 */
@Slf4j
@Component
public class ModuleImpactAnalyzerImpl implements ModuleImpactAnalyzer {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public ModuleImpactAnalyzerImpl(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public DetectionResult analyze(String requirementContent, SystemKnowledgeTree systemTree, String previousResponseId) {
        String prompt = buildPrompt(requirementContent, systemTree);

        try {
            LlmClient.LlmResponse response = llmClient.chat(prompt, null, 0.3, previousResponseId);
            EvaluationReport report = parseResult(response.content());
            return DetectionResult.of(report, response.responseId());
        } catch (Exception e) {
            log.error("模块影响分析失败", e);
            return DetectionResult.of(createErrorReport(), null);
        }
    }

    private String buildPrompt(String requirementContent, SystemKnowledgeTree systemTree) {
        String moduleStructure = extractModuleStructure(systemTree);

        return """
                作为系统架构师，请分析需求涉及的功能模块及影响范围。

                ## 需求文档
                ```
                %s
                ```

                ## 系统模块结构
                ```
                %s
                ```

                ## 分析任务
                请识别：
                1. **直接涉及的模块** - 需求直接修改或依赖的模块
                2. **间接依赖的模块** - 通过调用链或数据流间接影响的模块
                3. **影响范围评估** - 评估变更的复杂度和风险

                ## 输出格式（JSON）
                {
                  "riskLevel": "HIGH|MEDIUM|LOW|NONE",
                  "summary": "影响范围总体评估",
                  "items": [
                    {
                      "name": "模块名称",
                      "description": "具体影响点描述",
                      "severity": "core|dependent|indirect",
                      "suggestion": "实施建议"
                    }
                  ]
                }

                风险等级定义：
                - HIGH: 涉及核心模块，影响面广
                - MEDIUM: 涉及多个模块，有一定影响
                - LOW: 影响范围有限
                - NONE: 模块独立，无外部依赖

                severity 定义：
                - core: 核心模块（需求直接修改）
                - dependent: 依赖模块（需要配合修改）
                - indirect: 间接影响（需验证兼容性）

                请只输出 JSON，不要其他内容。
                """.formatted(requirementContent, moduleStructure);
    }

    private String extractModuleStructure(SystemKnowledgeTree tree) {
        StringBuilder sb = new StringBuilder();
        sb.append("系统模块层次结构：\n");
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
        sb.append(indent).append("- ").append(node.getTitle());
        if (node.getLevel() != null) {
            sb.append(" [Level ").append(node.getLevel()).append("]");
        }
        sb.append("\n");

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
                            item.has("severity") ? item.get("severity").asText() : "dependent",
                            item.has("suggestion") ? item.get("suggestion").asText() : null
                    ));
                }
            }

            return new EvaluationReport(
                    EvaluationCategory.IMPACT,
                    riskLevel,
                    "依赖模块识别",
                    null,
                    summary,
                    items,
                    null
            );

        } catch (Exception e) {
            log.error("解析模块影响结果失败: {}", content, e);
            return createErrorReport();
        }
    }

    private String extractJson(String content) {
        String trimmed = content.trim();

        if (trimmed.startsWith("{")) {
            return trimmed;
        }

        int start = trimmed.indexOf("```json");
        if (start != -1) {
            int end = trimmed.indexOf("```", start + 7);
            if (end != -1) {
                return trimmed.substring(start + 7, end).trim();
            }
        }

        start = trimmed.indexOf("{");
        int end = trimmed.lastIndexOf("}");
        if (start != -1 && end != -1 && end > start) {
            return trimmed.substring(start, end + 1);
        }

        return trimmed;
    }

    private EvaluationReport createErrorReport() {
        return new EvaluationReport(
                EvaluationCategory.IMPACT,
                RiskLevel.NONE,
                "依赖模块识别",
                null,
                "分析过程出现异常，请稍后重试",
                List.of(),
                null
        );
    }
}
