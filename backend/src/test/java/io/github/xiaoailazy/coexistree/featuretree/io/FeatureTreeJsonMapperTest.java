package io.github.xiaoailazy.coexistree.featuretree.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xiaoailazy.coexistree.featuretree.model.FeatureTreeNodeType;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureTreeJsonMapperTest {

    @Test
    void parseGoldenMinimalInit() throws Exception {
        String json =
                Files.readString(
                        Path.of("src/test/resources/featuretree/golden/minimal-init-tree.json"));
        FeatureTreeJsonMapper mapper = new FeatureTreeJsonMapper(new ObjectMapper());
        assertThat(mapper.parseRoot(json).getType()).isEqualTo(FeatureTreeNodeType.SYSTEM_ROOT);
    }
}
