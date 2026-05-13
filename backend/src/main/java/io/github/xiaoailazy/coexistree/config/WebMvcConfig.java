package io.github.xiaoailazy.coexistree.config;

import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration with SecurityContext propagation for async dispatch.
 *
 * When a controller returns SseEmitter, Spring MVC dispatches the async result
 * to a different thread. Without proper configuration, the SecurityContext set
 * by JwtAuthenticationFilter on the initial request thread is NOT visible on
 * the async dispatch thread, causing 401 Unauthorized.
 *
 * This configuration wraps the MVC async executor with
 * DelegatingSecurityContextAsyncTaskExecutor, which propagates the
 * SecurityContext from the request thread to the async dispatch thread.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        SimpleAsyncTaskExecutor underlying = new SimpleAsyncTaskExecutor("mvc-async-");
        DelegatingSecurityContextAsyncTaskExecutor executor =
                new DelegatingSecurityContextAsyncTaskExecutor(underlying);

        configurer.setDefaultTimeout(60_000L)
                .setTaskExecutor(executor);
    }
}
