package io.github.xiaoailazy.coexistree.document.repository;

import io.github.xiaoailazy.coexistree.document.entity.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentRepository extends JpaRepository<DocumentEntity, Long> {
    List<DocumentEntity> findBySystemId(Long systemId);
    long countBySystemId(Long systemId);

    /**
     * 检查系统内是否存在相同内容且未处理失败的文档
     */
    boolean existsBySystemIdAndContentHashAndParseStatusNot(
        Long systemId, String contentHash, String parseStatus);
}

