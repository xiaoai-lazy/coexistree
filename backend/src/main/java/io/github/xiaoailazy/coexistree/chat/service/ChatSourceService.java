package io.github.xiaoailazy.coexistree.chat.service;

import io.github.xiaoailazy.coexistree.chat.dto.SseEvent;
import io.github.xiaoailazy.coexistree.security.model.SecurityUserDetails;

import java.util.List;

public interface ChatSourceService {

    List<SseEvent.SourceDto> retrieveSources(Long systemId, String query, SecurityUserDetails userDetails);
}
