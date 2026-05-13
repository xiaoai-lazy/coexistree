package io.github.xiaoailazy.coexistree.document.service;

import io.github.xiaoailazy.coexistree.document.entity.DocumentEntity;
import io.github.xiaoailazy.coexistree.security.model.SecurityUserDetails;
import io.github.xiaoailazy.coexistree.shared.enums.ErrorCode;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import io.github.xiaoailazy.coexistree.system.entity.SystemUserMappingEntity;
import io.github.xiaoailazy.coexistree.system.repository.SystemUserMappingRepository;
import io.github.xiaoailazy.coexistree.user.entity.UserEntity;
import io.github.xiaoailazy.coexistree.user.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DocumentAccessServiceTest {

    private SystemUserMappingRepository mappingRepository;
    private DocumentAccessService documentAccessService;

    @BeforeEach
    void setUp() {
        mappingRepository = mock(SystemUserMappingRepository.class);
        documentAccessService = new DocumentAccessService(mappingRepository);
    }

    @Test
    void superAdminCanAccessAnySystemAndDocument() {
        SecurityUserDetails admin = user(1L, UserRole.SUPER_ADMIN);
        DocumentEntity document = document(10L, 2L, 5);

        assertThatCode(() -> documentAccessService.checkSystemAccess(10L, admin)).doesNotThrowAnyException();
        assertThatCode(() -> documentAccessService.checkDocumentAccess(document, admin)).doesNotThrowAnyException();
        assertThatCode(() -> documentAccessService.requireCanReadDocument(document, admin)).doesNotThrowAnyException();
        assertThat(documentAccessService.getViewLevel(10L, admin)).isEqualTo(5);
        assertThat(documentAccessService.canReadDocument(document, admin)).isTrue();
    }

    @Test
    void mappedUserCanAccessDocumentAtOrBelowViewLevel() {
        SecurityUserDetails user = user(2L, UserRole.USER);
        DocumentEntity document = document(20L, 7L, 3);
        SystemUserMappingEntity mapping = mapping(7L, 2L, 3);
        when(mappingRepository.findBySystemIdAndUserId(7L, 2L)).thenReturn(Optional.of(mapping));

        assertThatCode(() -> documentAccessService.checkSystemAccess(7L, user)).doesNotThrowAnyException();
        assertThatCode(() -> documentAccessService.checkDocumentAccess(document, user)).doesNotThrowAnyException();
        assertThatCode(() -> documentAccessService.requireCanReadDocument(document, user)).doesNotThrowAnyException();
        assertThat(documentAccessService.getViewLevel(7L, user)).isEqualTo(3);
        assertThat(documentAccessService.canReadDocument(document, user)).isTrue();
    }

    @Test
    void unmappedUserCannotAccessSystemOrDocument() {
        SecurityUserDetails user = user(3L, UserRole.USER);
        DocumentEntity document = document(30L, 8L, 1);
        when(mappingRepository.findBySystemIdAndUserId(8L, 3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentAccessService.checkSystemAccess(8L, user))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PERMISSION_DENIED);
        assertThatThrownBy(() -> documentAccessService.checkDocumentAccess(document, user))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> documentAccessService.requireCanReadDocument(document, user))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权限访问此文档")
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PERMISSION_DENIED);
        assertThat(documentAccessService.getViewLevel(8L, user)).isEqualTo(0);
        assertThat(documentAccessService.canReadDocument(document, user)).isFalse();
    }

    @Test
    void documentAboveViewLevelIsDenied() {
        SecurityUserDetails user = user(4L, UserRole.USER);
        DocumentEntity document = document(40L, 9L, 4);
        SystemUserMappingEntity mapping = mapping(9L, 4L, 2);
        when(mappingRepository.findBySystemIdAndUserId(9L, 4L)).thenReturn(Optional.of(mapping));

        assertThatThrownBy(() -> documentAccessService.checkDocumentAccess(document, user))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PERMISSION_DENIED);
        assertThatThrownBy(() -> documentAccessService.requireCanReadDocument(document, user))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权限访问此文档")
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PERMISSION_DENIED);
        assertThat(documentAccessService.canReadDocument(document, user)).isFalse();
    }

    @Test
    void missingInputsDenyReadAndReturnViewLevelZero() {
        SecurityUserDetails user = user(5L, UserRole.USER);
        DocumentEntity document = document(50L, 10L, 1);

        assertThat(documentAccessService.canReadDocument(null, user)).isFalse();
        assertThat(documentAccessService.canReadDocument(document, (SecurityUserDetails) null)).isFalse();
        assertThat(documentAccessService.getViewLevel(null, user)).isEqualTo(0);
        assertThat(documentAccessService.getViewLevel(10L, null)).isEqualTo(0);
        assertThatThrownBy(() -> documentAccessService.requireCanReadDocument(null, user))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权限访问此文档")
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PERMISSION_DENIED);
    }

    @Test
    void publicReadPolicyShouldRequireSecurityUserDetails() {
        boolean hasLongUserIdReadPolicy = Arrays.stream(DocumentAccessService.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals("canReadDocument")
                        && Arrays.equals(method.getParameterTypes(), new Class<?>[]{DocumentEntity.class, Long.class}));

        assertThat(hasLongUserIdReadPolicy).isFalse();
    }

    @Test
    void superAdminCanReadDocumentWithoutSystemMapping() {
        SecurityUserDetails admin = user(6L, UserRole.SUPER_ADMIN);

        assertThat(documentAccessService.canReadDocument(document(60L, 99L, 5), admin)).isTrue();
        assertThat(documentAccessService.getViewLevel(99L, admin)).isEqualTo(5);
        verifyNoInteractions(mappingRepository);
    }

    @Test
    void nonSuperAdminWithoutMappingCannotReadDocument() {
        SecurityUserDetails user = user(7L, UserRole.USER);
        when(mappingRepository.findBySystemIdAndUserId(99L, 7L)).thenReturn(Optional.empty());

        assertThat(documentAccessService.canReadDocument(document(70L, 99L, 1), user)).isFalse();
    }

    private SecurityUserDetails user(Long id, UserRole role) {
        UserEntity entity = new UserEntity();
        entity.setId(id);
        entity.setUsername("user" + id);
        entity.setPasswordHash("password");
        entity.setRole(role);
        return new SecurityUserDetails(entity);
    }

    private DocumentEntity document(Long id, Long systemId, Integer securityLevel) {
        DocumentEntity document = new DocumentEntity();
        document.setId(id);
        document.setSystemId(systemId);
        document.setSecurityLevel(securityLevel);
        return document;
    }

    private SystemUserMappingEntity mapping(Long systemId, Long userId, Integer viewLevel) {
        SystemUserMappingEntity mapping = new SystemUserMappingEntity();
        mapping.setSystemId(systemId);
        mapping.setUserId(userId);
        mapping.setViewLevel(viewLevel);
        return mapping;
    }
}
