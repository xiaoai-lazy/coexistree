package io.github.xiaoailazy.coexistree.system.service;

import io.github.xiaoailazy.coexistree.security.model.SecurityUserDetails;
import io.github.xiaoailazy.coexistree.system.dto.AddMemberRequest;
import io.github.xiaoailazy.coexistree.system.dto.MemberResponse;
import io.github.xiaoailazy.coexistree.system.dto.UpdateViewLevelRequest;
import io.github.xiaoailazy.coexistree.system.entity.RelationType;
import io.github.xiaoailazy.coexistree.system.entity.SystemUserMappingEntity;
import io.github.xiaoailazy.coexistree.system.repository.SystemUserMappingRepository;
import io.github.xiaoailazy.coexistree.user.entity.UserEntity;
import io.github.xiaoailazy.coexistree.user.entity.UserRole;
import io.github.xiaoailazy.coexistree.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MemberService 测试")
class MemberServiceTest {

    @Mock
    private SystemUserMappingRepository mappingRepository;

    @Mock
    private UserRepository userRepository;

    private MemberService memberService;

    @BeforeEach
    void setUp() {
        memberService = new MemberServiceImpl(mappingRepository, userRepository);
    }

    private SecurityUserDetails createUserDetails(Long id, String username, UserRole role) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setUsername(username);
        user.setPasswordHash("password");
        user.setDisplayName("Test User");
        user.setRole(role);
        return new SecurityUserDetails(user);
    }

    private UserEntity createUser(Long id, String username) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setUsername(username);
        user.setPasswordHash("password");
        user.setDisplayName("Display " + username);
        user.setRole(UserRole.USER);
        return user;
    }

    private SystemUserMappingEntity createMapping(Long id, Long systemId, Long userId, RelationType type, int viewLevel) {
        SystemUserMappingEntity mapping = new SystemUserMappingEntity();
        mapping.setId(id);
        mapping.setSystemId(systemId);
        mapping.setUserId(userId);
        mapping.setRelationType(type);
        mapping.setViewLevel(viewLevel);
        mapping.setAssignedAt(LocalDateTime.now());
        return mapping;
    }

    @Nested
    @DisplayName("添加成员测试")
    class AddMemberTests {

        @Test
        @DisplayName("SUPER_ADMIN 可以添加任何角色成员")
        void shouldAllowSuperAdminToAddAnyRole() {
            // Given
            Long systemId = 1L;
            SecurityUserDetails admin = createUserDetails(99L, "admin", UserRole.SUPER_ADMIN);
            UserEntity newUser = createUser(3L, "newmember");

            AddMemberRequest request = new AddMemberRequest();
            request.setUsername("newmember");
            request.setRelationType(RelationType.MAINTAINER);
            request.setViewLevel(3);

            when(userRepository.findByUsername("newmember")).thenReturn(Optional.of(newUser));
            when(mappingRepository.existsBySystemIdAndUserId(systemId, 3L)).thenReturn(false);
            when(mappingRepository.save(any(SystemUserMappingEntity.class))).thenAnswer(inv -> {
                SystemUserMappingEntity saved = inv.getArgument(0);
                saved.setId(10L);
                return saved;
            });

            // When
            MemberResponse result = memberService.addMember(systemId, request, admin);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo(3L);
            assertThat(result.getRelationType()).isEqualTo(RelationType.MAINTAINER);
            assertThat(result.getViewLevel()).isEqualTo(3);
        }

        @Test
        @DisplayName("OWNER 可以添加 MAINTAINER 和 SUBSCRIBER")
        void shouldAllowOwnerToAddMaintainerAndSubscriber() {
            // Given
            Long systemId = 1L;
            SecurityUserDetails owner = createUserDetails(1L, "owner", UserRole.USER);
            UserEntity newUser = createUser(3L, "newmember");

            SystemUserMappingEntity ownerMapping = createMapping(1L, systemId, 1L, RelationType.OWNER, 5);

            AddMemberRequest request = new AddMemberRequest();
            request.setUsername("newmember");
            request.setRelationType(RelationType.MAINTAINER);
            request.setViewLevel(3);

            when(mappingRepository.findBySystemIdAndUserId(systemId, 1L)).thenReturn(Optional.of(ownerMapping));
            when(userRepository.findByUsername("newmember")).thenReturn(Optional.of(newUser));
            when(mappingRepository.existsBySystemIdAndUserId(systemId, 3L)).thenReturn(false);
            when(mappingRepository.save(any(SystemUserMappingEntity.class))).thenAnswer(inv -> {
                SystemUserMappingEntity saved = inv.getArgument(0);
                saved.setId(10L);
                return saved;
            });

            // When
            MemberResponse result = memberService.addMember(systemId, request, owner);

            // Then
            assertThat(result.getRelationType()).isEqualTo(RelationType.MAINTAINER);
        }

        @Test
        @DisplayName("MAINTAINER 只能添加 SUBSCRIBER")
        void shouldAllowMaintainerToAddOnlySubscriber() {
            // Given
            Long systemId = 1L;
            SecurityUserDetails maintainer = createUserDetails(2L, "maintainer", UserRole.USER);
            UserEntity newUser = createUser(3L, "newmember");

            SystemUserMappingEntity maintainerMapping = createMapping(2L, systemId, 2L, RelationType.MAINTAINER, 3);

            AddMemberRequest request = new AddMemberRequest();
            request.setUsername("newmember");
            request.setRelationType(RelationType.SUBSCRIBER);
            request.setViewLevel(1);

            when(mappingRepository.findBySystemIdAndUserId(systemId, 2L)).thenReturn(Optional.of(maintainerMapping));
            when(userRepository.findByUsername("newmember")).thenReturn(Optional.of(newUser));
            when(mappingRepository.existsBySystemIdAndUserId(systemId, 3L)).thenReturn(false);
            when(mappingRepository.save(any(SystemUserMappingEntity.class))).thenAnswer(inv -> {
                SystemUserMappingEntity saved = inv.getArgument(0);
                saved.setId(10L);
                return saved;
            });

            // When
            MemberResponse result = memberService.addMember(systemId, request, maintainer);

            // Then
            assertThat(result.getRelationType()).isEqualTo(RelationType.SUBSCRIBER);
        }

        @Test
        @DisplayName("MAINTAINER 尝试添加 MAINTAINER 失败")
        void shouldFailWhenMaintainerTriesToAddMaintainer() {
            // Given
            Long systemId = 1L;
            SecurityUserDetails maintainer = createUserDetails(2L, "maintainer", UserRole.USER);

            SystemUserMappingEntity maintainerMapping = createMapping(2L, systemId, 2L, RelationType.MAINTAINER, 3);

            AddMemberRequest request = new AddMemberRequest();
            request.setUsername("newmember");
            request.setRelationType(RelationType.MAINTAINER);

            when(mappingRepository.findBySystemIdAndUserId(systemId, 2L)).thenReturn(Optional.of(maintainerMapping));

            // When & Then
            assertThatThrownBy(() -> memberService.addMember(systemId, request, maintainer))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("维护人只能添加订阅者");
        }

        @Test
        @DisplayName("用户不存在时抛出异常")
        void shouldFailWhenUserNotFound() {
            // Given
            Long systemId = 1L;
            SecurityUserDetails admin = createUserDetails(99L, "admin", UserRole.SUPER_ADMIN);

            AddMemberRequest request = new AddMemberRequest();
            request.setUsername("nonexistent");
            request.setRelationType(RelationType.SUBSCRIBER);

            when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> memberService.addMember(systemId, request, admin))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("用户不存在: nonexistent");
        }

        @Test
        @DisplayName("用户已是成员时抛出异常")
        void shouldFailWhenUserAlreadyMember() {
            // Given
            Long systemId = 1L;
            SecurityUserDetails admin = createUserDetails(99L, "admin", UserRole.SUPER_ADMIN);
            UserEntity existingUser = createUser(3L, "existing");

            AddMemberRequest request = new AddMemberRequest();
            request.setUsername("existing");
            request.setRelationType(RelationType.SUBSCRIBER);

            when(userRepository.findByUsername("existing")).thenReturn(Optional.of(existingUser));
            when(mappingRepository.existsBySystemIdAndUserId(systemId, 3L)).thenReturn(true);

            // When & Then
            assertThatThrownBy(() -> memberService.addMember(systemId, request, admin))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("该用户已经是系统成员");
        }

        @Test
        @DisplayName("viewLevel 超过限制时抛出异常")
        void shouldFailWhenViewLevelExceedsLimit() {
            // Given
            Long systemId = 1L;
            SecurityUserDetails maintainer = createUserDetails(2L, "maintainer", UserRole.USER);
            UserEntity newUser = createUser(3L, "newmember");

            // MAINTAINER 有 viewLevel 2，尝试添加 viewLevel 3 的成员
            SystemUserMappingEntity maintainerMapping = createMapping(2L, systemId, 2L, RelationType.MAINTAINER, 2);

            AddMemberRequest request = new AddMemberRequest();
            request.setUsername("newmember");
            request.setRelationType(RelationType.SUBSCRIBER);
            request.setViewLevel(3); // 超过自己的 viewLevel

            when(mappingRepository.findBySystemIdAndUserId(systemId, 2L)).thenReturn(Optional.of(maintainerMapping));
            when(userRepository.findByUsername("newmember")).thenReturn(Optional.of(newUser));

            // When & Then
            assertThatThrownBy(() -> memberService.addMember(systemId, request, maintainer))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("设置的查看等级不能超过 2");
        }
    }

    @Nested
    @DisplayName("移除成员测试")
    class RemoveMemberTests {

        @Test
        @DisplayName("成功移除 SUBSCRIBER")
        void shouldRemoveSubscriber() {
            // Given
            Long systemId = 1L;
            Long userIdToRemove = 3L;
            SecurityUserDetails owner = createUserDetails(1L, "owner", UserRole.USER);

            SystemUserMappingEntity subscriberMapping = createMapping(3L, systemId, userIdToRemove, RelationType.SUBSCRIBER, 1);
            SystemUserMappingEntity ownerMapping = createMapping(1L, systemId, 1L, RelationType.OWNER, 5);

            when(mappingRepository.findBySystemIdAndUserId(systemId, userIdToRemove)).thenReturn(Optional.of(subscriberMapping));
            when(mappingRepository.findBySystemIdAndUserId(systemId, 1L)).thenReturn(Optional.of(ownerMapping));

            // When
            memberService.removeMember(systemId, userIdToRemove, owner);

            // Then
            verify(mappingRepository).deleteBySystemIdAndUserId(systemId, userIdToRemove);
        }

        @Test
        @DisplayName("不能移除 OWNER")
        void shouldNotRemoveOwner() {
            // Given
            Long systemId = 1L;
            Long ownerId = 1L;
            SecurityUserDetails admin = createUserDetails(99L, "admin", UserRole.SUPER_ADMIN);

            SystemUserMappingEntity ownerMapping = createMapping(1L, systemId, ownerId, RelationType.OWNER, 5);

            when(mappingRepository.findBySystemIdAndUserId(systemId, ownerId)).thenReturn(Optional.of(ownerMapping));

            // When & Then
            assertThatThrownBy(() -> memberService.removeMember(systemId, ownerId, admin))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("不能移除系统主人");

            verify(mappingRepository, never()).deleteBySystemIdAndUserId(any(), any());
        }

        @Test
        @DisplayName("SUPER_ADMIN 可以移除任何非 OWNER 成员")
        void shouldAllowSuperAdminToRemoveNonOwner() {
            // Given
            Long systemId = 1L;
            Long userIdToRemove = 2L;
            SecurityUserDetails admin = createUserDetails(99L, "admin", UserRole.SUPER_ADMIN);

            SystemUserMappingEntity maintainerMapping = createMapping(2L, systemId, userIdToRemove, RelationType.MAINTAINER, 3);

            when(mappingRepository.findBySystemIdAndUserId(systemId, userIdToRemove)).thenReturn(Optional.of(maintainerMapping));

            // When
            memberService.removeMember(systemId, userIdToRemove, admin);

            // Then
            verify(mappingRepository).deleteBySystemIdAndUserId(systemId, userIdToRemove);
        }

        @Test
        @DisplayName("MAINTAINER 只能移除 SUBSCRIBER")
        void shouldAllowMaintainerToRemoveOnlySubscriber() {
            // Given
            Long systemId = 1L;
            Long subscriberId = 3L;
            SecurityUserDetails maintainer = createUserDetails(2L, "maintainer", UserRole.USER);

            SystemUserMappingEntity subscriberMapping = createMapping(3L, systemId, subscriberId, RelationType.SUBSCRIBER, 1);
            SystemUserMappingEntity maintainerMapping = createMapping(2L, systemId, 2L, RelationType.MAINTAINER, 3);

            when(mappingRepository.findBySystemIdAndUserId(systemId, subscriberId)).thenReturn(Optional.of(subscriberMapping));
            when(mappingRepository.findBySystemIdAndUserId(systemId, 2L)).thenReturn(Optional.of(maintainerMapping));

            // When
            memberService.removeMember(systemId, subscriberId, maintainer);

            // Then
            verify(mappingRepository).deleteBySystemIdAndUserId(systemId, subscriberId);
        }

        @Test
        @DisplayName("MAINTAINER 尝试移除 MAINTAINER 失败")
        void shouldFailWhenMaintainerTriesToRemoveMaintainer() {
            // Given
            Long systemId = 1L;
            Long otherMaintainerId = 3L;
            SecurityUserDetails maintainer = createUserDetails(2L, "maintainer", UserRole.USER);

            SystemUserMappingEntity otherMaintainerMapping = createMapping(3L, systemId, otherMaintainerId, RelationType.MAINTAINER, 3);
            SystemUserMappingEntity maintainerMapping = createMapping(2L, systemId, 2L, RelationType.MAINTAINER, 3);

            when(mappingRepository.findBySystemIdAndUserId(systemId, otherMaintainerId)).thenReturn(Optional.of(otherMaintainerMapping));
            when(mappingRepository.findBySystemIdAndUserId(systemId, 2L)).thenReturn(Optional.of(maintainerMapping));

            // When & Then
            assertThatThrownBy(() -> memberService.removeMember(systemId, otherMaintainerId, maintainer))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("维护人只能移除订阅者");
        }

        @Test
        @DisplayName("成员不存在时抛出异常")
        void shouldFailWhenMemberNotFound() {
            // Given
            Long systemId = 1L;
            Long userId = 999L;
            SecurityUserDetails admin = createUserDetails(99L, "admin", UserRole.SUPER_ADMIN);

            when(mappingRepository.findBySystemIdAndUserId(systemId, userId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> memberService.removeMember(systemId, userId, admin))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("成员不存在");
        }
    }

    @Nested
    @DisplayName("更新查看等级测试")
    class UpdateViewLevelTests {

        @Test
        @DisplayName("成功更新 SUBSCRIBER 的 viewLevel")
        void shouldUpdateSubscriberViewLevel() {
            // Given
            Long systemId = 1L;
            Long userId = 3L;
            SecurityUserDetails owner = createUserDetails(1L, "owner", UserRole.USER);
            UserEntity subscriber = createUser(userId, "subscriber");

            SystemUserMappingEntity subscriberMapping = createMapping(3L, systemId, userId, RelationType.SUBSCRIBER, 1);
            SystemUserMappingEntity ownerMapping = createMapping(1L, systemId, 1L, RelationType.OWNER, 5);

            UpdateViewLevelRequest request = new UpdateViewLevelRequest();
            request.setViewLevel(3);

            when(mappingRepository.findBySystemIdAndUserId(systemId, userId)).thenReturn(Optional.of(subscriberMapping));
            when(mappingRepository.findBySystemIdAndUserId(systemId, 1L)).thenReturn(Optional.of(ownerMapping));
            when(mappingRepository.save(any(SystemUserMappingEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(userRepository.findById(userId)).thenReturn(Optional.of(subscriber));

            // When
            MemberResponse result = memberService.updateViewLevel(systemId, userId, request, owner);

            // Then
            assertThat(result.getViewLevel()).isEqualTo(3);
            verify(mappingRepository).save(argThat(m -> m.getViewLevel() == 3));
        }

        @Test
        @DisplayName("不能修改 OWNER 的 viewLevel")
        void shouldNotUpdateOwnerViewLevel() {
            // Given
            Long systemId = 1L;
            Long ownerId = 1L;
            SecurityUserDetails admin = createUserDetails(99L, "admin", UserRole.SUPER_ADMIN);

            SystemUserMappingEntity ownerMapping = createMapping(1L, systemId, ownerId, RelationType.OWNER, 5);

            UpdateViewLevelRequest request = new UpdateViewLevelRequest();
            request.setViewLevel(3);

            when(mappingRepository.findBySystemIdAndUserId(systemId, ownerId)).thenReturn(Optional.of(ownerMapping));

            // When & Then
            assertThatThrownBy(() -> memberService.updateViewLevel(systemId, ownerId, request, admin))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("不能修改主人的查看等级");
        }

        @Test
        @DisplayName("viewLevel 超过限制时抛出异常")
        void shouldFailWhenViewLevelExceedsLimit() {
            // Given
            Long systemId = 1L;
            Long userId = 3L;
            SecurityUserDetails maintainer = createUserDetails(2L, "maintainer", UserRole.USER);

            SystemUserMappingEntity subscriberMapping = createMapping(3L, systemId, userId, RelationType.SUBSCRIBER, 1);
            SystemUserMappingEntity maintainerMapping = createMapping(2L, systemId, 2L, RelationType.MAINTAINER, 2);

            UpdateViewLevelRequest request = new UpdateViewLevelRequest();
            request.setViewLevel(3); // 超过 MAINTAINER 的 viewLevel 2

            when(mappingRepository.findBySystemIdAndUserId(systemId, userId)).thenReturn(Optional.of(subscriberMapping));
            when(mappingRepository.findBySystemIdAndUserId(systemId, 2L)).thenReturn(Optional.of(maintainerMapping));

            // When & Then
            assertThatThrownBy(() -> memberService.updateViewLevel(systemId, userId, request, maintainer))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("设置的查看等级不能超过 2");
        }
    }

    @Nested
    @DisplayName("查询成员列表测试")
    class ListMembersTests {

        @Test
        @DisplayName("SUPER_ADMIN 可以查看任何系统成员")
        void shouldAllowSuperAdminToListAnySystemMembers() {
            // Given
            Long systemId = 1L;
            SecurityUserDetails admin = createUserDetails(99L, "admin", UserRole.SUPER_ADMIN);

            SystemUserMappingEntity mapping1 = createMapping(1L, systemId, 1L, RelationType.OWNER, 5);
            SystemUserMappingEntity mapping2 = createMapping(2L, systemId, 2L, RelationType.SUBSCRIBER, 1);

            UserEntity user1 = createUser(1L, "owner");
            UserEntity user2 = createUser(2L, "subscriber");

            when(mappingRepository.findBySystemId(systemId)).thenReturn(List.of(mapping1, mapping2));
            when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
            when(userRepository.findById(2L)).thenReturn(Optional.of(user2));

            // When
            List<MemberResponse> result = memberService.listMembers(systemId, admin);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getUsername()).isEqualTo("owner");
            assertThat(result.get(1).getUsername()).isEqualTo("subscriber");
        }

        @Test
        @DisplayName("系统成员可以查看成员列表")
        void shouldAllowMemberToListMembers() {
            // Given
            Long systemId = 1L;
            SecurityUserDetails subscriber = createUserDetails(2L, "subscriber", UserRole.USER);

            SystemUserMappingEntity subscriberMapping = createMapping(2L, systemId, 2L, RelationType.SUBSCRIBER, 1);
            SystemUserMappingEntity ownerMapping = createMapping(1L, systemId, 1L, RelationType.OWNER, 5);

            UserEntity owner = createUser(1L, "owner");
            UserEntity subscriberUser = createUser(2L, "subscriber");

            when(mappingRepository.findBySystemIdAndUserId(systemId, 2L)).thenReturn(Optional.of(subscriberMapping));
            when(mappingRepository.findBySystemId(systemId)).thenReturn(List.of(ownerMapping, subscriberMapping));
            when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
            when(userRepository.findById(2L)).thenReturn(Optional.of(subscriberUser));

            // When
            List<MemberResponse> result = memberService.listMembers(systemId, subscriber);

            // Then
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("非成员尝试查看成员列表失败")
        void shouldFailWhenNonMemberTriesToListMembers() {
            // Given
            Long systemId = 1L;
            SecurityUserDetails outsider = createUserDetails(3L, "outsider", UserRole.USER);

            when(mappingRepository.findBySystemIdAndUserId(systemId, 3L)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> memberService.listMembers(systemId, outsider))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("无权限查看此系统");
        }
    }
}
