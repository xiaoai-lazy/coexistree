package io.github.xiaoailazy.coexistree.indexer.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import io.github.xiaoailazy.coexistree.shared.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * LLM 响应结构验证器
 * 使用 JSON Schema 验证 LLM 返回的数据结构是否符合预期
 */
@Slf4j
@Component
public class LlmResponseValidator {

    private final ObjectMapper objectMapper;
    private final Map<String, JsonSchema> schemaCache = new HashMap<>();

    public LlmResponseValidator(JsonUtils jsonUtils) {
        this.objectMapper = jsonUtils.objectMapper();
        initializeSchemas();
    }

    /**
     * 验证结果
     */
    public record ValidationResult(boolean valid, String errorMessage, Set<ValidationMessage> errors) {
        public static ValidationResult success() {
            return new ValidationResult(true, null, null);
        }

        public static ValidationResult failure(String message, Set<ValidationMessage> errors) {
            return new ValidationResult(false, message, errors);
        }
    }

    /**
     * 验证树搜索结果结构
     */
    public ValidationResult validateTreeSearch(String content) {
        return validate(content, "treeSearch");
    }

    /**
     * 验证系统树结构
     */
    public ValidationResult validateSystemTreeStructure(String content) {
        return validate(content, "systemTreeStructure");
    }

    /**
     * 验证合并指令结构
     */
    public ValidationResult validateMergeInstructions(String content) {
        return validate(content, "mergeInstructions");
    }

    /**
     * 通用验证方法
     */
    private ValidationResult validate(String content, String schemaName) {
        try {
            JsonSchema schema = schemaCache.get(schemaName);
            if (schema == null) {
                log.warn("Schema not found: {}", schemaName);
                return ValidationResult.success(); // 没有 schema 则跳过验证
            }

            // 先尝试提取 JSON 块（处理 LLM 返回的 markdown 格式）
            String jsonContent = extractJsonBlock(content);
            if (jsonContent == null) {
                return ValidationResult.failure("No JSON content found in response", null);
            }

            JsonNode jsonNode = objectMapper.readTree(jsonContent);
            Set<ValidationMessage> errors = schema.validate(jsonNode);

            if (errors.isEmpty()) {
                return ValidationResult.success();
            }

            StringBuilder errorMsg = new StringBuilder("Validation failed:");
            for (ValidationMessage error : errors) {
                errorMsg.append("\n- ").append(error.getMessage());
            }
            return ValidationResult.failure(errorMsg.toString(), errors);

        } catch (Exception e) {
            log.warn("Validation error for schema {}: {}", schemaName, e.getMessage());
            return ValidationResult.failure("Parse error: " + e.getMessage(), null);
        }
    }

    /**
     * 初始化 JSON Schemas
     */
    private void initializeSchemas() {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

        // TreeSearch 结果 schema
        String treeSearchSchema = """
            {
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                "type": "object",
                "required": ["thinking", "node_list"],
                "properties": {
                    "thinking": {
                        "type": "string",
                        "description": "LLM 的思考过程"
                    },
                    "node_list": {
                        "type": "array",
                        "items": {
                            "type": "string"
                        },
                        "description": "节点 ID 列表"
                    }
                }
            }
            """;
        schemaCache.put("treeSearch", factory.getSchema(treeSearchSchema));

        // SystemTreeStructure schema
        String systemTreeSchema = """
            {
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                "type": "object",
                "required": ["structure"],
                "properties": {
                    "system_description": {
                        "type": "string",
                        "description": "系统描述"
                    },
                    "structure": {
                        "type": "array",
                        "minItems": 1,
                        "items": {
                            "$ref": "#/$defs/llmTreeNode"
                        },
                        "description": "树节点结构"
                    }
                },
                "$defs": {
                    "llmTreeNode": {
                        "type": "object",
                        "required": ["title"],
                        "properties": {
                            "title": {
                                "type": "string",
                                "minLength": 1,
                                "description": "节点标题"
                            },
                            "level": {
                                "type": "integer",
                                "minimum": 1,
                                "maximum": 6,
                                "description": "节点层级"
                            },
                            "source_node_ids": {
                                "type": "array",
                                "items": {
                                    "type": "string"
                                },
                                "description": "来源节点 ID 列表"
                            },
                            "children": {
                                "type": "array",
                                "items": {
                                    "$ref": "#/$defs/llmTreeNode"
                                },
                                "description": "子节点列表"
                            }
                        }
                    }
                }
            }
            """;
        schemaCache.put("systemTreeStructure", factory.getSchema(systemTreeSchema));

        // MergeInstructions schema
        String mergeInstructionsSchema = """
            {
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                "type": "array",
                "items": {
                    "type": "object",
                    "required": ["operation"],
                    "properties": {
                        "operation": {
                            "type": "string",
                            "enum": ["UPDATE", "CREATE", "MOVE", "DELETE", "update", "create", "move", "delete"],
                            "description": "操作类型"
                        },
                        "target_node_id": {
                            "type": "string",
                            "description": "目标节点 ID"
                        },
                        "source_node_id": {
                            "type": "string",
                            "description": "源节点 ID"
                        },
                        "new_parent_node_id": {
                            "type": "string",
                            "description": "新父节点 ID"
                        }
                    },
                    "allOf": [
                        {
                            "if": {
                                "properties": {
                                    "operation": { "enum": ["UPDATE", "update"] }
                                }
                            },
                            "then": {
                                "required": ["target_node_id", "source_node_id"]
                            }
                        },
                        {
                            "if": {
                                "properties": {
                                    "operation": { "enum": ["CREATE", "create"] }
                                }
                            },
                            "then": {
                                "required": ["source_node_id"]
                            }
                        },
                        {
                            "if": {
                                "properties": {
                                    "operation": { "enum": ["MOVE", "move"] }
                                }
                            },
                            "then": {
                                "required": ["target_node_id", "new_parent_node_id"]
                            }
                        },
                        {
                            "if": {
                                "properties": {
                                    "operation": { "enum": ["DELETE", "delete"] }
                                }
                            },
                            "then": {
                                "required": ["target_node_id"]
                            }
                        }
                    ]
                }
            }
            """;
        schemaCache.put("mergeInstructions", factory.getSchema(mergeInstructionsSchema));
    }

    /**
     * 提取 JSON 块（支持 markdown 代码块和普通 JSON）
     */
    private String extractJsonBlock(String content) {
        if (content == null || content.trim().isEmpty()) {
            return null;
        }

        String trimmed = content.trim();

        // 尝试提取 markdown 代码块
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start > 0 && end > start) {
                return trimmed.substring(start + 1, end).trim();
            }
        }

        // 查找 JSON 对象或数组的开始
        int firstBrace = trimmed.indexOf('{');
        int firstBracket = trimmed.indexOf('[');

        if (firstBrace == -1 && firstBracket == -1) {
            return null;
        }

        int start;
        char openChar, closeChar;
        if (firstBrace != -1 && (firstBracket == -1 || firstBrace < firstBracket)) {
            start = firstBrace;
            openChar = '{';
            closeChar = '}';
        } else {
            start = firstBracket;
            openChar = '[';
            closeChar = ']';
        }

        // 使用计数器找到匹配的闭合括号
        int count = 0;
        int end = -1;
        for (int i = start; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == openChar) {
                count++;
            } else if (c == closeChar) {
                count--;
                if (count == 0) {
                    end = i;
                    break;
                }
            }
        }

        if (end != -1) {
            return trimmed.substring(start, end + 1);
        }

        return null;
    }
}
