package io.github.xiaoailazy.coexistree.indexer.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import io.github.xiaoailazy.coexistree.shared.util.JsonUtils;
import io.github.xiaoailazy.coexistree.knowledge.model.LlmTreeNode;
import io.github.xiaoailazy.coexistree.knowledge.model.MergeInstruction;
import io.github.xiaoailazy.coexistree.knowledge.model.SystemTreeStructure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmResponseParserTest {

    private LlmResponseParser parser;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonUtils jsonUtils = new JsonUtils(objectMapper);
        parser = new LlmResponseParser(jsonUtils);
    }

    @Test
    void parseSystemTreeStructure_shouldParseValidJson() {
        String json = """
            {
                "systemDescription": "支付系统",
                "structure": [
                    {
                        "title": "支付核心",
                        "sourceNodeIds": ["doc1_node1"],
                        "children": []
                    }
                ]
            }
            """;

        SystemTreeStructure result = parser.parseSystemTreeStructure(json);

        assertThat(result).isNotNull();
        assertThat(result.getSystemDescription()).isEqualTo("支付系统");
        assertThat(result.getStructure()).hasSize(1);
        assertThat(result.getStructure().get(0).getTitle()).isEqualTo("支付核心");
        assertThat(result.getStructure().get(0).getSourceNodeIds()).containsExactly("doc1_node1");
    }

    @Test
    void parseSystemTreeStructure_shouldParseNestedNodes() {
        String json = """
            {
                "systemDescription": "测试系统",
                "structure": [
                    {
                        "title": "模块A",
                        "sourceNodeIds": ["node1"],
                        "children": [
                            {
                                "title": "子模块A1",
                                "sourceNodeIds": ["node2"],
                                "children": []
                            }
                        ]
                    }
                ]
            }
            """;

        SystemTreeStructure result = parser.parseSystemTreeStructure(json);

        assertThat(result.getStructure()).hasSize(1);
        LlmTreeNode parent = result.getStructure().get(0);
        assertThat(parent.getChildren()).hasSize(1);
        assertThat(parent.getChildren().get(0).getTitle()).isEqualTo("子模块A1");
    }

    @Test
    void parseSystemTreeStructure_shouldExtractEmbeddedJson() {
        // 使用无缩进的文本块，避免 JSON 格式问题
        String json = "这是一些前置文本\n" +
                "{\n" +
                "    \"systemDescription\": \"系统描述\",\n" +
                "    \"structure\": [\n" +
                "        {\n" +
                "            \"title\": \"模块\",\n" +
                "            \"sourceNodeIds\": [],\n" +
                "            \"children\": []\n" +
                "        }\n" +
                "    ]\n" +
                "}\n" +
                "这是一些后置文本";

        SystemTreeStructure result = parser.parseSystemTreeStructure(json);

        assertThat(result).isNotNull();
        assertThat(result.getSystemDescription()).isEqualTo("系统描述");
    }

    @Test
    void parseSystemTreeStructure_shouldThrowExceptionWhenStructureIsEmpty() {
        String json = """
{
    "systemDescription": "系统",
    "structure": []
}
""";

        assertThatThrownBy(() -> parser.parseSystemTreeStructure(json))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("structure is required and cannot be empty");
    }

    @Test
    void parseSystemTreeStructure_shouldThrowExceptionWhenTitleIsMissing() {
        String json = """
{
    "systemDescription": "系统",
    "structure": [
        {
            "sourceNodeIds": [],
            "children": []
        }
    ]
}
""";

        assertThatThrownBy(() -> parser.parseSystemTreeStructure(json))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("title is required");
    }

    @Test
    void parseMergeInstructions_shouldParseValidJson() {
        String json = """
            [
                {
                    "operation": "UPDATE",
                    "targetNodeId": "sys_1",
                    "sourceNodeId": "doc_2"
                },
                {
                    "operation": "CREATE",
                    "sourceNodeId": "doc_3"
                }
            ]
            """;

        List<MergeInstruction> result = parser.parseMergeInstructions(json);

        assertThat(result).hasSize(2);

        MergeInstruction update = result.get(0);
        assertThat(update.getOperation()).isEqualTo("UPDATE");
        assertThat(update.getTargetNodeId()).isEqualTo("sys_1");
        assertThat(update.getSourceNodeId()).isEqualTo("doc_2");

        MergeInstruction create = result.get(1);
        assertThat(create.getOperation()).isEqualTo("CREATE");
        assertThat(create.getSourceNodeId()).isEqualTo("doc_3");
    }

    @Test
    void parseMergeInstructions_shouldExtractEmbeddedJson() {
        String json = """
            以下是合并指令：
            [
                {
                    "operation": "UPDATE",
                    "targetNodeId": "sys_1",
                    "sourceNodeId": "doc_1"
                }
            ]
            以上是所有指令
            """;

        List<MergeInstruction> result = parser.parseMergeInstructions(json);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOperation()).isEqualTo("UPDATE");
    }

    @Test
    void parseMergeInstructions_shouldThrowExceptionWhenOperationIsMissing() {
        String json = """
            [
                {
                    "targetNodeId": "sys_1",
                    "sourceNodeId": "doc_1"
                }
            ]
            """;

        assertThatThrownBy(() -> parser.parseMergeInstructions(json))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("operation is required");
    }

    @Test
    void parseMergeInstructions_shouldThrowExceptionWhenUpdateMissingTargetNodeId() {
        String json = """
            [
                {
                    "operation": "UPDATE",
                    "sourceNodeId": "doc_1"
                }
            ]
            """;

        assertThatThrownBy(() -> parser.parseMergeInstructions(json))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("targetNodeId is required for UPDATE");
    }

    @Test
    void parseMergeInstructions_shouldThrowExceptionWhenUpdateMissingSourceNodeId() {
        String json = """
            [
                {
                    "operation": "UPDATE",
                    "targetNodeId": "sys_1"
                }
            ]
            """;

        assertThatThrownBy(() -> parser.parseMergeInstructions(json))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("sourceNodeId is required for UPDATE");
    }

    @Test
    void parseMergeInstructions_shouldThrowExceptionWhenCreateMissingSourceNodeId() {
        String json = """
            [
                {
                    "operation": "CREATE"
                }
            ]
            """;

        assertThatThrownBy(() -> parser.parseMergeInstructions(json))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("sourceNodeId is required for CREATE");
    }

    @Test
    void parseMergeInstructions_shouldThrowExceptionWhenMoveMissingTargetNodeId() {
        String json = """
            [
                {
                    "operation": "MOVE",
                    "newParentNodeId": "sys_2"
                }
            ]
            """;

        assertThatThrownBy(() -> parser.parseMergeInstructions(json))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("targetNodeId is required for MOVE");
    }

    @Test
    void parseMergeInstructions_shouldThrowExceptionWhenMoveMissingNewParentNodeId() {
        String json = """
            [
                {
                    "operation": "MOVE",
                    "targetNodeId": "sys_1"
                }
            ]
            """;

        assertThatThrownBy(() -> parser.parseMergeInstructions(json))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("newParentNodeId is required for MOVE");
    }

    @Test
    void parseMergeInstructions_shouldThrowExceptionWhenDeleteMissingTargetNodeId() {
        String json = """
            [
                {
                    "operation": "DELETE"
                }
            ]
            """;

        assertThatThrownBy(() -> parser.parseMergeInstructions(json))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("targetNodeId is required for DELETE");
    }

    @Test
    void parseMergeInstructions_shouldThrowExceptionWhenOperationIsInvalid() {
        String json = """
            [
                {
                    "operation": "INVALID_OP",
                    "targetNodeId": "sys_1"
                }
            ]
            """;

        assertThatThrownBy(() -> parser.parseMergeInstructions(json))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Invalid operation: INVALID_OP");
    }

    @Test
    void parseMergeInstructions_shouldAcceptEmptyList() {
        String json = "[]";

        List<MergeInstruction> result = parser.parseMergeInstructions(json);

        assertThat(result).isEmpty();
    }
}
