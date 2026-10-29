package com.tejesh.timetable.ui;

import com.tejesh.timetable.model.User;
import com.tejesh.timetable.service.TimetableManagerService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

/**
 * <h2>LoginView – Modern Pure-Java Authentication Screen</h2>
 *
 * <p>Allows the student to log in or register. Upon successful authentication,
 * smoothly transitions to the {@link InputDashboardView}.
 */
public class LoginView extends StackPane {

    private static final Logger log = LoggerFactory.getLogger(LoginView.class);

    private final ApplicationContext springContext;
    private final Stage primaryStage;

    public LoginView(ApplicationContext springContext, Stage primaryStage) {
        this.springContext = springContext;
        this.primaryStage = primaryStage;

        initUI();
    }

    private void initUI() {
        // Deep modern gradient background
        setStyle("-fx-background-color: linear-gradient(to bottom right, #0f172a, #1e3a8a, #3b82f6);");

        // Centered Auth Card
        VBox card = new VBox(20);
        card.setMaxWidth(440);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(35, 40, 35, 40));
        card.setStyle("-fx-background-color: #ffffff; " +
                     "-fx-background-radius: 16; " +
                     "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.35), 25, 0, 0, 10);");

        // Header Title
        Label brandBadge = new Label("PREPRO AI BRAIN");
        brandBadge.setStyle("-fx-background-color: #dbeafe; -fx-text-fill: #1d4ed8; " +
                           "-fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 4 10; " +
                           "-fx-background-radius: 20;");

        Label titleLabel = new Label("Study Planner & Assistant");
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");

        Label subtitleLabel = new Label("Personalized scheduling with 30-min pacing, alarms & Zoom launch.");
        subtitleLabel.setWrapText(true);
        subtitleLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #64748b;");

        VBox headerBox = new VBox(8, brandBadge, titleLabel, subtitleLabel);

        // Username Field
        Label userLabel = new Label("Student Username");
        userLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #334155;");

        TextField usernameField = new TextField();
        usernameField.setPromptText("Enter your username");
        usernameField.setStyle("-fx-padding: 10; -fx-background-radius: 8; -fx-border-color: #cbd5e1; " +
                              "-fx-border-radius: 8; -fx-border-width: 1.5; -fx-font-size: 14px;");

        // Password Field
        Label passLabel = new Label("Password");
        passLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #334155;");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Enter password");
        passwordField.setStyle("-fx-padding: 10; -fx-background-radius: 8; -fx-border-color: #cbd5e1; " +
                              "-fx-border-radius: 8; -fx-border-width: 1.5; -fx-font-size: 14px;");

        // Exam Target Field
        Label examLabel = new Label("Target Competitive Exam");
        examLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #334155;");

        ComboBox<String> examComboBox = new ComboBox<>();
        examComboBox.getItems().addAll(
                "JEE Advanced",
                "JEE Mains",
                "NEET (Medical)",
                "UPSC Civil Services",
                "CBSE Class 12 Boards",
                "GATE",
                "SAT / College Entrance"
        );
        examComboBox.setValue("JEE Advanced");
        examComboBox.setMaxWidth(Double.MAX_VALUE);
        examComboBox.setStyle("-fx-padding: 5; -fx-background-radius: 8; -fx-border-color: #cbd5e1; " +
                             "-fx-border-radius: 8; -fx-font-size: 13px;");

        // Error / Status Message
        Label statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 12px;");
        statusLabel.setVisible(false);

        // Submit Button
        Button loginButton = new Button("🚀 Enter Study Dashboard");
        loginButton.setMaxWidth(Double.MAX_VALUE);
        loginButton.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; " +
                            "-fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 12; " +
                            "-fx-background-radius: 8; -fx-cursor: hand;");

        // Button hover animation
        loginButton.setOnMouseEntered(e -> loginButton.setStyle(
                "-fx-background-color: #1d4ed8; -fx-text-fill: white; -fx-font-size: 14px; " +
                "-fx-font-weight: bold; -fx-padding: 12; -fx-background-radius: 8; -fx-cursor: hand;"));
        loginButton.setOnMouseExited(e -> loginButton.setStyle(
                "-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-size: 14px; " +
                "-fx-font-weight: bold; -fx-padding: 12; -fx-background-radius: 8; -fx-cursor: hand;"));

        // Action Handler
        loginButton.setOnAction(e -> {
            String username = usernameField.getText().trim();
            String password = passwordField.getText().trim();
            String exam = examComboBox.getValue();

            if (username.isEmpty() || password.isEmpty()) {
                statusLabel.setText("Please enter both username and password.");
                statusLabel.setVisible(true);
                return;
            }

            try {
                TimetableManagerService manager = springContext.getBean(TimetableManagerService.class);
                User user = manager.loginOrRegister(username, password, exam);
                log.info("LoginView: Successfully authenticated user '{}' for exam '{}'", username, exam);

                // Switch to Input Dashboard
                InputDashboardView dashboardView = new InputDashboardView(springContext, primaryStage, user);
                primaryStage.getScene().setRoot(dashboardView);

            } catch (Exception ex) {
                log.error("LoginView: Authentication error", ex);
                statusLabel.setText("Error: " + ex.getMessage());
                statusLabel.setVisible(true);
            }
        });

        // Assemble card
        VBox inputFields = new VBox(14,
                new VBox(6, userLabel, usernameField),
                new VBox(6, passLabel, passwordField),
                new VBox(6, examLabel, examComboBox),
                statusLabel,
                loginButton
        );

        card.getChildren().addAll(headerBox, inputFields);
        getChildren().add(card);
    }
}
