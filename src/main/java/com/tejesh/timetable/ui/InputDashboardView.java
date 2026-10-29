package com.tejesh.timetable.ui;

import com.tejesh.timetable.model.ProficiencyLevel;
import com.tejesh.timetable.model.SubjectInput;
import com.tejesh.timetable.model.TimetableSlot;
import com.tejesh.timetable.model.User;
import com.tejesh.timetable.service.TimetableManagerService;
import com.tejesh.timetable.service.VoiceInputService;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * <h2>InputDashboardView – Multi-Format Input & AI Generator Dashboard</h2>
 *
 * <p>Pure-Java UI screen enabling the student to:
 * <ul>
 *   <li>Add/edit subjects with topics and self-assessed proficiency.</li>
 *   <li>Select and parse syllabus PDFs with Apache PDFBox.</li>
 *   <li>Capture microphone audio clips transcribed by the Gemini Multimodal API.</li>
 *   <li>Input study window preferences and recurring Zoom video meeting links.</li>
 *   <li>Trigger the background AI Brain to generate the timetable with psychological pacing.</li>
 * </ul>
 */
public class InputDashboardView extends BorderPane {

    private static final Logger log = LoggerFactory.getLogger(InputDashboardView.class);

    private final ApplicationContext springContext;
    private final Stage primaryStage;
    private final User user;

    // Subject Rows Container
    private final VBox subjectRowsBox = new VBox(10);

    // Selected Files
    private File selectedPdfFile = null;
    private File recordedVoiceFile = null;

    private Label pdfStatusLabel;
    private Label voiceStatusLabel;
    private TextArea instructionsArea;
    private Button generateButton;
    private ProgressIndicator spinner;
    private Label progressLabel;

    public InputDashboardView(ApplicationContext springContext, Stage primaryStage, User user) {
        this.springContext = springContext;
        this.primaryStage = primaryStage;
        this.user = user;

        initUI();
    }

    private void initUI() {
        setStyle("-fx-background-color: #f8fafc;");

        // ── 1. Top Header Bar ─────────────────────────────────────────────────
        setTop(buildTopHeader());

        // ── 2. Scrollable Center Form ─────────────────────────────────────────
        VBox formContent = new VBox(22);
        formContent.setPadding(new Insets(25, 40, 35, 40));
        formContent.setMaxWidth(920);

        formContent.getChildren().addAll(
                buildSubjectSection(),
                buildPdfSection(),
                buildVoiceSection(),
                buildInstructionsSection(),
                buildActionSection()
        );

        ScrollPane scrollPane = new ScrollPane(formContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #f8fafc; -fx-background-color: transparent; -fx-border-color: transparent;");

        setCenter(scrollPane);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Section Builders
    // ──────────────────────────────────────────────────────────────────────────

    private HBox buildTopHeader() {
        HBox header = new HBox(15);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(16, 30, 16, 30));
        header.setStyle("-fx-background-color: #ffffff; " +
                       "-fx-border-color: #e2e8f0; -fx-border-width: 0 0 1 0; " +
                       "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.03), 10, 0, 0, 2);");

        Label appLogo = new Label("PREPRO AI");
        appLogo.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #1e3a8a; " +
                        "-fx-background-color: #dbeafe; -fx-padding: 4 10; -fx-background-radius: 6;");

        VBox userDetails = new VBox(2);
        Label welcomeLabel = new Label("Welcome, " + user.getUsername());
        welcomeLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #0f172a;");

        Label examBadge = new Label("Target: " + user.getExamTarget());
        examBadge.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");
        userDetails.getChildren().addAll(welcomeLabel, examBadge);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button logoutButton = new Button("Switch User");
        logoutButton.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #475569; " +
                             "-fx-font-weight: bold; -fx-padding: 6 14; -fx-background-radius: 6; -fx-cursor: hand;");
        logoutButton.setOnAction(e -> {
            LoginView loginView = new LoginView(springContext, primaryStage);
            primaryStage.getScene().setRoot(loginView);
        });

        header.getChildren().addAll(appLogo, userDetails, spacer, logoutButton);
        return header;
    }

    private VBox buildSubjectSection() {
        VBox box = new VBox(12);
        box.setStyle("-fx-background-color: #ffffff; -fx-padding: 20; -fx-background-radius: 10; " +
                    "-fx-border-color: #e2e8f0; -fx-border-radius: 10;");

        Label sectionTitle = new Label("1. Subject Syllabus & Proficiency Levels");
        sectionTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");

        Label sectionDesc = new Label("AI Priority Rule: 50% study time is allocated to POOR subjects, 30% to AVERAGE, and 20% to GOOD.");
        sectionDesc.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");

        // Column Titles
        HBox headerRow = new HBox(10);
        headerRow.setPadding(new Insets(4, 0, 0, 0));
        Label colSubj = new Label("Subject");
        colSubj.setPrefWidth(160);
        colSubj.setStyle("-fx-font-weight: bold; -fx-text-fill: #475569; -fx-font-size: 12px;");

        Label colTopics = new Label("Topics to Cover");
        HBox.setHgrow(colTopics, Priority.ALWAYS);
        colTopics.setStyle("-fx-font-weight: bold; -fx-text-fill: #475569; -fx-font-size: 12px;");

        Label colProf = new Label("Proficiency Level");
        colProf.setPrefWidth(140);
        colProf.setStyle("-fx-font-weight: bold; -fx-text-fill: #475569; -fx-font-size: 12px;");

        Label colAction = new Label("");
        colAction.setPrefWidth(40);

        headerRow.getChildren().addAll(colSubj, colTopics, colProf, colAction);

        // Prepopulate standard default subjects
        addSubjectRow("Physics", "Electrostatics, Motion, Gravitation, Thermodynamics", ProficiencyLevel.GOOD);
        addSubjectRow("Chemistry", "Atoms & Molecules, Carbon Compounds, Chemical Bonding", ProficiencyLevel.AVERAGE);
        addSubjectRow("Mathematics", "Matrices, Straight Lines, Sequences, Integration", ProficiencyLevel.POOR);

        Button addRowBtn = new Button("+ Add Another Subject");
        addRowBtn.setStyle("-fx-background-color: #eff6ff; -fx-text-fill: #2563eb; " +
                          "-fx-font-weight: bold; -fx-padding: 6 14; -fx-background-radius: 6; -fx-cursor: hand;");
        addRowBtn.setOnAction(e -> addSubjectRow("", "", ProficiencyLevel.AVERAGE));

        box.getChildren().addAll(sectionTitle, sectionDesc, headerRow, subjectRowsBox, addRowBtn);
        return box;
    }

    private void addSubjectRow(String subjectName, String topics, ProficiencyLevel level) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);

        TextField subjectField = new TextField(subjectName);
        subjectField.setPromptText("e.g. Physics");
        subjectField.setPrefWidth(160);
        subjectField.setStyle("-fx-padding: 8; -fx-border-color: #cbd5e1; -fx-border-radius: 6;");

        TextField topicsField = new TextField(topics);
        topicsField.setPromptText("Comma-separated topics");
        HBox.setHgrow(topicsField, Priority.ALWAYS);
        topicsField.setStyle("-fx-padding: 8; -fx-border-color: #cbd5e1; -fx-border-radius: 6;");

        ComboBox<ProficiencyLevel> levelCombo = new ComboBox<>();
        levelCombo.getItems().addAll(ProficiencyLevel.POOR, ProficiencyLevel.AVERAGE, ProficiencyLevel.GOOD);
        levelCombo.setValue(level != null ? level : ProficiencyLevel.AVERAGE);
        levelCombo.setPrefWidth(140);
        levelCombo.setStyle("-fx-padding: 4; -fx-border-color: #cbd5e1; -fx-border-radius: 6;");

        Button removeBtn = new Button("✕");
        removeBtn.setPrefWidth(40);
        removeBtn.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #dc2626; -fx-font-weight: bold; " +
                          "-fx-padding: 8; -fx-background-radius: 6; -fx-cursor: hand;");
        removeBtn.setOnAction(e -> {
            if (subjectRowsBox.getChildren().size() > 1) {
                subjectRowsBox.getChildren().remove(row);
            }
        });

        row.getChildren().addAll(subjectField, topicsField, levelCombo, removeBtn);
        subjectRowsBox.getChildren().add(row);
    }

    private VBox buildPdfSection() {
        VBox box = new VBox(10);
        box.setStyle("-fx-background-color: #ffffff; -fx-padding: 20; -fx-background-radius: 10; " +
                    "-fx-border-color: #e2e8f0; -fx-border-radius: 10;");

        Label sectionTitle = new Label("2. Syllabus PDF Upload (Optional)");
        sectionTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");

        Label sectionDesc = new Label("Upload official exam syllabus or class schedule. Apache PDFBox extracts chapters to align AI suggestions.");
        sectionDesc.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");

        HBox uploadControls = new HBox(12);
        uploadControls.setAlignment(Pos.CENTER_LEFT);

        Button choosePdfBtn = new Button("📁 Choose PDF File...");
        choosePdfBtn.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #334155; -fx-font-weight: bold; " +
                             "-fx-padding: 8 16; -fx-background-radius: 6; -fx-cursor: hand;");

        pdfStatusLabel = new Label("No PDF selected");
        pdfStatusLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #64748b;");

        Button clearPdfBtn = new Button("Clear");
        clearPdfBtn.setVisible(false);
        clearPdfBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ef4444; -fx-cursor: hand;");
        clearPdfBtn.setOnAction(e -> {
            selectedPdfFile = null;
            pdfStatusLabel.setText("No PDF selected");
            clearPdfBtn.setVisible(false);
        });

        choosePdfBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select Syllabus PDF Document");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files (*.pdf)", "*.pdf"));

            File file = chooser.showOpenDialog(primaryStage);
            if (file != null) {
                selectedPdfFile = file;
                pdfStatusLabel.setText("Selected: " + file.getName() + " (" + (file.length() / 1024) + " KB)");
                clearPdfBtn.setVisible(true);
            }
        });

        uploadControls.getChildren().addAll(choosePdfBtn, pdfStatusLabel, clearPdfBtn);
        box.getChildren().addAll(sectionTitle, sectionDesc, uploadControls);
        return box;
    }

    private VBox buildVoiceSection() {
        VBox box = new VBox(10);
        box.setStyle("-fx-background-color: #ffffff; -fx-padding: 20; -fx-background-radius: 10; " +
                    "-fx-border-color: #e2e8f0; -fx-border-radius: 10;");

        Label sectionTitle = new Label("3. Voice Input & Gemini Transcription (Optional)");
        sectionTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");

        Label sectionDesc = new Label("Capture spoken constraints using Java Sound API. Gemini Multimodal transcribes it automatically.");
        sectionDesc.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");

        HBox voiceControls = new HBox(12);
        voiceControls.setAlignment(Pos.CENTER_LEFT);

        Button recordVoiceBtn = new Button("🎤 Record Voice (5 Seconds)");
        recordVoiceBtn.setStyle("-fx-background-color: #fef2f2; -fx-text-fill: #b91c1c; -fx-font-weight: bold; " +
                               "-fx-padding: 8 16; -fx-background-radius: 6; -fx-cursor: hand;");

        voiceStatusLabel = new Label("Microphone ready");
        voiceStatusLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #64748b;");

        recordVoiceBtn.setOnAction(e -> {
            recordVoiceBtn.setDisable(true);
            voiceStatusLabel.setText("🎙️ Recording in progress... (speak now for 5 seconds)");

            // Record in a background thread so UI doesn't freeze
            Task<File> recordTask = new Task<>() {
                @Override
                protected File call() {
                    VoiceInputService voiceService = springContext.getBean(VoiceInputService.class);
                    return voiceService.recordVoiceClip(5);
                }
            };

            recordTask.setOnSucceeded(event -> {
                recordVoiceBtn.setDisable(false);
                recordedVoiceFile = recordTask.getValue();
                if (recordedVoiceFile != null && recordedVoiceFile.exists()) {
                    voiceStatusLabel.setText("✅ Audio captured: " + recordedVoiceFile.getName() + " (will transcribe on generation)");
                } else {
                    voiceStatusLabel.setText("⚠️ Microphone unavailable or capture failed. Using text instructions.");
                }
            });

            recordTask.setOnFailed(event -> {
                recordVoiceBtn.setDisable(false);
                voiceStatusLabel.setText("❌ Recording error: " + recordTask.getException().getMessage());
            });

            new Thread(recordTask).start();
        });

        voiceControls.getChildren().addAll(recordVoiceBtn, voiceStatusLabel);
        box.getChildren().addAll(sectionTitle, sectionDesc, voiceControls);
        return box;
    }

    private VBox buildInstructionsSection() {
        VBox box = new VBox(10);
        box.setStyle("-fx-background-color: #ffffff; -fx-padding: 20; -fx-background-radius: 10; " +
                    "-fx-border-color: #e2e8f0; -fx-border-radius: 10;");

        Label sectionTitle = new Label("4. Study Windows & Recurring Zoom Links");
        sectionTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");

        Label sectionDesc = new Label("Specify daily study hours and video meeting URLs. Zoom slots will be auto-opened at scheduled times.");
        sectionDesc.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");

        instructionsArea = new TextArea();
        instructionsArea.setPrefRowCount(4);
        instructionsArea.setWrapText(true);
        instructionsArea.setStyle("-fx-padding: 8; -fx-border-color: #cbd5e1; -fx-border-radius: 6; -fx-font-size: 13px;");
        instructionsArea.setText(
                "I study from 4:00 PM (16:00) to 8:00 PM (20:00) on weekdays (Monday through Friday).\n" +
                "I have a recurring Zoom meeting every Monday at 5:00 PM (17:00). Meeting link: https://zoom.us/j/99999. Please mark it as ZOOM_MEETING."
        );

        box.getChildren().addAll(sectionTitle, sectionDesc, instructionsArea);
        return box;
    }

    private VBox buildActionSection() {
        VBox box = new VBox(14);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(10, 0, 10, 0));

        generateButton = new Button("🧠 Generate AI Personalized Timetable");
        generateButton.setMaxWidth(460);
        generateButton.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; " +
                               "-fx-font-size: 16px; -fx-font-weight: bold; -fx-padding: 14 28; " +
                               "-fx-background-radius: 10; -fx-cursor: hand; " +
                               "-fx-effect: dropshadow(three-pass-box, rgba(37,99,235,0.4), 12, 0, 0, 4);");

        spinner = new ProgressIndicator();
        spinner.setMaxSize(36, 36);
        spinner.setVisible(false);

        progressLabel = new Label("");
        progressLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #2563eb; -fx-font-weight: bold;");

        HBox statusBox = new HBox(10, spinner, progressLabel);
        statusBox.setAlignment(Pos.CENTER);

        generateButton.setOnAction(e -> handleGenerateTimetable());

        box.getChildren().addAll(generateButton, statusBox);
        return box;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Generation Action Handler
    // ──────────────────────────────────────────────────────────────────────────

    private void handleGenerateTimetable() {
        // Collect Subject inputs
        List<SubjectInput> subjects = new ArrayList<>();
        for (var node : subjectRowsBox.getChildren()) {
            if (node instanceof HBox row) {
                TextField sField = (TextField) row.getChildren().get(0);
                TextField tField = (TextField) row.getChildren().get(1);
                @SuppressWarnings("unchecked")
                ComboBox<ProficiencyLevel> pCombo = (ComboBox<ProficiencyLevel>) row.getChildren().get(2);

                String name = sField.getText().trim();
                String topics = tField.getText().trim();
                ProficiencyLevel level = pCombo.getValue();

                if (!name.isEmpty()) {
                    subjects.add(new SubjectInput(name, topics, level));
                }
            }
        }

        if (subjects.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Missing Subjects");
            alert.setHeaderText("No Subjects Configured");
            alert.setContentText("Please add at least one subject with topics to generate your study timetable.");
            alert.showAndWait();
            return;
        }

        String extraInstructions = instructionsArea.getText().trim();

        // Lock UI and show spinner
        generateButton.setDisable(true);
        spinner.setVisible(true);
        progressLabel.setText("Connecting to Gemini AI Brain & applying psychological pacing... (approx 15-30s)");

        // Run in background Task to keep JavaFX UI thread fluid
        Task<List<TimetableSlot>> generateTask = new Task<>() {
            @Override
            protected List<TimetableSlot> call() {
                TimetableManagerService manager = springContext.getBean(TimetableManagerService.class);
                return manager.processInputsAndSaveTimetable(
                        user,
                        subjects,
                        selectedPdfFile,
                        recordedVoiceFile,
                        extraInstructions
                );
            }
        };

        generateTask.setOnSucceeded(event -> {
            log.info("InputDashboardView: Timetable successfully generated with {} slots.",
                    generateTask.getValue().size());

            // Switch to TimetableGridView
            TimetableGridView gridView = new TimetableGridView(springContext, primaryStage, user);
            primaryStage.getScene().setRoot(gridView);
        });

        generateTask.setOnFailed(event -> {
            generateButton.setDisable(false);
            spinner.setVisible(false);
            progressLabel.setText("");

            Throwable ex = generateTask.getException();
            log.error("InputDashboardView: Failed to generate timetable", ex);

            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("AI Timetable Generation Error");
            alert.setHeaderText("Generation Failed");
            alert.setContentText(ex != null ? ex.getMessage() : "Unknown error while processing AI timetable.");
            alert.showAndWait();
        });

        new Thread(generateTask).start();
    }
}
