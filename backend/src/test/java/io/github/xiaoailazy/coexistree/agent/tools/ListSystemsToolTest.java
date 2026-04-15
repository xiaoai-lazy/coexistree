package io.github.xiaoailazy.coexistree.agent.tools;

import io.github.xiaoailazy.coexistree.agent.context.AgentUserContext;
import io.github.xiaoailazy.coexistree.system.entity.SystemUserMappingEntity;
import io.github.xiaoailazy.coexistree.system.repository.SystemUserMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListSystemsToolTest {

    @Mock
    private SystemUserMappingRepository mappingRepository;

    private ListSystemsTool tool;

    @BeforeEach
    void setUp() {
        tool = new ListSystemsTool(mappingRepository);
    }

    @Test
    void shouldListUserSystems() {
        AgentUserContext context = new AgentUserContext(1L, null, null, "conv-1");

        SystemUserMappingEntity m1 = new SystemUserMappingEntity();
        m1.setUserId(1L);
        m1.setSystemId(10L);
        m1.setViewLevel(3);

        SystemUserMappingEntity m2 = new SystemUserMappingEntity();
        m2.setUserId(1L);
        m2.setSystemId(20L);
        m2.setViewLevel(5);

        when(mappingRepository.findByUserId(1L)).thenReturn(List.of(m1, m2));

        String result = tool.execute(context);

        assertThat(result).contains("系统 ID: 10");
        assertThat(result).contains("系统 ID: 20");
    }

    @Test
    void shouldReturnMessageWhenNoSystems() {
        AgentUserContext context = new AgentUserContext(1L, null, null, "conv-1");

        when(mappingRepository.findByUserId(1L)).thenReturn(List.of());

        String result = tool.execute(context);

        assertThat(result).contains("未加入任何系统");
    }

    @Test
    void shouldHandleException() {
        AgentUserContext context = new AgentUserContext(1L, null, null, "conv-1");

        when(mappingRepository.findByUserId(1L)).thenThrow(new RuntimeException("DB error"));

        String result = tool.execute(context);

        assertThat(result).contains("获取系统列表失败");
        assertThat(result).contains("DB error");
    }
}
