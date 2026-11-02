package com.recu.ai.features.service;


import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.recu.ai.commons.exception.BusinessException;
import com.recu.ai.commons.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class GeminiGuideClient {

    private static final int MAX_ATTEMPTS = 3;
    private static final long BACKOFF_MILLIS = 500;

    private final RestClient restClient;
    private final String apiKey;
    private final String model;
    private final Sleeper sleeper;

    @Autowired
    public GeminiGuideClient(
            RestClient.Builder builder,
            @Value("${gemini.api-key}") String apiKey,
            @Value("${collect.classify.model:}") String model) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        this.restClient =
                builder
                        .clone()
                        .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                        .requestFactory(requestFactory)
                        .build();
        this.apiKey = apiKey;
        this.model = model;
        this.sleeper = Thread::sleep;
    }

    GeminiGuideClient(RestClient restClient, String apiKey, String model, Sleeper sleeper) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.model = model;
        this.sleeper = sleeper;
    }

    public String generate(String prompt) {
        if (model.isBlank()) {
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        }

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return callGemini(prompt);
            } catch (HttpServerErrorException.ServiceUnavailable exception) {
                if (attempt == MAX_ATTEMPTS) {
                    log.error("Gemini guide request failed after {} attempts", MAX_ATTEMPTS);
                    throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
                }
                log.warn("Gemini guide request returned 503. retry={}/{}", attempt + 1, MAX_ATTEMPTS);
                sleep(BACKOFF_MILLIS * attempt);
            } catch (BusinessException exception) {
                throw exception;
            } catch (Exception exception) {
                log.error("Gemini guide request failed. type={}", exception.getClass().getName());
                throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
            }
        }
        throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
    }

    private String callGemini(String prompt) {
        JsonNode response =
                restClient
                        .post()
                        .uri(
                                uriBuilder ->
                                        uriBuilder
                                                .path("/models/{model}:generateContent")
                                                .queryParam("key", apiKey)
                                                .build(model))
                        .body(
                                Map.of(
                                        "contents",
                                        List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                                        "generationConfig",
                                        Map.of("responseMimeType", "application/json")))
                        .retrieve()
                        .body(JsonNode.class);

        String text =
                response == null
                        ? null
                        : response
                        .path("candidates")
                        .path(0)
                        .path("content")
                        .path("parts")
                        .path(0)
                        .path("text")
                        .textValue();
        if (text == null) {
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        }
        return text;
    }

    private void sleep(long millis) {
        try {
            sleeper.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        }
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
