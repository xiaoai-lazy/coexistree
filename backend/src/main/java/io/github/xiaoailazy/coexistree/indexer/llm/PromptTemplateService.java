package io.github.xiaoailazy.coexistree.indexer.llm;

import io.github.xiaoailazy.coexistree.shared.util.JsonUtils;
import io.github.xiaoailazy.coexistree.chat.entity.MessageEntity;
import io.github.xiaoailazy.coexistree.indexer.model.TreeNode;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PromptTemplateService {

    private final JsonUtils jsonUtils;

    public PromptTemplateService(JsonUtils jsonUtils) {
        this.jsonUtils = jsonUtils;
    }

    public String buildTreeSearchPrompt(String query, List<TreeNode> structure) {
        return """
                You are given a question and a tree structure of a document.
                Each node contains a node id, node title, and a corresponding summary.
                Your task is to find all nodes that are likely to contain the answer to the question.

                Question: %s

                Document tree structure:
                %s

                Please reply in the following JSON format:
                {
                    "thinking": "<Your thinking process on which nodes are relevant to the question>",
                    "node_list": ["node_id_1", "node_id_2", ..., "node_id_n"]
                }
                Directly return the final JSON structure. Do not output anything else."""
                .formatted(query, jsonUtils.toJson(structure));
    }

    public String buildAnswerPrompt(String query, String relevantContent) {
        return "Question: " + query + "\nContext:\n" + relevantContent;
    }

    public String buildTitleGenerationPrompt(List<MessageEntity> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert in generating concise titles for conversations.\n");
        sb.append("Please generate a very short title (max 10 Chinese characters) that accurately summarizes the main topic.\n\n");
        sb.append("Conversation content:\n");

        for (MessageEntity msg : messages) {
            sb.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
        }

        sb.append("\nReturn only the title, no quotes or extra text.");
        return sb.toString();
    }

    public String buildBaselinePrompt(String systemName, String systemCode, String docTreeJson) {
        return """
                你是一个系统架构分析专家。你的任务是分析文档树，提取系统的逻辑架构，生成系统知识树。

                ## 系统信息
                - 系统名称：%s
                - 系统代码：%s

                ## 文档树（文档章节结构）
                %s

                ## 任务
                分析文档树的内容，提取系统的核心模块和子模块，生成系统知识树。

                ## 系统树层级规范
                - **根节点**：系统名称（如"支付系统"）
                - **一级节点**：核心模块（如"支付流程模块"、"风控模块"、"对账模块"）
                - **二级节点**：子模块或功能点（如"微信支付"、"支付宝支付"）
                - **三级及以下**：更细粒度的功能或流程

                ## 输出格式（JSON）
                {
                  "systemDescription": "系统的整体描述（1-2句话）",
                  "structure": [
                    {
                      "title": "支付流程模块",
                      "level": 1,
                      "sourceNodeIds": ["0001", "0002", "0003"],
                      "children": [
                        {
                          "title": "微信支付",
                          "level": 2,
                          "sourceNodeIds": ["0002"],
                          "children": []
                        }
                      ]
                    }
                  ]
                }

                ## 规则
                1. 系统树的层级应反映系统的逻辑架构，而非文档的章节结构
                2. 合并文档中分散的相关内容到同一模块下
                3. 每个节点必须提供 sourceNodeIds（标明内容来源）
                4. level 字段表示层级：1=系统级，2=模块级，3=功能级，4=细节级，5=参数级
                5. 保持层级清晰，避免过深（最多 5 层）
                6. 不需要生成 summary 字段，由系统自动生成"""
                .formatted(systemName, systemCode, docTreeJson);
    }

    public String buildMergePrompt(String systemTreeJson, String docTreeJson) {
        return """
                你是一个知识树合并专家。你的任务是分析系统知识树和变更文档树，生成合并指令。

                ## 系统知识树（当前状态）
                %s

                ## 变更文档树（新内容）
                %s

                ## ID 格式说明
                - 系统树节点 ID：格式如 "DEV_01_1", "DEV_01_2"（带有系统代码前缀和下划线）
                - 文档树节点 ID：格式如 "0001", "0002"（纯数字）
                **重要**：targetNodeId 和 newParentNodeId 必须引用系统树节点 ID！

                ## 任务
                分析变更文档树中的每个节点，判断它应该：
                1. UPDATE：更新系统树中的某个现有节点（标题相同或语义相似）
                2. CREATE：作为新节点添加到系统树（找不到对应节点）

                ## 输出格式（JSON）
                [
                  {
                    "operation": "UPDATE",
                    "targetNodeId": "DEV_01_1",    // 必须是系统树中存在的节点 ID
                    "sourceNodeId": "0003"       // 文档树中的节点 ID
                  },
                  {
                    "operation": "CREATE",
                    "sourceNodeId": "0005",       // 文档树中的节点 ID
                    "newParentNodeId": "DEV_01_1" // **必须**：系统树中已存在的节点 ID
                  }
                ]

                ## 约束规则（非常重要）
                1. **所有 CREATE 操作必须有 newParentNodeId**：不能省略，不能为空
                2. **newParentNodeId 必须是系统树节点 ID**：格式为 "{systemCode}_{数字}"
                3. **禁止生成根级别节点**：所有新节点必须挂载到现有系统节点下
                4. **操作顺序至关重要**：
                   - 如果变更文档中有父子层级关系
                   - 父节点和子节点都需要创建时
                   - 父节点的 CREATE 指令必须在子节点的 CREATE 指令之前
                5. **优先匹配标题完全相同的节点**
                6. **标题不同但语义相似也应匹配为 UPDATE**
                7. **只有确实是新增内容时才使用 CREATE**
                8. **每个文档树节点只能对应一条指令**

                ## 示例场景
                假设变更文档中有两个新节点：
                - 0005: "订单模块"（一级模块）
                - 0006: "订单查询"（订单模块下的功能）

                正确的指令顺序（假设 "DEV_01_3" 是系统树中的模块节点）：
                [
                  {
                    "operation": "CREATE",
                    "sourceNodeId": "0005",
                    "newParentNodeId": "DEV_01_3"  // 先创建父节点
                  },
                  // ... 这里可以插入其他指令 ...
                  {
                    "operation": "CREATE",
                    "sourceNodeId": "0006",
                    "newParentNodeId": "DEV_01_3"  // 再创建子节点，挂载到同一父节点
                  }
                ]

                注意：子节点的父节点应该是系统树中已存在的节点，而不是刚刚创建的节点。"""
                .formatted(systemTreeJson, docTreeJson);
    }
}

