package com.example.project_flow.aiteam;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on scheduled work for the AI team (see {@link AiRequestSyncJob}). */
@Configuration
@EnableScheduling
public class AiTeamConfig {
}
