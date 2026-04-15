package io.github.xiaoailazy.coexistree.agent.tools;

import io.github.xiaoailazy.coexistree.agent.context.AgentUserContext;
import io.github.xiaoailazy.coexistree.system.entity.SystemUserMappingEntity;
import io.github.xiaoailazy.coexistree.system.repository.SystemUserMappingRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 列出用户可访问的系统。
 */
@Slf4j
public class ListSystemsTool {

    private final SystemUserMappingRepository mappingRepository;

    public ListSystemsTool(SystemUserMappingRepository mappingRepository) {
        this.mappingRepository = mappingRepository;
    }

    public String execute(AgentUserContext context) {
        try {
            List<SystemUserMappingEntity> mappings = mappingRepository
                    .findByUserId(context.userId());

            if (mappings.isEmpty()) {
                return "当前用户未加入任何系统。";
            }

            return mappings.stream()
                    .map(m -> "- 系统 ID: " + m.getSystemId() +
                            ", 角色: " + m.getRelationType() +
                            ", 查看等级: " + m.getViewLevel())
                    .collect(Collectors.joining("\n"));

        } catch (Exception e) {
            log.error("list_systems 执行失败", e);
            return "获取系统列表失败: " + e.getMessage();
        }
    }
}
