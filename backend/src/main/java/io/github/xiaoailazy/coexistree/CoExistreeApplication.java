package io.github.xiaoailazy.coexistree;

import io.github.xiaoailazy.coexistree.config.AppStorageProperties;
import io.github.xiaoailazy.coexistree.config.LlmProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan(basePackageClasses = {AppStorageProperties.class, LlmProperties.class})
public class CoExistreeApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoExistreeApplication.class, args);
    }

}
