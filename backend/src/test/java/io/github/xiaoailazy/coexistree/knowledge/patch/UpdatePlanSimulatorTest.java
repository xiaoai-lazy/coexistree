package io.github.xiaoailazy.coexistree.knowledge.patch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xiaoailazy.coexistree.featuretree.io.FeatureTreeJsonMapper;
import io.github.xiaoailazy.coexistree.featuretree.model.FeatureTreeRoot;
import io.github.xiaoailazy.coexistree.shared.enums.ErrorCode;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpdatePlanSimulatorTest {

    @Test
    void validGolden_applies() throws Exception {
        UpdatePlanSimulator sim = new UpdatePlanSimulator(new ObjectMapper());
        String planJson =
                Files.readString(
                        Path.of("src/test/resources/featuretree/golden/update-plan-valid-order.json"));
        String activeTreeJson =
                Files.readString(
                        Path.of("src/test/resources/featuretree/golden/minimal-active-tree.json"));
        FeatureTreeRoot root = new FeatureTreeJsonMapper(new ObjectMapper()).parseRoot(activeTreeJson);
        assertThatCode(() -> sim.simulate(root, planJson)).doesNotThrowAnyException();
    }

    @Test
    void badGolden_fails() throws Exception {
        UpdatePlanSimulator sim = new UpdatePlanSimulator(new ObjectMapper());
        ObjectMapper om = new ObjectMapper();
        FeatureTreeJsonMapper mapper = new FeatureTreeJsonMapper(om);
        FeatureTreeRoot root =
                mapper.parseRoot(
                        Files.readString(
                                Path.of("src/test/resources/featuretree/golden/minimal-active-tree.json")));
        String badPlan =
                Files.readString(
                        Path.of("src/test/resources/featuretree/golden/update-plan-bad-order.json"));
        assertThatThrownBy(() -> sim.simulate(root, badPlan))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UPDATE_PLAN_DEPENDENCY_FAILED));
    }
}
