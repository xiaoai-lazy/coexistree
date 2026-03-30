package io.github.xiaoailazy.coexistree.document.event;

import io.github.xiaoailazy.coexistree.document.task.DocumentTreeBuildTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 文档上传事件监听器
 * 在事务提交后异步处理文档树构建
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentUploadEventListener {

    private final DocumentTreeBuildTask documentTreeBuildTask;

    /**
     * 处理文档上传事件
     * 使用虚拟线程异步执行，不阻塞主线程
     * 在事务提交后才触发，确保数据已持久化
     */
    @Async("documentTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleDocumentUpload(DocumentUploadedEvent event) {
        Long documentId = event.documentId();
        log.info("开始异步处理文档, documentId={}", documentId);

        try {
            documentTreeBuildTask.submit(documentId);
            log.info("文档异步处理完成, documentId={}", documentId);
        } catch (Exception e) {
            log.error("文档异步处理失败, documentId={}", documentId, e);
            // 异常已记录，不抛出让虚拟线程继续处理其他任务
        }
    }
}
