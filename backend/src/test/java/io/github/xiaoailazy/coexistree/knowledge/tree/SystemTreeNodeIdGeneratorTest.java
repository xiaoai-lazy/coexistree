package io.github.xiaoailazy.coexistree.knowledge.tree;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SystemTreeNodeIdGeneratorTest {

    @Test
    void constructor_validSystemCode_shouldCreateGenerator() {
        SystemTreeNodeIdGenerator generator = new SystemTreeNodeIdGenerator("PAY");
        assertThat(generator).isNotNull();
    }

    @Test
    void constructor_nullSystemCode_shouldThrowException() {
        assertThatThrownBy(() -> new SystemTreeNodeIdGenerator(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("systemCode cannot be null or empty");
    }

    @Test
    void constructor_emptySystemCode_shouldThrowException() {
        assertThatThrownBy(() -> new SystemTreeNodeIdGenerator(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("systemCode cannot be null or empty");
    }

    @Test
    void constructor_blankSystemCode_shouldThrowException() {
        assertThatThrownBy(() -> new SystemTreeNodeIdGenerator("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("systemCode cannot be null or empty");
    }

    @Test
    void nextId_firstCall_shouldReturnSystemCode_1() {
        SystemTreeNodeIdGenerator generator = new SystemTreeNodeIdGenerator("PAY");
        String id = generator.nextId();
        assertThat(id).isEqualTo("PAY_1");
    }

    @Test
    void nextId_multipleCalls_shouldIncrementSequentially() {
        SystemTreeNodeIdGenerator generator = new SystemTreeNodeIdGenerator("ORDER");
        assertThat(generator.nextId()).isEqualTo("ORDER_1");
        assertThat(generator.nextId()).isEqualTo("ORDER_2");
        assertThat(generator.nextId()).isEqualTo("ORDER_3");
        assertThat(generator.nextId()).isEqualTo("ORDER_4");
        assertThat(generator.nextId()).isEqualTo("ORDER_5");
    }

    @ParameterizedTest
    @CsvSource({
            "DEV, DEV_1",
            "PAY_SYSTEM, PAY_SYSTEM_1",
            "A, A_1",
            "SYSTEM_01, SYSTEM_01_1"
    })
    void nextId_variousSystemCodes_shouldHaveCorrectFormat(String systemCode, String expectedFirstId) {
        SystemTreeNodeIdGenerator generator = new SystemTreeNodeIdGenerator(systemCode);
        String id = generator.nextId();
        assertThat(id).isEqualTo(expectedFirstId);
    }

    @Test
    void nextId_manyCalls_shouldMaintainCorrectFormat() {
        SystemTreeNodeIdGenerator generator = new SystemTreeNodeIdGenerator("TEST");
        for (int i = 1; i <= 1000; i++) {
            String id = generator.nextId();
            assertThat(id).isEqualTo("TEST_" + i);
        }
    }

    @Test
    void reset_afterSomeCalls_shouldResetTo1() {
        SystemTreeNodeIdGenerator generator = new SystemTreeNodeIdGenerator("USER");
        generator.nextId();
        generator.nextId();
        generator.nextId();
        generator.reset();
        String idAfterReset = generator.nextId();
        assertThat(idAfterReset).isEqualTo("USER_1");
    }

    @Test
    void setCounter_validValue_shouldSetToSpecifiedValue() {
        SystemTreeNodeIdGenerator generator = new SystemTreeNodeIdGenerator("PROD");
        generator.setCounter(100);
        assertThat(generator.nextId()).isEqualTo("PROD_100");
        assertThat(generator.nextId()).isEqualTo("PROD_101");
    }

    @Test
    void setCounter_value1_shouldWorkCorrectly() {
        SystemTreeNodeIdGenerator generator = new SystemTreeNodeIdGenerator("PROD");
        generator.nextId();
        generator.setCounter(1);
        assertThat(generator.nextId()).isEqualTo("PROD_1");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -5, -100})
    void setCounter_invalidValue_shouldThrowException(int invalidValue) {
        SystemTreeNodeIdGenerator generator = new SystemTreeNodeIdGenerator("TEST");
        assertThatThrownBy(() -> generator.setCounter(invalidValue))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("counter must be >= 1");
    }

    @Test
    void setCounter_forRecoveryScenario_shouldWorkCorrectly() {
        SystemTreeNodeIdGenerator generator = new SystemTreeNodeIdGenerator("RECOVERY");
        generator.setCounter(51);
        assertThat(generator.nextId()).isEqualTo("RECOVERY_51");
        assertThat(generator.nextId()).isEqualTo("RECOVERY_52");
    }

    @Test
    void multipleGeneratorsDifferentSystemCodes_shouldBeIndependent() {
        SystemTreeNodeIdGenerator generatorA = new SystemTreeNodeIdGenerator("SYS_A");
        SystemTreeNodeIdGenerator generatorB = new SystemTreeNodeIdGenerator("SYS_B");
        assertThat(generatorA.nextId()).isEqualTo("SYS_A_1");
        assertThat(generatorB.nextId()).isEqualTo("SYS_B_1");
        assertThat(generatorA.nextId()).isEqualTo("SYS_A_2");
        assertThat(generatorB.nextId()).isEqualTo("SYS_B_2");
    }

    @Test
    void getCurrentCounter_shouldReturnNextValueToBeUsed() {
        SystemTreeNodeIdGenerator generator = new SystemTreeNodeIdGenerator("COUNTER");
        assertThat(generator.getCurrentCounter()).isEqualTo(1);
        generator.nextId();
        assertThat(generator.getCurrentCounter()).isEqualTo(2);
        generator.nextId();
        assertThat(generator.getCurrentCounter()).isEqualTo(3);
    }
}
