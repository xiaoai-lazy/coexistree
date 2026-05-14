package io.github.xiaoailazy.coexistree.knowledge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xiaoailazy.coexistree.change.entity.SystemChangeRecordEntity;
import io.github.xiaoailazy.coexistree.change.repository.SystemChangeRecordRepository;
import io.github.xiaoailazy.coexistree.document.entity.DocumentEntity;
import io.github.xiaoailazy.coexistree.document.repository.DocumentRepository;
import io.github.xiaoailazy.coexistree.document.repository.DocumentTreeRepository;
import io.github.xiaoailazy.coexistree.document.tree.DocumentTreeBuildService;
import io.github.xiaoailazy.coexistree.featuretree.io.FeatureTreeJsonMapper;
import io.github.xiaoailazy.coexistree.featuretree.model.FeatureNode;
import io.github.xiaoailazy.coexistree.featuretree.model.FeatureTreeRoot;
import io.github.xiaoailazy.coexistree.knowledge.changeinput.ChangeInputFingerprintService;
import io.github.xiaoailazy.coexistree.knowledge.entity.SystemKnowledgeTreeEntity;
import io.github.xiaoailazy.coexistree.knowledge.entity.SystemTreeSnapshotEntity;
import io.github.xiaoailazy.coexistree.knowledge.lock.SystemTreeAdvisoryLockSupport;
import io.github.xiaoailazy.coexistree.knowledge.patch.SystemTreePatchService;
import io.github.xiaoailazy.coexistree.knowledge.plan.SystemTreePlanService;
import io.github.xiaoailazy.coexistree.knowledge.plan.SystemTreeUpdatePlan;
import io.github.xiaoailazy.coexistree.knowledge.repository.SystemKnowledgeTreeRepository;
import io.github.xiaoailazy.coexistree.knowledge.repository.SystemTreeSnapshotRepository;
import io.github.xiaoailazy.coexistree.shared.enums.ErrorCode;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * applyChange 编排：阶段 A（事务外准备 + 计划/模拟）与阶段 B（短事务写锁 + 持久化）。
 *
 * <p>说明：当前库表 {@code system_knowledge_trees} 仍为每系统单行，阶段 B 采用<strong>原地更新</strong> ACTIVE 行
 *（递增 {@code tree_version}），与多版本归档 DDL 解耦；快照表写入 {@code tree_version} / {@code related_change_record_id}（V2 列）。
 */
@Slf4j
@Service
public class SystemTreeUpdateServiceImpl implements SystemTreeUpdateService {

    private static final DateTimeFormatter SNAPSHOT_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm");

    private final SystemChangeRecordRepository changeRecordRepository;
    private final DocumentRepository documentRepository;
    private final DocumentTreeRepository documentTreeRepository;
    private final SystemKnowledgeTreeRepository systemKnowledgeTreeRepository;
    private final SystemTreeSnapshotRepository systemTreeSnapshotRepository;
    private final DocumentTreeBuildService documentTreeBuildService;
    private final ChangeInputFingerprintService fingerprintService;
    private final SystemTreePlanService planService;
    private final SystemTreePatchService patchService;
    private final SystemTreeAdvisoryLockSupport advisoryLockSupport;
    private final SystemTreeApplyChangeFailureHandler failureHandler;
    private final ObjectMapper objectMapper;
    private final FeatureTreeJsonMapper featureTreeJsonMapper;
    private final TransactionTemplate transactionTemplate;

    public SystemTreeUpdateServiceImpl(
            PlatformTransactionManager transactionManager,
            SystemChangeRecordRepository changeRecordRepository,
            DocumentRepository documentRepository,
            DocumentTreeRepository documentTreeRepository,
            SystemKnowledgeTreeRepository systemKnowledgeTreeRepository,
            SystemTreeSnapshotRepository systemTreeSnapshotRepository,
            DocumentTreeBuildService documentTreeBuildService,
            ChangeInputFingerprintService fingerprintService,
            SystemTreePlanService planService,
            SystemTreePatchService patchService,
            SystemTreeAdvisoryLockSupport advisoryLockSupport,
            SystemTreeApplyChangeFailureHandler failureHandler,
            ObjectMapper objectMapper) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.changeRecordRepository = changeRecordRepository;
        this.documentRepository = documentRepository;
        this.documentTreeRepository = documentTreeRepository;
        this.systemKnowledgeTreeRepository = systemKnowledgeTreeRepository;
        this.systemTreeSnapshotRepository = systemTreeSnapshotRepository;
        this.documentTreeBuildService = documentTreeBuildService;
        this.fingerprintService = fingerprintService;
        this.planService = planService;
        this.patchService = patchService;
        this.advisoryLockSupport = advisoryLockSupport;
        this.failureHandler = failureHandler;
        this.objectMapper = objectMapper;
        this.featureTreeJsonMapper = new FeatureTreeJsonMapper(objectMapper);
    }

    @Override
    public void applyChange(long changeRecordId) {
        SystemChangeRecordEntity rec =
                changeRecordRepository
                        .findById(changeRecordId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.APPLY_PRECONDITION_FAILED,
                                                "Change record not found: " + changeRecordId));

        if (rec.getTreeVersionAfter() != null) {
            log.info("applyChange idempotent skip, changeRecordId={} already at treeVersionAfter={}", changeRecordId, rec.getTreeVersionAfter());
            return;
        }

        long systemId = rec.getSystemId();

        documentTreeBuildService.buildDocumentTreesForChange(changeRecordId);

        List<DocumentEntity> docs = documentRepository.findAllByChangeRecordIdOrderByIdAsc(changeRecordId);
        boolean hasSuccessfulRd =
                docs.stream()
                        .anyMatch(
                                d ->
                                        ("REQUIREMENT".equals(d.getDocContentType())
                                                        || "DESIGN".equals(d.getDocContentType()))
                                                && "SUCCESS".equals(d.getTreeBuildStatus()));
        if (!hasSuccessfulRd) {
            throw new BusinessException(
                    ErrorCode.APPLY_PRECONDITION_FAILED,
                    "At least one REQUIREMENT/DESIGN document with tree_build_status=SUCCESS is required");
        }

        SystemKnowledgeTreeEntity active =
                systemKnowledgeTreeRepository
                        .findBySystemIdAndTreeStatus(systemId, "ACTIVE")
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.SYSTEM_TREE_NOT_FOUND,
                                                "No ACTIVE system tree for systemId=" + systemId));

        String fpBeforeLlm = fingerprintService.compute(buildFingerprintRows(changeRecordId));

        String planJson = planService.generateUpdatePlanJson("changeRecord:" + changeRecordId);

        try {
            FeatureTreeRoot rootA = featureTreeJsonMapper.parseRoot(active.getTreeJson());
            patchService.validateAndSimulate(rootA, planJson);
        } catch (BusinessException ex) {
            failureHandler.markRequirementDesignMergeFailed(changeRecordId);
            throw ex;
        } catch (IOException e) {
            failureHandler.markRequirementDesignMergeFailed(changeRecordId);
            throw new BusinessException(ErrorCode.JSON_PARSE_ERROR, e.getMessage());
        }

        final String fpCaptured = fpBeforeLlm;
        transactionTemplate.executeWithoutResult(
                status -> {
                    try {
                        advisoryLockSupport.acquireTransactionalLockForSystem(systemId);
                    } catch (NoSuchAlgorithmException e) {
                        throw new IllegalStateException(e);
                    }

                    SystemChangeRecordEntity locked =
                            changeRecordRepository
                                    .findByIdForUpdate(changeRecordId)
                                    .orElseThrow(
                                            () ->
                                                    new BusinessException(
                                                            ErrorCode.APPLY_PRECONDITION_FAILED,
                                                            "Change record disappeared"));

                    if (locked.getTreeVersionAfter() != null) {
                        return;
                    }

                    SystemKnowledgeTreeEntity activeLocked =
                            systemKnowledgeTreeRepository
                                    .findBySystemIdAndTreeStatusWithLock(systemId, "ACTIVE")
                                    .orElseThrow(
                                            () ->
                                                    new BusinessException(
                                                            ErrorCode.SYSTEM_TREE_NOT_FOUND,
                                                            "No ACTIVE system tree under lock"));

                    SystemTreeUpdatePlan plan;
                    try {
                        plan = objectMapper.readValue(planJson, SystemTreeUpdatePlan.class);
                    } catch (JsonProcessingException e) {
                        throw new BusinessException(ErrorCode.JSON_PARSE_ERROR, e.getMessage());
                    }

                    if (plan.getBaseTreeVersion() != activeLocked.getTreeVersion()) {
                        throw new BusinessException(
                                ErrorCode.BASE_TREE_VERSION_MISMATCH,
                                "Plan baseTreeVersion " + plan.getBaseTreeVersion() + " != active " + activeLocked.getTreeVersion());
                    }

                    String fpNow = fingerprintService.compute(buildFingerprintRows(changeRecordId));
                    if (!fpNow.equals(fpCaptured)) {
                        throw new BusinessException(ErrorCode.CHANGE_INPUT_CHANGED, "Document inputs changed since plan phase");
                    }

                    FeatureTreeRoot rootB;
                    try {
                        rootB = featureTreeJsonMapper.parseRoot(activeLocked.getTreeJson());
                    } catch (IOException e) {
                        throw new BusinessException(ErrorCode.JSON_PARSE_ERROR, e.getMessage());
                    }
                    patchService.validateAndSimulate(rootB, planJson);

                    String newTreeJson;
                    try {
                        newTreeJson = featureTreeJsonMapper.writeTree(rootB);
                    } catch (IOException e) {
                        throw new BusinessException(ErrorCode.JSON_SERIALIZE_ERROR, e.getMessage());
                    }

                    int nextVersion = activeLocked.getTreeVersion() + 1;
                    activeLocked.setTreeJson(newTreeJson);
                    activeLocked.setTreeVersion(nextVersion);
                    activeLocked.setNodeCount(countFeatureNodes(rootB));
                    activeLocked.setUpdatedAt(LocalDateTime.now());
                    systemKnowledgeTreeRepository.save(activeLocked);

                    locked.setTreeVersionBefore(plan.getBaseTreeVersion());
                    locked.setTreeVersionAfter(nextVersion);
                    locked.setUpdatedAt(LocalDateTime.now());
                    changeRecordRepository.save(locked);

                    markRequirementDesignMergeSuccess(changeRecordId);
                    saveSnapshot(systemId, changeRecordId, nextVersion, newTreeJson, docs, countFeatureNodes(rootB));
                });
    }

    private void markRequirementDesignMergeSuccess(long changeRecordId) {
        List<DocumentEntity> list = documentRepository.findAllByChangeRecordIdOrderByIdAsc(changeRecordId);
        LocalDateTime now = LocalDateTime.now();
        for (DocumentEntity d : list) {
            String ct = d.getDocContentType();
            if (("REQUIREMENT".equals(ct) || "DESIGN".equals(ct)) && "SUCCESS".equals(d.getTreeBuildStatus())) {
                d.setMergeStatus("SUCCESS");
                d.setUpdatedAt(now);
                documentRepository.save(d);
            }
        }
    }

    private void saveSnapshot(
            long systemId,
            long changeRecordId,
            int treeVersion,
            String treeJson,
            List<DocumentEntity> docs,
            int nodeCount) {
        LocalDateTime now = LocalDateTime.now();
        String snapshotName = "tree-" + now.format(SNAPSHOT_NAME_FORMAT);
        Long triggerDocId =
                docs.stream()
                        .filter(d -> "REQUIREMENT".equals(d.getDocContentType()) || "DESIGN".equals(d.getDocContentType()))
                        .map(DocumentEntity::getId)
                        .findFirst()
                        .orElse(null);

        SystemTreeSnapshotEntity snap =
                SystemTreeSnapshotEntity.builder()
                        .systemId(systemId)
                        .snapshotName(snapshotName)
                        .treeJson(treeJson)
                        .treeVersion(treeVersion)
                        .relatedChangeRecordId(changeRecordId)
                        .triggeredByDocId(triggerDocId)
                        .triggeredBy("applyChange")
                        .nodeCount(nodeCount)
                        .isPinned(false)
                        .status("ACTIVE")
                        .createdAt(now)
                        .build();
        systemTreeSnapshotRepository.save(snap);
    }

    private List<ChangeInputFingerprintService.Row> buildFingerprintRows(long changeRecordId) {
        return documentRepository.findAllByChangeRecordIdOrderByIdAsc(changeRecordId).stream()
                .map(
                        d ->
                                new ChangeInputFingerprintService.Row(
                                        d.getId(),
                                        d.getContentHash() != null ? d.getContentHash() : "",
                                        d.getTreeBuildStatus() != null ? d.getTreeBuildStatus() : "",
                                        documentTreeRepository.findByDocumentId(d.getId()).isPresent()))
                .toList();
    }

    private static int countFeatureNodes(FeatureTreeRoot root) {
        if (root == null || root.getTree() == null) {
            return 0;
        }
        return countNode(root.getTree());
    }

    private static int countNode(FeatureNode n) {
        int c = 1;
        if (n.getNodes() != null) {
            for (FeatureNode ch : n.getNodes()) {
                c += countNode(ch);
            }
        }
        return c;
    }
}
