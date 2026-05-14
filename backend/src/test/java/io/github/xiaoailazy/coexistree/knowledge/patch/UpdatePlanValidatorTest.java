package io.github.xiaoailazy.coexistree.knowledge.patch;

import io.github.xiaoailazy.coexistree.shared.enums.ErrorCode;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpdatePlanValidatorTest {

    @Test
    void rejectsMoveFeature() {
        String json =
                """
                        {"changeRecordId":1,"baseTreeVersion":1,"operations":[
                          {"op":"MOVE_FEATURE","featureNodeId":"x","targetModuleNodeId":"y"}
                        ]}""";
        UpdatePlanValidator v = new UpdatePlanValidator();
        assertThatThrownBy(() -> v.validateJsonPlan(json, 1L, 1L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> {
                            assertThat(ex.getErrorCode())
                                    .isEqualTo(ErrorCode.UPDATE_PLAN_OPERATION_NOT_SUPPORTED);
                            assertThat(ex.getMessage()).contains("MOVE_FEATURE");
                        });
    }
}
