package com.example.project_flow.admin.web;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AdminWebConfig implements WebMvcConfigurer {

    public static final long MAX_FILE_BYTES = 50L * 1024 * 1024;
    public static final int MAX_FILES_PER_UPLOAD = 10;

    private final AdminAuthInterceptor adminAuth;

    public AdminWebConfig(AdminAuthInterceptor adminAuth) {
        this.adminAuth = adminAuth;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuth).addPathPatterns("/api/admin/**");
    }

    /**
     * Raises Spring Boot's 1 MB upload default for submission files. Declared here so
     * application.properties stays untouched; no existing endpoint accepts uploads.
     */
    @Bean
    public MultipartConfigElement multipartConfigElement() {
        long maxRequest = MAX_FILE_BYTES * MAX_FILES_PER_UPLOAD + 1024 * 1024;
        return new MultipartConfigElement("", MAX_FILE_BYTES, maxRequest, 1024 * 1024);
    }
}
