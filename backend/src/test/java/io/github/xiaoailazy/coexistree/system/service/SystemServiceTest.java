package io.github.xiaoailazy.coexistree.system.service;

import io.github.xiaoailazy.coexistree.shared.enums.ErrorCode;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import io.github.xiaoailazy.coexistree.document.repository.DocumentRepository;
import io.github.xiaoailazy.coexistree.system.dto.CreateSystemRequest;
import io.github.xiaoailazy.coexistree.system.dto.SystemResponse;
import io.github.xiaoailazy.coexistree.system.dto.UpdateSystemRequest;
import io.github.xiaoailazy.coexistree.system.entity.SystemEntity;
import io.github.xiaoailazy.coexistree.system.repository.SystemRepository;
import io.github.xiaoailazy.coexistree.system.repository.SystemUserMappingRepository;
import io.github.xiaoailazy.coexistree.security.model.SecurityUserDetails;
import io.github.xiaoailazy.coexistree.user.entity.UserEntity;
import io.github.xiaoailazy.coexistree.user.repository.UserRepository;
import io.github.xiaoailazy.coexistree.user.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemServiceTest {

    @Mock
    private SystemRepository systemRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private SystemUserMappingRepository systemUserMappingRepository;
    @Mock
    private UserRepository userRepository;

    private SystemServiceImpl systemService;

    @BeforeEach
    void setUp() {
        systemService = new SystemServiceImpl(systemRepository, documentRepository, systemUserMappingRepository, userRepository);
    }

    private SecurityUserDetails createTestUser() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername("testuser");
        user.setDisplayName("Test User");
        user.setRole(UserRole.SUPER_ADMIN);
        user.setPasswordHash("password");
        return new SecurityUserDetails(user);
    }

    private SystemEntity createTestSystem(Long id, String code, String name) {
        SystemEntity entity = new SystemEntity();
        entity.setId(id);
        entity.setSystemCode(code);
        entity.setSystemName(name);
        entity.setDescription("描述");
        entity.setStatus("ACTIVE");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }

    @Test
    void testCreateSystem() {
        CreateSystemRequest request = new CreateSystemRequest("ops", "运维系统", "运维知识库");
        SecurityUserDetails user = createTestUser();

        when(systemRepository.existsBySystemCode("ops")).thenReturn(false);
        when(systemRepository.save(any(SystemEntity.class))).thenAnswer(invocation -> {
            SystemEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return entity;
        });

        SystemResponse response = systemService.create(request, user);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.systemCode()).isEqualTo("ops");
        assertThat(response.systemName()).isEqualTo("运维系统");
        assertThat(response.description()).isEqualTo("运维知识库");
        assertThat(response.status()).isEqualTo("ACTIVE");

        verify(systemRepository).save(any(SystemEntity.class));
    }

    @Test
    void testCreateSystemWithEmptyCode() {
        CreateSystemRequest request = new CreateSystemRequest("", "运维系统", "描述");
        SecurityUserDetails user = createTestUser();

        assertThatThrownBy(() -> systemService.create(request, user))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SYSTEM_CODE_EMPTY);
    }

    @Test
    void testCreateSystemWithEmptyName() {
        CreateSystemRequest request = new CreateSystemRequest("ops", "", "描述");
        SecurityUserDetails user = createTestUser();

        assertThatThrownBy(() -> systemService.create(request, user))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SYSTEM_NAME_EMPTY);
    }

    @Test
    void testCreateSystemWithDuplicateCode() {
        CreateSystemRequest request = new CreateSystemRequest("ops", "运维系统", "描述");
        SecurityUserDetails user = createTestUser();

        when(systemRepository.existsBySystemCode("ops")).thenReturn(true);

        assertThatThrownBy(() -> systemService.create(request, user))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SYSTEM_CODE_DUPLICATE);
    }

    @Test
    void testGetSystem() {
        SystemEntity entity = createTestSystem(1L, "ops", "运维系统");

        when(systemRepository.findById(1L)).thenReturn(Optional.of(entity));

        SystemResponse response = systemService.get(1L);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.systemCode()).isEqualTo("ops");
    }

    @Test
    void testGetSystemNotFound() {
        when(systemRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> systemService.get(999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SYSTEM_NOT_FOUND);
    }

    @Test
    void testUpdateSystem() {
        SystemEntity entity = createTestSystem(1L, "ops", "旧名称");
        UpdateSystemRequest request = new UpdateSystemRequest("新名称", "新描述", "INACTIVE");

        when(systemRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(systemRepository.save(any(SystemEntity.class))).thenReturn(entity);

        SystemResponse response = systemService.update(1L, request);

        assertThat(response.systemName()).isEqualTo("新名称");
        assertThat(response.description()).isEqualTo("新描述");
        assertThat(response.status()).isEqualTo("INACTIVE");

        verify(systemRepository).save(any(SystemEntity.class));
    }

    @Test
    void testUpdateSystemPartial() {
        SystemEntity entity = createTestSystem(1L, "ops", "旧名称");
        UpdateSystemRequest request = new UpdateSystemRequest("新名称", null, null);

        when(systemRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(systemRepository.save(any(SystemEntity.class))).thenReturn(entity);

        SystemResponse response = systemService.update(1L, request);

        assertThat(response.systemName()).isEqualTo("新名称");
        assertThat(response.description()).isEqualTo("描述");
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void testUpdateSystemNotFound() {
        UpdateSystemRequest request = new UpdateSystemRequest("新名称", null, null);

        when(systemRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> systemService.update(999L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SYSTEM_NOT_FOUND);
    }

    @Test
    void testDeleteSystem() {
        SystemEntity entity = createTestSystem(1L, "ops", "运维系统");

        when(systemRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(documentRepository.countBySystemId(1L)).thenReturn(0L);

        systemService.delete(1L);

        verify(systemRepository).delete(entity);
    }

    @Test
    void testDeleteSystemNotFound() {
        when(systemRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> systemService.delete(999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SYSTEM_NOT_FOUND);
    }

    @Test
    void testDeleteSystemWithDocuments() {
        SystemEntity entity = createTestSystem(1L, "ops", "运维系统");

        when(systemRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(documentRepository.countBySystemId(1L)).thenReturn(5L);

        assertThatThrownBy(() -> systemService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SYSTEM_HAS_DOCUMENTS);
    }

    @Test
    void testListSystems() {
        SystemEntity entity1 = createTestSystem(1L, "ops", "运维系统");
        SystemEntity entity2 = createTestSystem(2L, "dev", "开发系统");
        SecurityUserDetails user = createTestUser();

        when(systemRepository.findAll()).thenReturn(List.of(entity1, entity2));

        List<SystemResponse> responses = systemService.list(user);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).systemCode()).isEqualTo("ops");
        assertThat(responses.get(1).systemCode()).isEqualTo("dev");
    }

    @Test
    void testListSystemsEmpty() {
        SecurityUserDetails user = createTestUser();
        when(systemRepository.findAll()).thenReturn(List.of());

        List<SystemResponse> responses = systemService.list(user);

        assertThat(responses).isEmpty();
    }

    @Test
    void testGetEntity() {
        SystemEntity entity = createTestSystem(1L, "ops", "运维系统");

        when(systemRepository.findById(1L)).thenReturn(Optional.of(entity));

        SystemEntity result = systemService.getEntity(1L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void testGetEntityNotFound() {
        when(systemRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> systemService.getEntity(999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SYSTEM_NOT_FOUND);
    }
}
