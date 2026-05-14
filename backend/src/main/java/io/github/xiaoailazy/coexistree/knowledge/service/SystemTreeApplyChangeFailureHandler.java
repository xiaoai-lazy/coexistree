package io.github.xiaoailazy.coexistree.knowledge.service;

import io.github.xiaoailazy.coexistree.document.entity.DocumentEntity;
import io.github.xiaoailazy.coexistree.document.repository.DocumentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * applyChange 阶段 D：在独立短事务中标记 R/D 文档合并失败（设计 §6.D）。
 */
@Slf4j
@Service
public class SystemTreeApplyChangeFailureHandler {

    public static final String DOC_REQUIREMENT = "REQUIREMENT";
    public static final String DOC_DESIGN = "DESIGN";

    private final DocumentRepository documentRepository;

    public SystemTreeApplyChangeFailureHandler(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRequirementDesignMergeFailed(long changeRecordId) {
        List<DocumentEntity> docs = documentRepository.findAllByChangeRecordIdOrderByIdAsc(changeRecordId);
        LocalDateTime now = LocalDateTime.now();
        for (DocumentEntity d : docs) {
            String ct = d.getDocContentType();
            if (DOC_REQUIREMENT.equals(ct) || DOC_DESIGN.equals(ct)) {
                d.setMergeStatus("FAILED");
                d.setUpdatedAt(now);
                documentRepository.save(d);
            }
        }
        log.warn("Marked REQUIREMENT/DESIGN merge_status=FAILED for changeRecordId={}", changeRecordId);
    }
}
