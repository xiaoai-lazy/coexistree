package io.github.xiaoailazy.coexistree.shared.integration;

import io.github.xiaoailazy.coexistree.chat.entity.ConversationEntity;
import io.github.xiaoailazy.coexistree.chat.entity.MessageEntity;
import io.github.xiaoailazy.coexistree.document.entity.DocumentEntity;
import io.github.xiaoailazy.coexistree.document.entity.DocumentTreeEntity;
import io.github.xiaoailazy.coexistree.knowledge.entity.SystemKnowledgeTreeEntity;
import io.github.xiaoailazy.coexistree.system.entity.SystemEntity;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Factory class for creating test entities.
 * Provides fluent API for building test data.
 */
public class TestDataFactory {

    public static SystemEntityBuilder aSystem() {
        return new SystemEntityBuilder();
    }

    public static DocumentEntityBuilder aDocument() {
        return new DocumentEntityBuilder();
    }

    public static DocumentTreeEntityBuilder aDocumentTree() {
        return new DocumentTreeEntityBuilder();
    }

    public static ConversationEntityBuilder aConversation() {
        return new ConversationEntityBuilder();
    }

    public static MessageEntityBuilder aMessage() {
        return new MessageEntityBuilder();
    }

    public static SystemKnowledgeTreeEntityBuilder aSystemKnowledgeTree() {
        return new SystemKnowledgeTreeEntityBuilder();
    }

    // Builder classes

    public static class SystemEntityBuilder {
        private Long id;
        private String systemCode = "TEST";
        private String systemName = "Test System";
        private String description = "Test system description";
        private String status = "ACTIVE";
        private LocalDateTime createdAt = LocalDateTime.now();
        private LocalDateTime updatedAt = LocalDateTime.now();

        public SystemEntityBuilder withId(Long id) {
            this.id = id;
            return this;
        }

        public SystemEntityBuilder withSystemCode(String code) {
            this.systemCode = code;
            return this;
        }

        public SystemEntityBuilder withSystemName(String name) {
            this.systemName = name;
            return this;
        }

        public SystemEntityBuilder withDescription(String description) {
            this.description = description;
            return this;
        }

        public SystemEntityBuilder withStatus(String status) {
            this.status = status;
            return this;
        }

        public SystemEntity build() {
            SystemEntity entity = new SystemEntity();
            entity.setId(id);
            entity.setSystemCode(systemCode);
            entity.setSystemName(systemName);
            entity.setDescription(description);
            entity.setStatus(status);
            entity.setCreatedAt(createdAt);
            entity.setUpdatedAt(updatedAt);
            return entity;
        }
    }

    public static class DocumentEntityBuilder {
        private Long id;
        private Long systemId = 1L;
        private String docName = "test-document.md";
        private String originalFileName = "test-document.md";
        private String filePath = "/test/path/test-document.md";
        private String fileHash = "abc123";
        private String contentType = "text/markdown";
        private String parseStatus = "PENDING";
        private String parseError = null;
        private String docType = "BASELINE";
        private LocalDateTime createdAt = LocalDateTime.now();
        private LocalDateTime updatedAt = LocalDateTime.now();

        public DocumentEntityBuilder withId(Long id) {
            this.id = id;
            return this;
        }

        public DocumentEntityBuilder withSystemId(Long systemId) {
            this.systemId = systemId;
            return this;
        }

        public DocumentEntityBuilder withDocName(String docName) {
            this.docName = docName;
            return this;
        }

        public DocumentEntityBuilder withOriginalFileName(String fileName) {
            this.originalFileName = fileName;
            return this;
        }

        public DocumentEntityBuilder withFilePath(String filePath) {
            this.filePath = filePath;
            return this;
        }

        public DocumentEntityBuilder withFileHash(String fileHash) {
            this.fileHash = fileHash;
            return this;
        }

        public DocumentEntityBuilder withContentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        public DocumentEntityBuilder withParseStatus(String status) {
            this.parseStatus = status;
            return this;
        }

        public DocumentEntityBuilder withParseError(String error) {
            this.parseError = error;
            return this;
        }

        public DocumentEntityBuilder withDocType(String docType) {
            this.docType = docType;
            return this;
        }

        public DocumentEntity build() {
            DocumentEntity entity = new DocumentEntity();
            entity.setId(id);
            entity.setSystemId(systemId);
            entity.setDocName(docName);
            entity.setOriginalFileName(originalFileName);
            entity.setFilePath(filePath);
            entity.setFileHash(fileHash);
            entity.setContentType(contentType);
            entity.setParseStatus(parseStatus);
            entity.setParseError(parseError);
            entity.setDocType(docType);
            entity.setCreatedAt(createdAt);
            entity.setUpdatedAt(updatedAt);
            return entity;
        }
    }

    public static class DocumentTreeEntityBuilder {
        private Long id;
        private Long documentId = 1L;
        private String treeFilePath = "/test/path/tree.json";
        private String docDescription = "Test document description";
        private Integer nodeCount = 1;
        private LocalDateTime createdAt = LocalDateTime.now();
        private LocalDateTime updatedAt = LocalDateTime.now();

        public DocumentTreeEntityBuilder withId(Long id) {
            this.id = id;
            return this;
        }

        public DocumentTreeEntityBuilder withDocumentId(Long documentId) {
            this.documentId = documentId;
            return this;
        }

        public DocumentTreeEntityBuilder withTreeFilePath(String treeFilePath) {
            this.treeFilePath = treeFilePath;
            return this;
        }

        public DocumentTreeEntityBuilder withDocDescription(String docDescription) {
            this.docDescription = docDescription;
            return this;
        }

        public DocumentTreeEntityBuilder withNodeCount(Integer count) {
            this.nodeCount = count;
            return this;
        }

        public DocumentTreeEntity build() {
            DocumentTreeEntity entity = new DocumentTreeEntity();
            entity.setId(id);
            entity.setDocumentId(documentId);
            entity.setTreeFilePath(treeFilePath);
            entity.setDocDescription(docDescription);
            entity.setNodeCount(nodeCount);
            entity.setCreatedAt(createdAt);
            entity.setUpdatedAt(updatedAt);
            return entity;
        }
    }

    public static class ConversationEntityBuilder {
        private String conversationId = UUID.randomUUID().toString();
        private Long systemId = 1L;
        private String title = "Test Conversation";
        private LocalDateTime createdAt = LocalDateTime.now();
        private LocalDateTime updatedAt = LocalDateTime.now();

        public ConversationEntityBuilder withConversationId(String id) {
            this.conversationId = id;
            return this;
        }

        public ConversationEntityBuilder withSystemId(Long systemId) {
            this.systemId = systemId;
            return this;
        }

        public ConversationEntityBuilder withTitle(String title) {
            this.title = title;
            return this;
        }

        public ConversationEntity build() {
            ConversationEntity entity = new ConversationEntity();
            entity.setConversationId(conversationId);
            entity.setSystemId(systemId);
            entity.setTitle(title);
            entity.setCreatedAt(createdAt);
            entity.setUpdatedAt(updatedAt);
            return entity;
        }
    }

    public static class MessageEntityBuilder {
        private Long id;
        private String conversationId;
        private String role = "USER";
        private String content = "Test message content";
        private String llmResponseId = null;
        private String thinking = null;
        private String citations = null;
        private LocalDateTime createdAt = LocalDateTime.now();

        public MessageEntityBuilder withId(Long id) {
            this.id = id;
            return this;
        }

        public MessageEntityBuilder withConversationId(String conversationId) {
            this.conversationId = conversationId;
            return this;
        }

        public MessageEntityBuilder withRole(String role) {
            this.role = role;
            return this;
        }

        public MessageEntityBuilder withContent(String content) {
            this.content = content;
            return this;
        }

        public MessageEntityBuilder withLlmResponseId(String llmResponseId) {
            this.llmResponseId = llmResponseId;
            return this;
        }

        public MessageEntityBuilder withThinking(String thinking) {
            this.thinking = thinking;
            return this;
        }

        public MessageEntityBuilder withCitations(String citations) {
            this.citations = citations;
            return this;
        }

        public MessageEntity build() {
            MessageEntity entity = new MessageEntity();
            entity.setId(id);
            entity.setConversationId(conversationId);
            entity.setRole(role);
            entity.setContent(content);
            entity.setLlmResponseId(llmResponseId);
            entity.setThinking(thinking);
            entity.setCitations(citations);
            entity.setCreatedAt(createdAt);
            return entity;
        }
    }

    public static class SystemKnowledgeTreeEntityBuilder {
        private Long id;
        private Long systemId = 1L;
        private String treeFilePath = "/test/path/system_tree.json";
        private Integer treeVersion = 1;
        private Integer nodeCount = 10;
        private String treeStatus = "ACTIVE";
        private LocalDateTime createdAt = LocalDateTime.now();
        private LocalDateTime updatedAt = LocalDateTime.now();

        public SystemKnowledgeTreeEntityBuilder withId(Long id) {
            this.id = id;
            return this;
        }

        public SystemKnowledgeTreeEntityBuilder withSystemId(Long systemId) {
            this.systemId = systemId;
            return this;
        }

        public SystemKnowledgeTreeEntityBuilder withTreeVersion(Integer version) {
            this.treeVersion = version;
            return this;
        }

        public SystemKnowledgeTreeEntityBuilder withNodeCount(Integer count) {
            this.nodeCount = count;
            return this;
        }

        public SystemKnowledgeTreeEntityBuilder withTreeStatus(String status) {
            this.treeStatus = status;
            return this;
        }

        public SystemKnowledgeTreeEntity build() {
            SystemKnowledgeTreeEntity entity = new SystemKnowledgeTreeEntity();
            entity.setId(id);
            entity.setSystemId(systemId);
            entity.setTreeFilePath(treeFilePath);
            entity.setTreeVersion(treeVersion);
            entity.setNodeCount(nodeCount);
            entity.setTreeStatus(treeStatus);
            entity.setCreatedAt(createdAt);
            entity.setUpdatedAt(updatedAt);
            return entity;
        }
    }
}
