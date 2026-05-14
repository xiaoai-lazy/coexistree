package io.github.xiaoailazy.coexistree.knowledge.changeinput;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeInputFingerprintServiceTest {

    @Test
    void stableOrder_sameInput_sameFingerprint() {
        ChangeInputFingerprintService svc = new ChangeInputFingerprintServiceImpl();
        var a =
                List.of(
                        new ChangeInputFingerprintService.Row(2L, "h2", "SUCCESS", true),
                        new ChangeInputFingerprintService.Row(1L, "h1", "SUCCESS", true));
        var b =
                List.of(
                        new ChangeInputFingerprintService.Row(1L, "h1", "SUCCESS", true),
                        new ChangeInputFingerprintService.Row(2L, "h2", "SUCCESS", true));
        assertThat(svc.compute(a)).isEqualTo(svc.compute(b));
    }
}
