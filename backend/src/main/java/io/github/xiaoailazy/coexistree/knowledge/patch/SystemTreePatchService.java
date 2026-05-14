package io.github.xiaoailazy.coexistree.knowledge.patch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xiaoailazy.coexistree.featuretree.model.FeatureTreeRoot;
import io.github.xiaoailazy.coexistree.shared.enums.ErrorCode;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * 对功能树 + 更新计划做校验与内存模拟（设计 §7 / §14）。
 */
@Service
public class SystemTreePatchService {

    private final UpdatePlanSimulator simulator;

    public SystemTreePatchService(ObjectMapper objectMapper) {
        this.simulator = new UpdatePlanSimulator(objectMapper);
    }

    public void validateAndSimulate(FeatureTreeRoot root, String planJson) {
        try {
            simulator.simulate(root, planJson);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.JSON_PARSE_ERROR, "Invalid update plan JSON: " + e.getMessage());
        }
    }
}
