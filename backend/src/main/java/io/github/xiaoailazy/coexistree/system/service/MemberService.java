package io.github.xiaoailazy.coexistree.system.service;

import io.github.xiaoailazy.coexistree.security.model.SecurityUserDetails;
import io.github.xiaoailazy.coexistree.system.dto.AddMemberRequest;
import io.github.xiaoailazy.coexistree.system.dto.MemberResponse;
import io.github.xiaoailazy.coexistree.system.dto.UpdateViewLevelRequest;

import java.util.List;

public interface MemberService {
    MemberResponse addMember(Long systemId, AddMemberRequest request, SecurityUserDetails currentUser);
    void removeMember(Long systemId, Long userId, SecurityUserDetails currentUser);
    MemberResponse updateViewLevel(Long systemId, Long userId, UpdateViewLevelRequest request, SecurityUserDetails currentUser);
    List<MemberResponse> listMembers(Long systemId, SecurityUserDetails currentUser);
}
