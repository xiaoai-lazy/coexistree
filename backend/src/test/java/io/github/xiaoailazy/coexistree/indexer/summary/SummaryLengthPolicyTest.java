package io.github.xiaoailazy.coexistree.indexer.summary;

import io.github.xiaoailazy.coexistree.indexer.summary.SummaryLengthPolicy.SummaryLengthConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SummaryLengthPolicyTest {

    @Test
    void getConfig_level1_shouldReturnCorrectConfig() {
        SummaryLengthConfig config = SummaryLengthPolicy.getConfig(1);

        assertThat(config.getLevel()).isEqualTo(1);
        assertThat(config.getMinLength()).isEqualTo(300);
        assertThat(config.getMaxLength()).isEqualTo(400);
        assertThat(config.getDescription()).isEqualTo("系统概述，需全面覆盖");
    }

    @Test
    void getConfig_level2_shouldReturnCorrectConfig() {
        SummaryLengthConfig config = SummaryLengthPolicy.getConfig(2);

        assertThat(config.getLevel()).isEqualTo(2);
        assertThat(config.getMinLength()).isEqualTo(250);
        assertThat(config.getMaxLength()).isEqualTo(350);
        assertThat(config.getDescription()).isEqualTo("模块说明，包含主要功能");
    }

    @Test
    void getConfig_level3_shouldReturnCorrectConfig() {
        SummaryLengthConfig config = SummaryLengthPolicy.getConfig(3);

        assertThat(config.getLevel()).isEqualTo(3);
        assertThat(config.getMinLength()).isEqualTo(200);
        assertThat(config.getMaxLength()).isEqualTo(250);
        assertThat(config.getDescription()).isEqualTo("功能细节，核心流程");
    }

    @Test
    void getConfig_level4_shouldReturnCorrectConfig() {
        SummaryLengthConfig config = SummaryLengthPolicy.getConfig(4);

        assertThat(config.getLevel()).isEqualTo(4);
        assertThat(config.getMinLength()).isEqualTo(150);
        assertThat(config.getMaxLength()).isEqualTo(200);
        assertThat(config.getDescription()).isEqualTo("具体步骤，实现细节");
    }

    @Test
    void getConfig_level5_shouldReturnCorrectConfig() {
        SummaryLengthConfig config = SummaryLengthPolicy.getConfig(5);

        assertThat(config.getLevel()).isEqualTo(5);
        assertThat(config.getMinLength()).isEqualTo(100);
        assertThat(config.getMaxLength()).isEqualTo(150);
        assertThat(config.getDescription()).isEqualTo("参数配置，格式说明");
    }

    @ParameterizedTest
    @ValueSource(ints = {6, 7, 10, 100})
    void getConfig_levelGreaterThan5_shouldReturnLevel5Config(int level) {
        SummaryLengthConfig config = SummaryLengthPolicy.getConfig(level);

        // 超出范围的层级应返回 L5 的配置
        assertThat(config.getLevel()).isEqualTo(5);
        assertThat(config.getMinLength()).isEqualTo(100);
        assertThat(config.getMaxLength()).isEqualTo(150);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -5, -100})
    void getConfig_levelLessThan1_shouldReturnLevel5Config(int level) {
        SummaryLengthConfig config = SummaryLengthPolicy.getConfig(level);

        // 小于 1 的层级应返回 L5 的配置（最严格）
        assertThat(config.getLevel()).isEqualTo(5);
        assertThat(config.getMinLength()).isEqualTo(100);
        assertThat(config.getMaxLength()).isEqualTo(150);
    }

    @Test
    void summaryLengthConfig_toString_shouldContainLevelAndRange() {
        SummaryLengthConfig config = SummaryLengthPolicy.getConfig(1);

        String result = config.toString();

        assertThat(result).contains("L1:");
        assertThat(result).contains("300-400");
        assertThat(result).contains("系统概述");
    }

    @Test
    void getConfig_allLevels_shouldHaveValidLengthRange() {
        for (int level = 1; level <= 5; level++) {
            SummaryLengthConfig config = SummaryLengthPolicy.getConfig(level);

            assertThat(config.getMinLength()).isPositive();
            assertThat(config.getMaxLength()).isPositive();
            assertThat(config.getMaxLength()).isGreaterThanOrEqualTo(config.getMinLength());
            assertThat(config.getDescription()).isNotEmpty();
        }
    }
}
