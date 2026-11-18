package com.example.project_flow.integration.ownersoffice;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;

/**
 * Owner's Office's view of one piece of work: {@code status} is queued, in_progress, completed
 * or failed. {@code result} is the report, as the other application wrote it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OfficeTask(long id, String kind, String status, String summary, JsonNode result, String error) {
}
