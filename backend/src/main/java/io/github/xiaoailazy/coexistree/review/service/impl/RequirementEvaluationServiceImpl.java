package io.github.xiaoailazy.coexistree.review.service.impl;

import io.github.xiaoailazy.coexistree.review.model.EvaluationReport;
import io.github.xiaoailazy.coexistree.review.service.RequirementEvaluationService;
import io.github.xiaoailazy.coexistree.review.service.detector.ConflictDetector;
import io.github.xiaoailazy.coexistree.review.service.detector.HistoryConsistencyChecker;
import io.github.xiaoailazy.coexistree.review.service.detector.ModuleImpactAnalyzer;
import io.github.xiaoailazy.coexistree.review.service.detector.RuleConsistencyChecker;
import io.github.xiaoailazy.coexistree.knowledge.model.SystemKnowledgeTree;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 需求评估服务实现
 */
@Slf4j
@Service
public class RequirementEvaluationServiceImpl implements RequirementEvaluationService {

    private final ConflictDetector conflictDetector;
    private final RuleConsistencyChecker ruleConsistencyChecker;
    private final ModuleImpactAnalyzer moduleImpactAnalyzer;
    private final HistoryConsistencyChecker historyConsistencyChecker;

    public RequirementEvaluationServiceImpl(
            ConflictDetector conflictDetector,
            RuleConsistencyChecker ruleConsistencyChecker,
            ModuleImpactAnalyzer moduleImpactAnalyzer,
            HistoryConsistencyChecker historyConsistencyChecker) {
        this.conflictDetector = conflictDetector;
        this.ruleConsistencyChecker = ruleConsistencyChecker;
        this.moduleImpactAnalyzer = moduleImpactAnalyzer;
        this.historyConsistencyChecker = historyConsistencyChecker;
    }

    @Override
    public EvaluationResult evaluate(String requirementContent,
                                     SystemKnowledgeTree systemTree,
                                     Consumer<EvaluationStage> onProgress,
                                     String previousResponseId) {
        List<EvaluationReport> reports = new ArrayList<>();
        String currentResponseId = previousResponseId;

        // 阶段1：功能冲突检测
        if (onProgress != null) {
            onProgress.accept(EvaluationStage.CONFLICT_DETECTION);
        }
        try {
            var result = conflictDetector.detect(requirementContent, systemTree, currentResponseId);
            reports.add(result.report());
            currentResponseId = result.responseId();
            log.debug("功能冲突检测完成: riskLevel={}, responseId={}", result.report().riskLevel(), currentResponseId);
        } catch (Exception e) {
            log.error("功能冲突检测失败", e);
        }

        // 阶段2：业务规则一致性检查
        if (onProgress != null) {
            onProgress.accept(EvaluationStage.CONSISTENCY_CHECK);
        }
        try {
            var result = ruleConsistencyChecker.check(requirementContent, systemTree, currentResponseId);
            reports.add(result.report());
            currentResponseId = result.responseId();
            log.debug("业务规则一致性检查完成: riskLevel={}, responseId={}", result.report().riskLevel(), currentResponseId);
        } catch (Exception e) {
            log.error("业务规则一致性检查失败", e);
        }

        // 阶段3：依赖模块识别
        if (onProgress != null) {
            onProgress.accept(EvaluationStage.IMPACT_ANALYSIS);
        }
        try {
            var result = moduleImpactAnalyzer.analyze(requirementContent, systemTree, currentResponseId);
            reports.add(result.report());
            currentResponseId = result.responseId();
            log.debug("模块影响分析完成: riskLevel={}, responseId={}", result.report().riskLevel(), currentResponseId);
        } catch (Exception e) {
            log.error("模块影响分析失败", e);
        }

        // 阶段4：历史背景/现状一致性检查
        if (onProgress != null) {
            onProgress.accept(EvaluationStage.HISTORY_CHECK);
        }
        try {
            var result = historyConsistencyChecker.check(requirementContent, systemTree, currentResponseId);
            reports.add(result.report());
            currentResponseId = result.responseId();
            log.debug("历史一致性检查完成: riskLevel={}, responseId={}", result.report().riskLevel(), currentResponseId);
        } catch (Exception e) {
            log.error("历史一致性检查失败", e);
        }

        return EvaluationResult.of(reports, currentResponseId);
    }

    @Override
    public EvaluationReport evaluateStage(String requirementContent,
                                          SystemKnowledgeTree systemTree,
                                          EvaluationStage stage) {
        return switch (stage) {
            case CONFLICT_DETECTION -> conflictDetector.detect(requirementContent, systemTree).report();
            case CONSISTENCY_CHECK -> ruleConsistencyChecker.check(requirementContent, systemTree).report();
            case IMPACT_ANALYSIS -> moduleImpactAnalyzer.analyze(requirementContent, systemTree).report();
            case HISTORY_CHECK -> historyConsistencyChecker.check(requirementContent, systemTree).report();
        };
    }
}
