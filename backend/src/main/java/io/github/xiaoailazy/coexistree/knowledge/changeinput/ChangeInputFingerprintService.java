package io.github.xiaoailazy.coexistree.knowledge.changeinput;

import java.util.List;

/**
 * 变更输入指纹（设计 §6.B-13）：稳定排序 + SHA-256。
 */
public interface ChangeInputFingerprintService {

    record Row(long documentId, String contentHash, String treeBuildStatus, boolean hasDocumentTreeRow) {}

    String compute(List<Row> rows);
}
