package com.example.project_flow.integration.ownersoffice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Talks to Owner's Office's agent API over localhost. Owner's Office holds the financial records;
 * FOCUS FLOW only sends work and receives reports. The bearer token is set from configuration
 * (environment or application-local.properties) and is never logged or shown.
 */
@Component
public class OwnersOfficeClient {

    private final RestClient rest;
    private final boolean configured;

    public OwnersOfficeClient(@Value("${owners-office.base-url:http://127.0.0.1:4320}") String baseUrl,
                              @Value("${owners-office.token:}") String token) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.configured = token != null && !token.isBlank();
        this.rest = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + (token == null ? "" : token.trim()))
                .build();
    }

    /** False until OWNERS_OFFICE_TOKEN is set; nothing is sent in that case. */
    public boolean configured() {
        return configured;
    }

    /** Whether Owner's Office answers right now. Never throws. */
    public boolean reachable() {
        if (!configured) return false;
        try {
            rest.get().uri("/api/agent/health").retrieve().toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            return false;
        }
    }

    /** Sends one piece of work. Retrying with the same externalRef returns the existing item. */
    public OfficeTask createTask(String externalRef, String kind, String title, String instructions) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("external_ref", externalRef);
        body.put("title", title);
        body.put("instructions", instructions == null ? "" : instructions);
        body.put("requested_by", "focus-flow");
        if (kind != null) body.put("kind", kind);
        return call(() -> rest.post()
                .uri("/api/agent/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(OfficeTask.class));
    }

    public OfficeTask getTask(long officeTaskId) {
        return call(() -> rest.get()
                .uri("/api/agent/tasks/{id}", officeTaskId)
                .retrieve()
                .body(OfficeTask.class));
    }

    private <T> T call(Supplier<T> request) {
        if (!configured) {
            throw new OwnersOfficeException("Owner's Office 연결 토큰(OWNERS_OFFICE_TOKEN)이 설정되지 않았습니다.");
        }
        try {
            T out = request.get();
            if (out == null) throw new OwnersOfficeException("Owner's Office가 빈 응답을 보냈습니다.");
            return out;
        } catch (RestClientResponseException e) {
            throw new OwnersOfficeException("Owner's Office가 요청을 거절했습니다 (HTTP " + e.getStatusCode().value() + ").");
        } catch (RestClientException e) {
            throw new OwnersOfficeException("Owner's Office에 연결할 수 없습니다. 앱이 실행 중인지 확인하세요.");
        }
    }
}
