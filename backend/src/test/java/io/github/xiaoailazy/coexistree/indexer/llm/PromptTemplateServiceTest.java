package io.github.xiaoailazy.coexistree.indexer.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xiaoailazy.coexistree.shared.util.JsonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PromptTemplateServiceTest {

    private PromptTemplateService promptTemplateService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonUtils jsonUtils = new JsonUtils(objectMapper);
        promptTemplateService = new PromptTemplateService(jsonUtils);
    }

    @Test
    void buildBaselinePrompt_shouldContainSystemInfo() {
        String systemName = "支付系统";
        String systemCode = "payment";
        String docTreeJson = "{\"nodes\": []}";

        String prompt = promptTemplateService.buildBaselinePrompt(systemName, systemCode, docTreeJson);

        assertThat(prompt).contains("系统名称：" + systemName);
        assertThat(prompt).contains("系统代码：" + systemCode);
        assertThat(prompt).contains(docTreeJson);
        assertThat(prompt).contains("系统架构分析专家");
        assertThat(prompt).contains("systemDescription");
        assertThat(prompt).contains("sourceNodeIds");
    }

    @Test
    void buildBaselinePrompt_shouldContainLayerSpecification() {
        String prompt = promptTemplateService.buildBaselinePrompt("测试系统", "test", "{}");

        assertThat(prompt).contains("根节点");
        assertThat(prompt).contains("一级节点");
        assertThat(prompt).contains("二级节点");
        assertThat(prompt).contains("核心模块");
    }

    @Test
    void buildMergePrompt_shouldContainBothTrees() {
        String systemTreeJson = "{\"structure\": []}";
        String docTreeJson = "{\"nodes\": []}";

        String prompt = promptTemplateService.buildMergePrompt(systemTreeJson, docTreeJson);

        assertThat(prompt).contains("系统知识树（当前状态）");
        assertThat(prompt).contains(systemTreeJson);
        assertThat(prompt).contains("变更文档树（新内容）");
        assertThat(prompt).contains(docTreeJson);
        assertThat(prompt).contains("知识树合并专家");
    }

    @Test
    void buildMergePrompt_shouldContainOperationTypes() {
        String prompt = promptTemplateService.buildMergePrompt("{}", "{}");

        assertThat(prompt).contains("UPDATE");
        assertThat(prompt).contains("CREATE");
        assertThat(prompt).contains("targetNodeId");
        assertThat(prompt).contains("sourceNodeId");
    }

    @Test
    void buildMergePrompt_shouldContainMergeRules() {
        String prompt = promptTemplateService.buildMergePrompt("{}", "{}");

        assertThat(prompt).contains("优先匹配标题完全相同的节点");
        assertThat(prompt).contains("语义相似");
        assertThat(prompt).contains("新增内容");
    }
}
