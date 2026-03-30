package io.github.xiaoailazy.coexistree.indexer.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.xiaoailazy.coexistree.shared.enums.ErrorCode;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import io.github.xiaoailazy.coexistree.shared.util.JsonUtils;
import io.github.xiaoailazy.coexistree.knowledge.model.MergeInstruction;
import io.github.xiaoailazy.coexistree.knowledge.model.SystemTreeStructure;
import io.github.xiaoailazy.coexistree.indexer.model.TreeSearchResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class LlmResponseParser {

    private final JsonUtils jsonUtils;

    public LlmResponseParser(JsonUtils jsonUtils) {
        this.jsonUtils = jsonUtils;
    }

    public TreeSearchResult parseTreeSearch(String content) {
        try {
            // 尝试直接解析
            JsonNode root = jsonUtils.objectMapper().readTree(content);
            return buildTreeSearchResult(root);
        } catch (Exception e) {
            // 尝试提取嵌入的JSON
            try {
                String jsonStr = extractJson(content);
                if (jsonStr != null) {
                    JsonNode root = jsonUtils.objectMapper().readTree(jsonStr);
                    return buildTreeSearchResult(root);
                }
            } catch (Exception ex) {
                // 解析失败
            }
            
            TreeSearchResult result = new TreeSearchResult();
            result.setThinking("parse_error");
            result.setNodeList(List.of());
            return result;
        }
    }

    private String extractJson(String content) {
        // 使用正则表达式提取JSON
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\{[^}]*node_list[^}]*\\}", java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(0);
        }
        return null;
    }

    private TreeSearchResult buildTreeSearchResult(JsonNode root) {
        TreeSearchResult result = new TreeSearchResult();
        result.setThinking(root.path("thinking").asText(""));
        List<String> nodeList = new ArrayList<>();
        if (root.has("node_list") && root.get("node_list").isArray()) {
            for (JsonNode item : root.get("node_list")) {
                nodeList.add(item.asText());
            }
        }
        result.setNodeList(nodeList);
        return result;
    }

    /**
     * 解析 LLM 输出的系统树结构（用于基线合并）
     * 
     * @param json LLM 返回的 JSON 字符串
     * @return SystemTreeStructure 对象
     * @throws BusinessException 如果 JSON 格式错误或缺少必填字段
     */
    public SystemTreeStructure parseSystemTreeStructure(String json) {
        try {
            // 尝试直接解析
            SystemTreeStructure structure = jsonUtils.fromJson(json, SystemTreeStructure.class);
            validateSystemTreeStructure(structure);
            return structure;
        } catch (BusinessException e) {
            // 如果是验证错误，直接抛出
            if (e.getMessage() != null && (e.getMessage().contains("structure is required")
                    || e.getMessage().contains("title is required"))) {
                throw e;
            }
            // 否则尝试提取嵌入的 JSON
            try {
                String extractedJson = extractJsonBlock(json);
                if (extractedJson != null) {
                    SystemTreeStructure structure = jsonUtils.fromJson(extractedJson, SystemTreeStructure.class);
                    validateSystemTreeStructure(structure);
                    return structure;
                }
            } catch (BusinessException ex) {
                // 如果提取后还是验证错误，抛出原始错误
                if (ex.getMessage() != null && (ex.getMessage().contains("structure is required")
                        || ex.getMessage().contains("title is required"))) {
                    throw ex;
                }
                throw new BusinessException(ErrorCode.TREE_FILE_INVALID,
                    "Failed to parse SystemTreeStructure from LLM response: " + ex.getMessage());
            } catch (Exception ex) {
                throw new BusinessException(ErrorCode.TREE_FILE_INVALID,
                    "Failed to parse SystemTreeStructure from LLM response: " + ex.getMessage());
            }
            throw new BusinessException(ErrorCode.TREE_FILE_INVALID,
                "Failed to parse SystemTreeStructure from LLM response: " + e.getMessage());
        }
    }

    /**
     * 解析 LLM 输出的合并指令列表（用于变更合并）
     * 
     * @param json LLM 返回的 JSON 字符串
     * @return MergeInstruction 列表
     * @throws BusinessException 如果 JSON 格式错误或缺少必填字段
     */
    public List<MergeInstruction> parseMergeInstructions(String json) {
        try {
            // 尝试直接解析
            List<MergeInstruction> instructions = jsonUtils.objectMapper().readValue(
                json, new TypeReference<List<MergeInstruction>>() {});
            validateMergeInstructions(instructions);
            return instructions;
        } catch (Exception e) {
            // 尝试提取嵌入的 JSON
            try {
                String extractedJson = extractJsonBlock(json);
                if (extractedJson != null) {
                    List<MergeInstruction> instructions = jsonUtils.objectMapper().readValue(
                        extractedJson, new TypeReference<List<MergeInstruction>>() {});
                    validateMergeInstructions(instructions);
                    return instructions;
                }
            } catch (Exception ex) {
                throw new BusinessException(ErrorCode.TREE_FILE_INVALID, 
                    "Failed to parse MergeInstructions from LLM response: " + ex.getMessage());
            }
            throw new BusinessException(ErrorCode.TREE_FILE_INVALID, 
                "Failed to parse MergeInstructions from LLM response: " + e.getMessage());
        }
    }

    /**
     * 校验 SystemTreeStructure 的必填字段
     */
    private void validateSystemTreeStructure(SystemTreeStructure structure) {
        if (structure == null) {
            throw new BusinessException(ErrorCode.TREE_FILE_INVALID,
                "SystemTreeStructure is null");
        }
        if (structure.getStructure() == null || structure.getStructure().isEmpty()) {
            throw new BusinessException(ErrorCode.TREE_FILE_INVALID,
                "structure is required and cannot be empty");
        }
        // 递归校验每个节点
        for (var node : structure.getStructure()) {
            validateLlmTreeNode(node);
        }
    }

    /**
     * 递归校验 LlmTreeNode 的必填字段
     */
    private void validateLlmTreeNode(io.github.xiaoailazy.coexistree.knowledge.model.LlmTreeNode node) {
        if (node == null) {
            throw new BusinessException(ErrorCode.TREE_FILE_INVALID,
                "LlmTreeNode is null");
        }
        if (node.getTitle() == null || node.getTitle().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.TREE_FILE_INVALID,
                "title is required");
        }
        // 递归校验子节点
        if (node.getChildren() != null) {
            for (var child : node.getChildren()) {
                validateLlmTreeNode(child);
            }
        }
    }

    /**
     * 校验 MergeInstruction 列表的必填字段
     */
    private void validateMergeInstructions(List<MergeInstruction> instructions) {
        if (instructions == null) {
            throw new BusinessException(ErrorCode.TREE_FILE_INVALID, 
                "MergeInstructions list is null");
        }
        for (MergeInstruction instruction : instructions) {
            validateMergeInstruction(instruction);
        }
    }

    /**
     * 校验单个 MergeInstruction 的必填字段
     */
    private void validateMergeInstruction(MergeInstruction instruction) {
        if (instruction == null) {
            throw new BusinessException(ErrorCode.TREE_FILE_INVALID, 
                "MergeInstruction is null");
        }
        if (instruction.getOperation() == null || instruction.getOperation().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.TREE_FILE_INVALID, 
                "MergeInstruction.operation is required");
        }
        
        String operation = instruction.getOperation().toUpperCase();
        switch (operation) {
            case "UPDATE":
                if (instruction.getTargetNodeId() == null || instruction.getTargetNodeId().trim().isEmpty()) {
                    throw new BusinessException(ErrorCode.TREE_FILE_INVALID, 
                        "MergeInstruction.targetNodeId is required for UPDATE operation");
                }
                if (instruction.getSourceNodeId() == null || instruction.getSourceNodeId().trim().isEmpty()) {
                    throw new BusinessException(ErrorCode.TREE_FILE_INVALID, 
                        "MergeInstruction.sourceNodeId is required for UPDATE operation");
                }
                break;
            case "CREATE":
                if (instruction.getSourceNodeId() == null || instruction.getSourceNodeId().trim().isEmpty()) {
                    throw new BusinessException(ErrorCode.TREE_FILE_INVALID, 
                        "MergeInstruction.sourceNodeId is required for CREATE operation");
                }
                break;
            case "MOVE":
                if (instruction.getTargetNodeId() == null || instruction.getTargetNodeId().trim().isEmpty()) {
                    throw new BusinessException(ErrorCode.TREE_FILE_INVALID, 
                        "MergeInstruction.targetNodeId is required for MOVE operation");
                }
                if (instruction.getNewParentNodeId() == null || instruction.getNewParentNodeId().trim().isEmpty()) {
                    throw new BusinessException(ErrorCode.TREE_FILE_INVALID, 
                        "MergeInstruction.newParentNodeId is required for MOVE operation");
                }
                break;
            case "DELETE":
                if (instruction.getTargetNodeId() == null || instruction.getTargetNodeId().trim().isEmpty()) {
                    throw new BusinessException(ErrorCode.TREE_FILE_INVALID, 
                        "MergeInstruction.targetNodeId is required for DELETE operation");
                }
                break;
            default:
                throw new BusinessException(ErrorCode.TREE_FILE_INVALID, 
                    "Invalid operation: " + instruction.getOperation() + 
                    ". Must be one of: UPDATE, CREATE, MOVE, DELETE");
        }
    }

    /**
     * 提取嵌入在文本中的 JSON 块
     * 支持提取 {...} 或 [...] 格式的 JSON
     * 使用括号匹配来找到最外层的 JSON 结构
     */
    private String extractJsonBlock(String content) {
        // 找到第一个 { 或 [
        int firstBrace = content.indexOf('{');
        int firstBracket = content.indexOf('[');

        if (firstBrace == -1 && firstBracket == -1) {
            return null;
        }

        // 确定起始位置和对应的结束字符
        int start;
        char openChar, closeChar;
        if (firstBrace != -1 && (firstBracket == -1 || firstBrace < firstBracket)) {
            // 对象格式 {...}
            start = firstBrace;
            openChar = '{';
            closeChar = '}';
        } else {
            // 数组格式 [...]
            start = firstBracket;
            openChar = '[';
            closeChar = ']';
        }

        // 使用计数器找到匹配的闭合括号
        int count = 0;
        int end = -1;
        for (int i = start; i < content.length(); i++) {
            char c = content.charAt(i);
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
            return content.substring(start, end + 1);
        }

        return null;
    }
}

