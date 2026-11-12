package com.example.project_flow.admin.web;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.web.server.servlet.CookieSameSiteSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AdminWebConfig implements WebMvcConfigurer {

    public static final long MAX_FILE_BYTES = 50L * 1024 * 1024;
    public static final int MAX_FILES_PER_UPLOAD = 10;

    private final AuthInterceptor auth;

    public AdminWebConfig(AuthInterceptor auth) {
        this.auth = auth;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(auth).addPathPatterns("/api/**");
    }

    /**
     * Raises Spring Boot's 1 MB upload default for submission files. Declared here so
     * application.properties stays untouched.
     */
    @Bean
    public MultipartConfigElement multipartConfigElement() {
        long maxRequest = MAX_FILE_BYTES * MAX_FILES_PER_UPLOAD + 1024 * 1024;
        return new MultipartConfigElement("", MAX_FILE_BYTES, maxRequest, 1024 * 1024);
    }

    /** The session cookie is never sent on requests started by another site. */
    @Bean
    public CookieSameSiteSupplier sessionCookieSameSite() {
        return CookieSameSiteSupplier.ofLax().whenHasName("JSESSIONID");
    }
}
