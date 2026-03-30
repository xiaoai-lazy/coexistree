package io.github.xiaoailazy.coexistree.review.service.detector.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xiaoailazy.coexistree.review.enums.EvaluationCategory;
import io.github.xiaoailazy.coexistree.review.enums.RiskLevel;
import io.github.xiaoailazy.coexistree.review.model.EvaluationReport;
import io.github.xiaoailazy.coexistree.review.service.detector.DetectionResult;
import io.github.xiaoailazy.coexistree.review.service.detector.RuleConsistencyChecker;
import io.github.xiaoailazy.coexistree.knowledge.model.SystemKnowledgeTree;
import io.github.xiaoailazy.coexistree.indexer.llm.LlmClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 业务规则一致性检查器实现
 */
@Slf4j
@Component
public class RuleConsistencyCheckerImpl implements RuleConsistencyChecker {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public RuleConsistencyCheckerImpl(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public DetectionResult check(String requirementContent, SystemKnowledgeTree systemTree, String previousResponseId) {
        String prompt = buildPrompt(requirementContent, systemTree);

        try {
            LlmClient.LlmResponse response = llmClient.chat(prompt, null, 0.3, previousResponseId);
            EvaluationReport report = parseResult(response.content());
            return DetectionResult.of(report, response.responseId());
        } catch (Exception e) {
            log.error("业务规则一致性检查失败", e);
            return DetectionResult.of(createErrorReport(), null);
        }
    }

    private String buildPrompt(String requirementContent, SystemKnowledgeTree systemTree) {
        String existingRules = extractRules(systemTree);

        return """
                作为业务规则分析专家，请检查需求中的业务规则是否与现有规则冲突。

                ## 需求文档
                ```
                %s
                ```

                ## 现有业务规则（从系统知识树提取）
                ```
                %s
                ```

                ## 检测任务
                请识别需求规则与现有规则的不一致：
                1. **规则冲突** - 需求规则与现有规则直接矛盾
                2. **规则扩展** - 需求规则在现有规则基础上增加条件
                3. **规则缺失** - 需求依赖的规则未在现有系统中定义

                ## 输出格式（JSON）
                {
                  "riskLevel": "HIGH|MEDIUM|LOW|NONE",
                  "summary": "总体评估结论",
                  "items": [
                    {
                      "name": "规则名称",
                      "description": "详细描述冲突点",
                      "severity": "high|medium|low",
                      "suggestion": "解决建议"
                    }
                  ]
                }

                风险等级定义：
                - HIGH: 存在严重规则冲突，可能导致系统逻辑错误
                - MEDIUM: 规则扩展或边界条件不清
                - LOW: 规则描述有歧义或需要补充
                - NONE: 规则一致

                请只输出 JSON，不要其他内容。
                """.formatted(requirementContent, existingRules);
    }

    private String extractRules(SystemKnowledgeTree tree) {
        // 从知识树中提取规则相关的文本
        StringBuilder sb = new StringBuilder();
        sb.append("系统知识树包含的功能模块和规则（具体规则需查看详细节点内容）\n");
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
        if (node.getSummary() != null && !node.getSummary().isBlank()) {
            String preview = node.getSummary().replace("\n", " ");
            if (preview.length() > 100) {
                preview = preview.substring(0, 100) + "...";
            }
            sb.append(" [").append(preview).append("]");
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
                            item.has("severity") ? item.get("severity").asText() : "medium",
                            item.has("suggestion") ? item.get("suggestion").asText() : null
                    ));
                }
            }

            return new EvaluationReport(
                    EvaluationCategory.CONSISTENCY,
                    riskLevel,
                    "业务规则一致性",
                    null,
                    summary,
                    items,
                    null
            );

        } catch (Exception e) {
            log.error("解析规则一致性结果失败: {}", content, e);
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
                EvaluationCategory.CONSISTENCY,
                RiskLevel.NONE,
                "业务规则一致性",
                null,
                "检查过程出现异常，请稍后重试",
                List.of(),
                null
        );
    }
}
