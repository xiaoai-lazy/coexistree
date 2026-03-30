package io.github.xiaoailazy.coexistree.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public record AppStorageProperties(
        String docRoot,
        String treeRoot,
        String systemTreeRoot
) {
}

