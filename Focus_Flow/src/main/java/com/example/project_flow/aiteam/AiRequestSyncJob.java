package com.example.project_flow.aiteam;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Keeps AI work moving in the background. The interval is owners-office.sync-interval-ms (default one minute). */
@Component
public class AiRequestSyncJob {

    private static final Logger log = LoggerFactory.getLogger(AiRequestSyncJob.class);

    private final AiRequestService requests;

    public AiRequestSyncJob(AiRequestService requests) {
        this.requests = requests;
    }

    @Scheduled(fixedDelayString = "${owners-office.sync-interval-ms:60000}",
            initialDelayString = "${owners-office.sync-interval-ms:60000}")
    public void run() {
        try {
            requests.sync();
        } catch (RuntimeException e) {
            // Logged by type only: a message could contain record text.
            log.warn("AI team sync skipped ({})", e.getClass().getSimpleName());
        }
    }
}
