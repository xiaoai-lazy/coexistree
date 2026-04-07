package io.github.xiaoailazy.coexistree.document.service;

import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import io.github.xiaoailazy.coexistree.config.AppStorageProperties;
import io.github.xiaoailazy.coexistree.document.dto.DocumentResponse;
import io.github.xiaoailazy.coexistree.document.entity.DocumentEntity;
import io.github.xiaoailazy.coexistree.document.repository.DocumentRepository;
import io.github.xiaoailazy.coexistree.document.repository.DocumentTreeRepository;
import io.github.xiaoailazy.coexistree.document.storage.MarkdownFileStorageService;
import io.github.xiaoailazy.coexistree.document.event.DocumentUploadedEvent;
import io.github.xiaoailazy.coexistree.knowledge.entity.SystemKnowledgeTreeEntity;
import io.github.xiaoailazy.coexistree.knowledge.repository.SystemKnowledgeTreeRepository;
import io.github.xiaoailazy.coexistree.security.model.SecurityUserDetails;
import io.github.xiaoailazy.coexistree.system.entity.RelationType;
import io.github.xiaoailazy.coexistree.system.entity.SystemEntity;
import io.github.xiaoailazy.coexistree.system.entity.SystemUserMappingEntity;
import io.github.xiaoailazy.coexistree.system.repository.SystemUserMappingRepository;
import io.github.xiaoailazy.coexistree.system.service.SystemService;
import io.github.xiaoailazy.coexistree.user.entity.UserEntity;
import io.github.xiaoailazy.coexistree.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentTreeRepository documentTreeRepository;
    @Mock
    private SystemService systemService;
    @Mock
    private MarkdownFileStorageService markdownFileStorageService;
    @Mock
    private SystemKnowledgeTreeRepository systemKnowledgeTreeRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private SystemUserMappingRepository systemUserMappingRepository;
    @Mock
    private io.github.xiaoailazy.coexistree.indexer.storage.TreeFileLoader treeFileLoader;

    private final AppStorageProperties storageProperties = new AppStorageProperties("./data/docs", "./data/trees", "./data/system-trees");

    private SecurityUserDetails createOwnerUser() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername("testuser");
        user.setDisplayName("Test User");
        user.setRole(UserRole.USER);
        user.setPasswordHash("password");
        return new SecurityUserDetails(user);
    }

    private void mockOwnerPermission(Long systemId, Long userId) {
        SystemUserMappingEntity mapping = new SystemUserMappingEntity();
        mapping.setSystemId(systemId);
        mapping.setUserId(userId);
        mapping.setRelationType(RelationType.OWNER);
        mapping.setViewLevel(5);
        when(systemUserMappingRepository.findBySystemIdAndUserId(systemId, userId))
                .thenReturn(Optional.of(mapping));
    }

    @Test
    void shouldUploadMarkdownAndPublishEvent() {
        DocumentServiceImpl documentService = new DocumentServiceImpl(
                documentRepository,
                documentTreeRepository,
                systemService,
                storageProperties,
                markdownFileStorageService,
                systemKnowledgeTreeRepository,
                systemUserMappingRepository,
                eventPublisher,
                treeFileLoader
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "guide.md",
                "text/markdown",
                "# Guide\ncontent".getBytes()
        );
        SystemEntity system = new SystemEntity();
        system.setId(1L);
        system.setSystemCode("ops");

        SecurityUserDetails userDetails = createOwnerUser();
        mockOwnerPermission(1L, 1L);

        when(systemService.getEntity(1L)).thenReturn(system);
        when(systemKnowledgeTreeRepository.findBySystemId(1L)).thenReturn(Optional.empty());
        when(documentRepository.save(any(DocumentEntity.class)))
                .thenAnswer(invocation -> {
                    DocumentEntity entity = invocation.getArgument(0);
                    if (entity.getId() == null) {
                        entity.setId(9L);
                    }
                    return entity;
                });

        DocumentResponse response = documentService.upload(file, 1L, 1, userDetails);

        assertThat(response.id()).isEqualTo(9L);
        assertThat(response.systemId()).isEqualTo(1L);
        assertThat(response.parseStatus()).isEqualTo("PENDING");

        verify(markdownFileStorageService).save(any(Path.class), any());
        verify(documentRepository, times(2)).save(any(DocumentEntity.class));

        ArgumentCaptor<DocumentUploadedEvent> eventCaptor = ArgumentCaptor.forClass(DocumentUploadedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().documentId()).isEqualTo(9L);
    }

    @Test
    void shouldRejectNonMarkdownFile() {
        DocumentServiceImpl documentService = new DocumentServiceImpl(
                documentRepository,
                documentTreeRepository,
                systemService,
                storageProperties,
                markdownFileStorageService,
                systemKnowledgeTreeRepository,
                systemUserMappingRepository,
                eventPublisher,
                treeFileLoader
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "guide.txt",
                "text/plain",
                "content".getBytes()
        );

        SecurityUserDetails userDetails = createOwnerUser();

        assertThatThrownBy(() -> documentService.upload(file, 1L, 1, userDetails))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldGetDocumentById() {
        DocumentServiceImpl documentService = new DocumentServiceImpl(
                documentRepository,
                documentTreeRepository,
                systemService,
                storageProperties,
                markdownFileStorageService,
                systemKnowledgeTreeRepository,
                systemUserMappingRepository,
                eventPublisher,
                treeFileLoader
        );

        DocumentEntity entity = new DocumentEntity();
        entity.setId(1L);
        entity.setSystemId(1L);
        entity.setDocName("test.md");
        entity.setOriginalFileName("test.md");
        entity.setParseStatus("SUCCESS");

        when(documentRepository.findById(1L)).thenReturn(java.util.Optional.of(entity));

        DocumentResponse response = documentService.getById(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.systemId()).isEqualTo(1L);
        assertThat(response.docName()).isEqualTo("test.md");
    }

    @Test
    void shouldThrowExceptionWhenDocumentNotFound() {
        DocumentServiceImpl documentService = new DocumentServiceImpl(
                documentRepository,
                documentTreeRepository,
                systemService,
                storageProperties,
                markdownFileStorageService,
                systemKnowledgeTreeRepository,
                systemUserMappingRepository,
                eventPublisher,
                treeFileLoader
        );

        when(documentRepository.findById(999L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> documentService.getById(999L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldListDocumentsBySystemId() {
        DocumentServiceImpl documentService = new DocumentServiceImpl(
                documentRepository,
                documentTreeRepository,
                systemService,
                storageProperties,
                markdownFileStorageService,
                systemKnowledgeTreeRepository,
                systemUserMappingRepository,
                eventPublisher,
                treeFileLoader
        );

        DocumentEntity entity1 = new DocumentEntity();
        entity1.setId(1L);
        entity1.setSystemId(1L);
        entity1.setDocName("doc1.md");
        entity1.setParseStatus("SUCCESS");
        entity1.setSecurityLevel(1);

        DocumentEntity entity2 = new DocumentEntity();
        entity2.setId(2L);
        entity2.setSystemId(1L);
        entity2.setDocName("doc2.md");
        entity2.setParseStatus("SUCCESS");
        entity2.setSecurityLevel(1);

        SecurityUserDetails userDetails = createOwnerUser();
        mockOwnerPermission(1L, 1L);

        when(documentRepository.findBySystemId(1L)).thenReturn(java.util.List.of(entity1, entity2));

        java.util.List<DocumentResponse> responses = documentService.listBySystem(1L, userDetails);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).docName()).isEqualTo("doc1.md");
        assertThat(responses.get(1).docName()).isEqualTo("doc2.md");
    }

    @Test
    void shouldSetDocTypeToBaselineWhenSystemTreeDoesNotExist() {
        DocumentServiceImpl documentService = new DocumentServiceImpl(
                documentRepository,
                documentTreeRepository,
                systemService,
                storageProperties,
                markdownFileStorageService,
                systemKnowledgeTreeRepository,
                systemUserMappingRepository,
                eventPublisher,
                treeFileLoader
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "baseline.md",
                "text/markdown",
                "# Baseline\ncontent".getBytes()
        );
        SystemEntity system = new SystemEntity();
        system.setId(1L);
        system.setSystemCode("ops");

        SecurityUserDetails userDetails = createOwnerUser();
        mockOwnerPermission(1L, 1L);

        when(systemService.getEntity(1L)).thenReturn(system);
        when(systemKnowledgeTreeRepository.findBySystemId(1L)).thenReturn(Optional.empty());
        when(documentRepository.save(any(DocumentEntity.class)))
                .thenAnswer(invocation -> {
                    DocumentEntity entity = invocation.getArgument(0);
                    if (entity.getId() == null) {
                        entity.setId(10L);
                    }
                    return entity;
                });

        documentService.upload(file, 1L, 1, userDetails);

        verify(documentRepository, times(2)).save(any(DocumentEntity.class));
    }

    @Test
    void shouldSetDocTypeToBaselineWhenSystemTreeStatusIsEmpty() {
        DocumentServiceImpl documentService = new DocumentServiceImpl(
                documentRepository,
                documentTreeRepository,
                systemService,
                storageProperties,
                markdownFileStorageService,
                systemKnowledgeTreeRepository,
                systemUserMappingRepository,
                eventPublisher,
                treeFileLoader
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "baseline.md",
                "text/markdown",
                "# Baseline\ncontent".getBytes()
        );
        SystemEntity system = new SystemEntity();
        system.setId(1L);
        system.setSystemCode("ops");

        SystemKnowledgeTreeEntity treeEntity = new SystemKnowledgeTreeEntity();
        treeEntity.setSystemId(1L);
        treeEntity.setTreeStatus("EMPTY");

        SecurityUserDetails userDetails = createOwnerUser();
        mockOwnerPermission(1L, 1L);

        when(systemService.getEntity(1L)).thenReturn(system);
        when(systemKnowledgeTreeRepository.findBySystemId(1L)).thenReturn(Optional.of(treeEntity));
        when(documentRepository.save(any(DocumentEntity.class)))
                .thenAnswer(invocation -> {
                    DocumentEntity entity = invocation.getArgument(0);
                    if (entity.getId() == null) {
                        entity.setId(11L);
                    }
                    return entity;
                });

        documentService.upload(file, 1L, 1, userDetails);

        verify(documentRepository, times(2)).save(any(DocumentEntity.class));
    }

    @Test
    void shouldSetDocTypeToChangeWhenSystemTreeStatusIsActive() {
        DocumentServiceImpl documentService = new DocumentServiceImpl(
                documentRepository,
                documentTreeRepository,
                systemService,
                storageProperties,
                markdownFileStorageService,
                systemKnowledgeTreeRepository,
                systemUserMappingRepository,
                eventPublisher,
                treeFileLoader
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "change.md",
                "text/markdown",
                "# Change\ncontent".getBytes()
        );
        SystemEntity system = new SystemEntity();
        system.setId(1L);
        system.setSystemCode("ops");

        SystemKnowledgeTreeEntity treeEntity = new SystemKnowledgeTreeEntity();
        treeEntity.setSystemId(1L);
        treeEntity.setTreeStatus("ACTIVE");

        SecurityUserDetails userDetails = createOwnerUser();
        mockOwnerPermission(1L, 1L);

        when(systemService.getEntity(1L)).thenReturn(system);
        when(systemKnowledgeTreeRepository.findBySystemId(1L)).thenReturn(Optional.of(treeEntity));
        when(documentRepository.save(any(DocumentEntity.class)))
                .thenAnswer(invocation -> {
                    DocumentEntity entity = invocation.getArgument(0);
                    if (entity.getId() == null) {
                        entity.setId(12L);
                    }
                    return entity;
                });

        documentService.upload(file, 1L, 1, userDetails);

        verify(documentRepository, times(2)).save(any(DocumentEntity.class));
    }

    @Test
    void shouldSetDocTypeToChangeWhenSystemTreeStatusIsBuilding() {
        DocumentServiceImpl documentService = new DocumentServiceImpl(
                documentRepository,
                documentTreeRepository,
                systemService,
                storageProperties,
                markdownFileStorageService,
                systemKnowledgeTreeRepository,
                systemUserMappingRepository,
                eventPublisher,
                treeFileLoader
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "change.md",
                "text/markdown",
                "# Change\ncontent".getBytes()
        );
        SystemEntity system = new SystemEntity();
        system.setId(1L);
        system.setSystemCode("ops");

        SystemKnowledgeTreeEntity treeEntity = new SystemKnowledgeTreeEntity();
        treeEntity.setSystemId(1L);
        treeEntity.setTreeStatus("BUILDING");

        SecurityUserDetails userDetails = createOwnerUser();
        mockOwnerPermission(1L, 1L);

        when(systemService.getEntity(1L)).thenReturn(system);
        when(systemKnowledgeTreeRepository.findBySystemId(1L)).thenReturn(Optional.of(treeEntity));
        when(documentRepository.save(any(DocumentEntity.class)))
                .thenAnswer(invocation -> {
                    DocumentEntity entity = invocation.getArgument(0);
                    if (entity.getId() == null) {
                        entity.setId(13L);
                    }
                    return entity;
                });

        documentService.upload(file, 1L, 1, userDetails);

        verify(documentRepository, times(2)).save(any(DocumentEntity.class));
    }

    @Test
    void shouldGetDocumentContentWithAnchors() {
        // Given
        DocumentServiceImpl documentService = new DocumentServiceImpl(
                documentRepository,
                documentTreeRepository,
                systemService,
                storageProperties,
                markdownFileStorageService,
                systemKnowledgeTreeRepository,
                systemUserMappingRepository,
                eventPublisher,
                treeFileLoader
        );

        Long documentId = 1L;
        DocumentEntity entity = new DocumentEntity();
        entity.setId(documentId);
        entity.setSystemId(1L);
        entity.setDocName("test.md");
        entity.setOriginalFileName("test.md");
        entity.setFilePath("/nonexistent/path.md"); // File doesn't exist, should return empty content
        entity.setSecurityLevel(1);

        SecurityUserDetails userDetails = createOwnerUser();
        mockOwnerPermission(1L, 1L);

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(entity));

        // When
        io.github.xiaoailazy.coexistree.document.dto.DocumentContentResponse response =
                documentService.getContent(documentId, userDetails);

        // Then
        assertThat(response.docId()).isEqualTo(documentId);
        assertThat(response.docName()).isEqualTo("test.md");
        assertThat(response.contentType()).isEqualTo("text/markdown");
        assertThat(response.content()).isEqualTo(""); // File doesn't exist
        assertThat(response.downloadUrl()).isEqualTo("/api/v1/documents/1/download");
    }

    @Test
    void shouldThrowExceptionWhenDocumentNotFoundForGetContent() {
        // Given
        DocumentServiceImpl documentService = new DocumentServiceImpl(
                documentRepository,
                documentTreeRepository,
                systemService,
                storageProperties,
                markdownFileStorageService,
                systemKnowledgeTreeRepository,
                systemUserMappingRepository,
                eventPublisher,
                treeFileLoader
        );

        Long documentId = 999L;
        SecurityUserDetails userDetails = createOwnerUser();

        when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> documentService.getContent(documentId, userDetails))
                .isInstanceOf(io.github.xiaoailazy.coexistree.shared.exception.BusinessException.class)
                .hasMessageContaining("Document not found");
    }

    @Test
    void shouldThrowExceptionWhenNoPermissionForGetContent() {
        // Given
        DocumentServiceImpl documentService = new DocumentServiceImpl(
                documentRepository,
                documentTreeRepository,
                systemService,
                storageProperties,
                markdownFileStorageService,
                systemKnowledgeTreeRepository,
                systemUserMappingRepository,
                eventPublisher,
                treeFileLoader
        );

        Long documentId = 1L;
        DocumentEntity entity = new DocumentEntity();
        entity.setId(documentId);
        entity.setSystemId(1L);
        entity.setDocName("secret.md");
        entity.setSecurityLevel(5); // High security level

        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername("testuser");
        user.setDisplayName("Test User");
        user.setRole(UserRole.USER);
        user.setPasswordHash("password");
        SecurityUserDetails userDetails = new SecurityUserDetails(user);

        // User has no mapping to this system
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(entity));
        when(systemUserMappingRepository.findBySystemIdAndUserId(1L, 1L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> documentService.getContent(documentId, userDetails))
                .isInstanceOf(io.github.xiaoailazy.coexistree.shared.exception.BusinessException.class)
                .hasMessageContaining("无权限访问此文档");
    }
}
