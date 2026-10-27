package com.example.fams.reports;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers the report filter interceptor. */
@Configuration
public class ReportFilterConfig implements WebMvcConfigurer {

    private final ReportFilterInterceptor interceptor;

    public ReportFilterConfig(ReportFilterInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/reports/**");
    }
}
