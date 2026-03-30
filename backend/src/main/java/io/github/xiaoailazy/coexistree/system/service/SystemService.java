package io.github.xiaoailazy.coexistree.system.service;

import io.github.xiaoailazy.coexistree.system.dto.CreateSystemRequest;
import io.github.xiaoailazy.coexistree.system.dto.SystemResponse;
import io.github.xiaoailazy.coexistree.system.dto.UpdateSystemRequest;
import io.github.xiaoailazy.coexistree.system.entity.SystemEntity;

import java.util.List;

public interface SystemService {

    SystemEntity getEntity(Long id);

    SystemResponse create(CreateSystemRequest request);

    SystemResponse get(Long id);

    SystemResponse update(Long id, UpdateSystemRequest request);

    void delete(Long id);

    List<SystemResponse> list();
}
