package com.tejesh.timetable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * <h2>TimetableApplication – Web Application Main Entry Point</h2>
 *
 * <p>Bootstraps the Spring Boot Web Application (Spring MVC + Thymeleaf) on port 8082.
 * Also keeps {@code java.awt.headless = false} so native desktop alarms and Zoom automation continue working.
 */
@SpringBootApplication
@EnableScheduling
public class TimetableApplication {

    private static final Logger log = LoggerFactory.getLogger(TimetableApplication.class);

    public static void main(String[] args) {
        log.info("Starting AI Personalized Timetable Web Application on http://localhost:8082 ...");
        System.setProperty("java.awt.headless", "false");
        SpringApplication.run(TimetableApplication.class, args);
    }
}
