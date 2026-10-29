package com.tejesh.timetable.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Central Spring {@code @Configuration} class for application-wide beans.
 */
@Configuration
@EnableScheduling
public class AppConfig {

    /**
     * Shared Jackson {@link ObjectMapper} with:
     * <ul>
     *   <li>{@code JavaTimeModule} registered — handles {@code LocalDate},
     *       {@code LocalTime}, etc. in future entity fields.</li>
     *   <li>Unknown properties ignored globally (defensive deserialization).</li>
     * </ul>
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    /**
     * Shared Java 11+ {@link HttpClient} used by all services that call
     * external APIs (Gemini generateContent, multimodal transcription, etc.).
     *
     * <p>Configuration:
     * <ul>
     *   <li>30-second connect timeout — fail fast on DNS/network issues.</li>
     *   <li>Default redirect policy (NEVER) — Gemini doesn't redirect.</li>
     * </ul>
     */
    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }
}
