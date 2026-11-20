package com.example.project_flow.legal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class ReviewSubjectConfig {

    /** Used until a real product integration is provided. Returns no subjects and makes no connection. */
    @Bean
    @ConditionalOnMissingBean(ReviewSubjectSource.class)
    ReviewSubjectSource noProductReviewSource() {
        return List::of;
    }
}
