package com.example.fams.governance;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers the user activity interceptor alongside the existing security interceptor. */
@Configuration
public class ActivityLoggingConfig implements WebMvcConfigurer {

    private final UserActivityInterceptor interceptor;

    public ActivityLoggingConfig(UserActivityInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/**");
    }
}
