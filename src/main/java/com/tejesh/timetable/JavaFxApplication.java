package com.tejesh.timetable;

import com.tejesh.timetable.ui.LoginView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * <h2>JavaFxApplication – Spring Boot to JavaFX Integration Bridge</h2>
 *
 * <p>Handles the JavaFX lifecycle:
 * <ul>
 *   <li>{@link #init()}: Bootstraps the Spring Boot ApplicationContext in non-headless mode.</li>
 *   <li>{@link #start(Stage)}: Creates the primary stage and launches the pure-Java {@link LoginView}.</li>
 *   <li>{@link #stop()}: Gracefully closes the Spring context and shuts down background tasks.</li>
 * </ul>
 */
public class JavaFxApplication extends Application {

    private static final Logger log = LoggerFactory.getLogger(JavaFxApplication.class);

    private ConfigurableApplicationContext springContext;

    @Override
    public void init() {
        log.info("JavaFxApplication: Bootstrapping Spring Boot context...");
        // Ensure AWT headless mode is disabled so SystemTray, Desktop, and native dialogs function
        System.setProperty("java.awt.headless", "false");

        this.springContext = new SpringApplicationBuilder(TimetableApplication.class)
                .headless(false)
                .run();
        log.info("JavaFxApplication: Spring Boot context initialized successfully.");
    }

    @Override
    public void start(Stage primaryStage) {
        log.info("JavaFxApplication: Starting JavaFX UI...");
        primaryStage.setTitle("AI Personalized Timetable & Study Assistant");
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(700);

        LoginView loginView = new LoginView(springContext, primaryStage);
        Scene scene = new Scene(loginView, 960, 720);

        primaryStage.setScene(scene);
        primaryStage.centerOnScreen();
        primaryStage.show();
        log.info("JavaFxApplication: Primary stage displayed.");
    }

    @Override
    public void stop() {
        log.info("JavaFxApplication: Shutting down application...");
        if (springContext != null && springContext.isActive()) {
            springContext.close();
        }
        Platform.exit();
        System.exit(0);
    }
}
