package io.github.xiaoailazy.coexistree.indexer.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xiaoailazy.coexistree.shared.util.JsonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmResponseValidatorTest {

    private LlmResponseValidator validator;

    @BeforeEach
    void setUp() {
        JsonUtils jsonUtils = new JsonUtils(new ObjectMapper());
        validator = new LlmResponseValidator(jsonUtils);
    }

    @Test
    void validateTreeSearch_shouldPassForValidJson() {
        String validJson = """
            {
                "thinking": "用户想了解订单处理流程",
                "node_list": ["node_1", "node_2", "node_3"]
            }
            """;

        LlmResponseValidator.ValidationResult result = validator.validateTreeSearch(validJson);

        assertThat(result.valid()).isTrue();
    }

    @Test
    void validateTreeSearch_shouldPassForMarkdownWrappedJson() {
        String markdownJson = """
            ```json
            {
                "thinking": "分析用户需求",
                "node_list": ["node_1"]
            }
            ```
            """;

        LlmResponseValidator.ValidationResult result = validator.validateTreeSearch(markdownJson);

        assertThat(result.valid()).isTrue();
    }

    @Test
    void validateTreeSearch_shouldFailForMissingThinking() {
        String invalidJson = """
            {
                "node_list": ["node_1"]
            }
            """;

        LlmResponseValidator.ValidationResult result = validator.validateTreeSearch(invalidJson);

        assertThat(result.valid()).isFalse();
        assertThat(result.errorMessage()).contains("thinking");
    }

    @Test
    void validateTreeSearch_shouldFailForInvalidNodeListType() {
        String invalidJson = """
            {
                "thinking": "分析用户需求",
                "node_list": "not_an_array"
            }
            """;

        LlmResponseValidator.ValidationResult result = validator.validateTreeSearch(invalidJson);

        assertThat(result.valid()).isFalse();
        assertThat(result.errorMessage()).contains("node_list");
    }

    @Test
    void validateTreeSearch_shouldFailForNonJsonContent() {
        String nonJson = "This is not JSON at all";

        LlmResponseValidator.ValidationResult result = validator.validateTreeSearch(nonJson);

        assertThat(result.valid()).isFalse();
    }

    @Test
    void validateSystemTreeStructure_shouldPassForValidJson() {
        String validJson = """
            {
                "system_description": "订单管理系统",
                "structure": [
                    {
                        "title": "订单模块",
                        "level": 1,
                        "source_node_ids": ["doc_1"],
                        "children": [
                            {
                                "title": "创建订单",
                                "level": 2
                            }
                        ]
                    }
                ]
            }
            """;

        LlmResponseValidator.ValidationResult result = validator.validateSystemTreeStructure(validJson);

        assertThat(result.valid()).isTrue();
    }

    @Test
    void validateSystemTreeStructure_shouldFailForMissingTitle() {
        String invalidJson = """
            {
                "structure": [
                    {
                        "level": 1
                    }
                ]
            }
            """;

        LlmResponseValidator.ValidationResult result = validator.validateSystemTreeStructure(invalidJson);

        assertThat(result.valid()).isFalse();
        assertThat(result.errorMessage()).contains("title");
    }

    @Test
    void validateSystemTreeStructure_shouldFailForEmptyStructure() {
        String invalidJson = """
            {
                "structure": []
            }
            """;

        LlmResponseValidator.ValidationResult result = validator.validateSystemTreeStructure(invalidJson);

        assertThat(result.valid()).isFalse();
    }

    @Test
    void validateMergeInstructions_shouldPassForValidUpdateOperation() {
        String validJson = """
            [
                {
                    "operation": "UPDATE",
                    "target_node_id": "sys_1",
                    "source_node_id": "doc_1"
                }
            ]
            """;

        LlmResponseValidator.ValidationResult result = validator.validateMergeInstructions(validJson);

        assertThat(result.valid()).isTrue();
    }

    @Test
    void validateMergeInstructions_shouldPassForValidCreateOperation() {
        String validJson = """
            [
                {
                    "operation": "CREATE",
                    "source_node_id": "doc_1"
                }
            ]
            """;

        LlmResponseValidator.ValidationResult result = validator.validateMergeInstructions(validJson);

        assertThat(result.valid()).isTrue();
    }

    @Test
    void validateMergeInstructions_shouldPassForValidMoveOperation() {
        String validJson = """
            [
                {
                    "operation": "MOVE",
                    "target_node_id": "sys_1",
                    "new_parent_node_id": "sys_2"
                }
            ]
            """;

        LlmResponseValidator.ValidationResult result = validator.validateMergeInstructions(validJson);

        assertThat(result.valid()).isTrue();
    }

    @Test
    void validateMergeInstructions_shouldPassForValidDeleteOperation() {
        String validJson = """
            [
                {
                    "operation": "DELETE",
                    "target_node_id": "sys_1"
                }
            ]
            """;

        LlmResponseValidator.ValidationResult result = validator.validateMergeInstructions(validJson);

        assertThat(result.valid()).isTrue();
    }

    @Test
    void validateMergeInstructions_shouldFailForUpdateMissingTargetNodeId() {
        String invalidJson = """
            [
                {
                    "operation": "UPDATE",
                    "source_node_id": "doc_1"
                }
            ]
            """;

        LlmResponseValidator.ValidationResult result = validator.validateMergeInstructions(invalidJson);

        assertThat(result.valid()).isFalse();
        assertThat(result.errorMessage().toLowerCase()).contains("target_node_id");
    }

    @Test
    void validateMergeInstructions_shouldFailForCreateMissingSourceNodeId() {
        String invalidJson = """
            [
                {
                    "operation": "CREATE"
                }
            ]
            """;

        LlmResponseValidator.ValidationResult result = validator.validateMergeInstructions(invalidJson);

        assertThat(result.valid()).isFalse();
        assertThat(result.errorMessage().toLowerCase()).contains("source_node_id");
    }

    @Test
    void validateMergeInstructions_shouldFailForInvalidOperation() {
        String invalidJson = """
            [
                {
                    "operation": "INVALID_OP",
                    "target_node_id": "sys_1"
                }
            ]
            """;

        LlmResponseValidator.ValidationResult result = validator.validateMergeInstructions(invalidJson);

        assertThat(result.valid()).isFalse();
        assertThat(result.errorMessage().toLowerCase()).contains("operation");
    }

    @Test
    void validateMergeInstructions_shouldPassForEmptyArray() {
        String emptyArray = "[]";

        LlmResponseValidator.ValidationResult result = validator.validateMergeInstructions(emptyArray);

        assertThat(result.valid()).isTrue();
    }

    @Test
    void validateMergeInstructions_shouldPassForLowercaseOperations() {
        String validJson = """
            [
                {
                    "operation": "update",
                    "target_node_id": "sys_1",
                    "source_node_id": "doc_1"
                }
            ]
            """;

        LlmResponseValidator.ValidationResult result = validator.validateMergeInstructions(validJson);

        assertThat(result.valid()).isTrue();
    }
}
