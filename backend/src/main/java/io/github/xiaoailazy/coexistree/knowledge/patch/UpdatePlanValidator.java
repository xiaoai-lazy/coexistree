package io.github.xiaoailazy.coexistree.knowledge.patch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xiaoailazy.coexistree.knowledge.plan.SystemTreeUpdatePlan;
import io.github.xiaoailazy.coexistree.shared.enums.ErrorCode;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;

import java.util.Iterator;

/**
 * 校验系统树更新计划 JSON（设计 §7.1、§11）。
 */
public class UpdatePlanValidator {

    private final ObjectMapper objectMapper;

    public UpdatePlanValidator() {
        this(new ObjectMapper());
    }

    public UpdatePlanValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @param json 原始 JSON 字符串
     * @param systemId 调用方系统 ID（预留校验链）
     * @param expectedChangeRecordId 期望的变更记录 ID（预留）
     */
    public void validateJsonPlan(String json, long systemId, long expectedChangeRecordId)
            throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(json);
        JsonNode operations = root.get("operations");
        if (operations != null && operations.isArray()) {
            for (Iterator<JsonNode> it = operations.elements(); it.hasNext(); ) {
                JsonNode opNode = it.next();
                String op = opNode.path("op").asText("");
                if ("MOVE_FEATURE".equals(op)) {
                    throw new BusinessException(
                            ErrorCode.UPDATE_PLAN_OPERATION_NOT_SUPPORTED,
                            "MOVE_FEATURE is not supported in v1 update plans");
                }
            }
        }
        objectMapper.readValue(json, SystemTreeUpdatePlan.class);
    }
}
