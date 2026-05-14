package io.github.xiaoailazy.coexistree.document.service;

import io.github.xiaoailazy.coexistree.change.entity.SystemChangeRecordEntity;
import io.github.xiaoailazy.coexistree.change.repository.SystemChangeRecordRepository;
import io.github.xiaoailazy.coexistree.document.dto.ChangeDocumentUploadCommand;
import io.github.xiaoailazy.coexistree.shared.enums.ErrorCode;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import io.github.xiaoailazy.coexistree.shared.util.JsonUtils;
import io.github.xiaoailazy.coexistree.security.model.SecurityUserDetails;
import io.github.xiaoailazy.coexistree.system.entity.RelationType;
import io.github.xiaoailazy.coexistree.system.entity.SystemUserMappingEntity;
import io.github.xiaoailazy.coexistree.system.repository.SystemUserMappingRepository;
import io.github.xiaoailazy.coexistree.system.service.SystemService;
import io.github.xiaoailazy.coexistree.user.entity.UserEntity;
import io.github.xiaoailazy.coexistree.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentUploadChangeLockTest {

    @Mock
    private io.github.xiaoailazy.coexistree.document.repository.DocumentRepository documentRepository;
    @Mock
    private io.github.xiaoailazy.coexistree.document.repository.DocumentTreeRepository documentTreeRepository;
    @Mock
    private SystemService systemService;
    @Mock
    private io.github.xiaoailazy.coexistree.knowledge.repository.SystemKnowledgeTreeRepository systemKnowledgeTreeRepository;
    @Mock
    private DocumentAccessService documentAccessService;
    @Mock
    private SystemUserMappingRepository systemUserMappingRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private JsonUtils jsonUtils;
    @Mock
    private SystemChangeRecordRepository changeRecordRepository;

    private ChangeDocumentUploadCommand minimalUploadCommand() {
        return new ChangeDocumentUploadCommand("x.md", "# hi", "DESIGN", 1);
    }

    private SecurityUserDetails maintainerUser() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername("u");
        user.setDisplayName("U");
        user.setRole(UserRole.USER);
        user.setPasswordHash("p");
        return new SecurityUserDetails(user);
    }

    private void mockMaintainer(Long systemId, Long userId) {
        SystemUserMappingEntity mapping = new SystemUserMappingEntity();
        mapping.setSystemId(systemId);
        mapping.setUserId(userId);
        mapping.setRelationType(RelationType.MAINTAINER);
        mapping.setViewLevel(5);
        when(systemUserMappingRepository.findBySystemIdAndUserId(systemId, userId))
                .thenReturn(Optional.of(mapping));
    }

    @Test
    void uploadRejectedWhenChangeAlreadyApplied() {
        SystemChangeRecordEntity rec = new SystemChangeRecordEntity();
        rec.setId(1L);
        rec.setSystemId(9L);
        rec.setTreeVersionAfter(3);
        when(changeRecordRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(rec));

        DocumentServiceImpl documentService =
                new DocumentServiceImpl(
                        documentRepository,
                        documentTreeRepository,
                        systemService,
                        systemKnowledgeTreeRepository,
                        documentAccessService,
                        systemUserMappingRepository,
                        eventPublisher,
                        jsonUtils,
                        changeRecordRepository);

        SecurityUserDetails user = maintainerUser();
        mockMaintainer(9L, 1L);

        assertThatThrownBy(() -> documentService.uploadForChange(9L, 1L, minimalUploadCommand(), user))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.APPLY_PRECONDITION_FAILED));

        verify(documentRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}
