package com.tejesh.timetable.ui;

import com.tejesh.timetable.model.SlotType;
import com.tejesh.timetable.model.TimetableSlot;
import com.tejesh.timetable.model.User;
import com.tejesh.timetable.repository.TimetableSlotRepository;
import com.tejesh.timetable.service.ExportService;
import com.tejesh.timetable.service.NotificationService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <h2>TimetableGridView – Fixed Master Timetable Grid & Multi-Format Exporter UI</h2>
 *
 * <p>Presents the generated study schedule as a <b>Fixed Master Timetable Grid</b>
 * valid as a long-term academic plan (1–3 months):
 * <ul>
 *   <li><b>X-Axis (Columns):</b> Time Slots (e.g., 16:00-16:30, 16:30-16:40 (Break), 16:40-17:10).</li>
 *   <li><b>Y-Axis (Rows):</b> Subject Names (e.g., Mathematics, Chemistry, Physics, Cognitive Breaks, Online Meetings).</li>
 *   <li><b>Grid Intersections (Cells):</b> Specific Topic being studied or the activity details taking place.</li>
 *   <li><b>Color-Coding:</b>
 *     <ul>
 *       <li>POOR subjects: Soft Red / Orange.</li>
 *       <li>GOOD subjects: Soft Green.</li>
 *       <li>10-Minute Breaks: Soft Slate / Gray.</li>
 *       <li>Zoom Meetings: Soft Purple with clickable auto-launch buttons.</li>
 *     </ul>
 *   </li>
 *   <li><b>Day Selector:</b> Switch between Monday–Sunday views or view the full list.</li>
 * </ul>
 */
public class TimetableGridView extends BorderPane {

    private static final Logger log = LoggerFactory.getLogger(TimetableGridView.class);

    private final ApplicationContext springContext;
    private final Stage primaryStage;
    private final User user;

    private List<TimetableSlot> allSlots = new ArrayList<>();
    private String selectedDay = "Monday";
    private boolean isMatrixView = true;

    private final StackPane centerContentArea = new StackPane();
    private final HBox daySelectorBar = new HBox(8);

    public TimetableGridView(ApplicationContext springContext, Stage primaryStage, User user) {
        this.springContext = springContext;
        this.primaryStage = primaryStage;
        this.user = user;

        initUI();
        loadTimetableData();
    }

    private void initUI() {
        setStyle("-fx-background-color: #f8fafc;");

        setTop(buildHeader());
        setCenter(centerContentArea);
        setBottom(buildFooterControls());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Header & Controls
    // ──────────────────────────────────────────────────────────────────────────

    private VBox buildHeader() {
        VBox container = new VBox(12);
        container.setPadding(new Insets(18, 30, 14, 30));
        container.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e2e8f0; -fx-border-width: 0 0 1 0;");

        // Top Navigation Row
        HBox topRow = new HBox(15);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Button backButton = new Button("← Back to Dashboard");
        backButton.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #334155; -fx-font-weight: bold; " +
                           "-fx-padding: 8 16; -fx-background-radius: 6; -fx-cursor: hand;");
        backButton.setOnAction(e -> {
            InputDashboardView dashboardView = new InputDashboardView(springContext, primaryStage, user);
            primaryStage.getScene().setRoot(dashboardView);
        });

        VBox titleBox = new VBox(2);
        Label title = new Label("📅 Fixed Master Timetable Grid (1-3 Month Plan)");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");

        Label subtitle = new Label("Student: " + user.getUsername() + "  |  Exam: " + user.getExamTarget() +
                                  "  |  X-Axis: Time Slots  •  Y-Axis: Subjects & Activities");
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");
        titleBox.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // View Mode Switcher: Matrix Grid vs Chronological List
        Button toggleViewBtn = new Button("📋 Switch to List View");
        toggleViewBtn.setStyle("-fx-background-color: #eff6ff; -fx-text-fill: #2563eb; -fx-font-weight: bold; " +
                              "-fx-padding: 8 14; -fx-background-radius: 6; -fx-cursor: hand;");

        toggleViewBtn.setOnAction(e -> {
            isMatrixView = !isMatrixView;
            if (isMatrixView) {
                toggleViewBtn.setText("📋 Switch to List View");
                daySelectorBar.setVisible(true);
                daySelectorBar.setManaged(true);
                renderCurrentDayMatrix();
            } else {
                toggleViewBtn.setText("🔲 Switch to Master Grid (X:Time / Y:Subject)");
                daySelectorBar.setVisible(false);
                daySelectorBar.setManaged(false);
                renderChronologicalListView();
            }
        });

        Button testAlarmBtn = new Button("🔔 Test Alarm");
        testAlarmBtn.setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #92400e; -fx-font-weight: bold; " +
                             "-fx-padding: 8 14; -fx-background-radius: 6; -fx-cursor: hand;");
        testAlarmBtn.setOnAction(e -> {
            NotificationService notificationService = springContext.getBean(NotificationService.class);
            notificationService.sendDesktopAlert(
                    "🔔 Master Timetable Alert",
                    "Desktop notifications, system beeps, and Zoom automation are active for " + user.getUsername() + "!"
            );
        });

        topRow.getChildren().addAll(backButton, titleBox, spacer, toggleViewBtn, testAlarmBtn);

        // Day Selector Bar (Monday through Sunday)
        daySelectorBar.setAlignment(Pos.CENTER_LEFT);
        daySelectorBar.setPadding(new Insets(4, 0, 0, 0));
        buildDaySelectorButtons();

        container.getChildren().addAll(topRow, daySelectorBar);
        return container;
    }

    private void buildDaySelectorButtons() {
        daySelectorBar.getChildren().clear();

        Label dayLabel = new Label("Select Day:");
        dayLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #475569; -fx-font-size: 13px; -fx-padding: 6 10 6 0;");
        daySelectorBar.getChildren().add(dayLabel);

        List<String> days = List.of("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday");
        for (String day : days) {
            Button dayBtn = new Button(day);
            updateDayButtonStyle(dayBtn, day.equalsIgnoreCase(selectedDay));

            dayBtn.setOnAction(e -> {
                selectedDay = day;
                buildDaySelectorButtons();
                if (isMatrixView) {
                    renderCurrentDayMatrix();
                }
            });

            daySelectorBar.getChildren().add(dayBtn);
        }
    }

    private void updateDayButtonStyle(Button btn, boolean isSelected) {
        if (isSelected) {
            btn.setStyle("-fx-background-color: #1e3a8a; -fx-text-fill: white; -fx-font-weight: bold; " +
                        "-fx-padding: 6 14; -fx-background-radius: 6; -fx-cursor: hand;");
        } else {
            btn.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #475569; -fx-font-weight: bold; " +
                        "-fx-padding: 6 14; -fx-background-radius: 6; -fx-cursor: hand;");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 1. THE FIXED MASTER MATRIX GRID (X-Axis: Time / Y-Axis: Subject)
    // ──────────────────────────────────────────────────────────────────────────

    private void renderCurrentDayMatrix() {
        centerContentArea.getChildren().clear();

        // Filter slots for the selected day
        List<TimetableSlot> daySlots = allSlots.stream()
                .filter(s -> s.getDayOfWeek() != null && s.getDayOfWeek().equalsIgnoreCase(selectedDay))
                .sorted((a, b) -> a.getStartTime().compareTo(b.getStartTime()))
                .collect(Collectors.toList());

        if (daySlots.isEmpty()) {
            VBox emptyBox = new VBox(15);
            emptyBox.setAlignment(Pos.CENTER);
            Label emptyTitle = new Label("No Study Sessions Scheduled for " + selectedDay);
            emptyTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #64748b;");
            Label emptyDesc = new Label("This day is a designated rest day or no sessions fall on " + selectedDay + ".");
            emptyDesc.setStyle("-fx-font-size: 13px; -fx-text-fill: #94a3b8;");
            emptyBox.getChildren().addAll(emptyTitle, emptyDesc);
            centerContentArea.getChildren().add(emptyBox);
            return;
        }

        // 1. Unique Time Slots (Columns / X-Axis)
        List<String> timeSlots = daySlots.stream()
                .map(s -> s.getStartTime() + " - " + s.getEndTime())
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        // 2. Unique Subjects & Categories (Rows / Y-Axis)
        Set<String> subjectRows = new LinkedHashSet<>();
        // Collect distinct study subjects
        for (TimetableSlot slot : daySlots) {
            if (slot.getSlotType() == SlotType.STUDY && slot.getSubject() != null && !slot.getSubject().isBlank()) {
                subjectRows.add(slot.getSubject());
            }
        }
        // Add special rows if present in schedule
        boolean hasBreaks = daySlots.stream().anyMatch(s -> s.getSlotType() == SlotType.BREAK);
        boolean hasZoom   = daySlots.stream().anyMatch(s -> s.getSlotType() == SlotType.ZOOM_MEETING);

        if (hasZoom) {
            subjectRows.add("🎥 Online Meetings (Zoom)");
        }
        if (hasBreaks) {
            subjectRows.add("☕ Cognitive Breaks (5-10m)");
        }

        List<String> subjectList = new ArrayList<>(subjectRows);

        // 3. Construct GridPane
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(20));
        grid.setStyle("-fx-background-color: transparent;");

        // Set column widths
        ColumnConstraints subjectColConstraint = new ColumnConstraints();
        subjectColConstraint.setMinWidth(190);
        subjectColConstraint.setPrefWidth(210);
        grid.getColumnConstraints().add(subjectColConstraint);

        for (int i = 0; i < timeSlots.size(); i++) {
            ColumnConstraints timeColConstraint = new ColumnConstraints();
            timeColConstraint.setMinWidth(170);
            timeColConstraint.setPrefWidth(190);
            grid.getColumnConstraints().add(timeColConstraint);
        }

        // Top-Left Corner Header Cell
        Label cornerLabel = new Label("Subject \\ Time");
        cornerLabel.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        cornerLabel.setAlignment(Pos.CENTER);
        cornerLabel.setStyle("-fx-background-color: #1e3a8a; -fx-text-fill: white; -fx-font-weight: bold; " +
                            "-fx-padding: 12; -fx-background-radius: 8; -fx-font-size: 13px;");
        grid.add(cornerLabel, 0, 0);

        // X-Axis Header Cells: Time Slots
        for (int col = 0; col < timeSlots.size(); col++) {
            String timeSlot = timeSlots.get(col);
            boolean isBreakTime = daySlots.stream().anyMatch(
                    s -> (s.getStartTime() + " - " + s.getEndTime()).equals(timeSlot) && s.getSlotType() == SlotType.BREAK);

            VBox timeHeader = new VBox(4);
            timeHeader.setAlignment(Pos.CENTER);
            timeHeader.setPadding(new Insets(10, 8, 10, 8));
            timeHeader.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

            if (isBreakTime) {
                timeHeader.setStyle("-fx-background-color: #e2e8f0; -fx-background-radius: 8; -fx-border-color: #cbd5e1; -fx-border-radius: 8;");
                Label tLabel = new Label(TimetableSlot.formatTimeRange12(timeSlot));
                tLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #475569;");
                Label bLabel = new Label("☕ Rest Interval");
                bLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #64748b;");
                timeHeader.getChildren().addAll(tLabel, bLabel);
            } else {
                timeHeader.setStyle("-fx-background-color: #2563eb; -fx-background-radius: 8;");
                Label tLabel = new Label(TimetableSlot.formatTimeRange12(timeSlot));
                tLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: white;");
                Label bLabel = new Label("Focus Block (30m)");
                bLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #dbeafe;");
                timeHeader.getChildren().addAll(tLabel, bLabel);
            }

            grid.add(timeHeader, col + 1, 0);
        }

        // Y-Axis Rows: Subject Names and Intersecting Topic Cells
        for (int row = 0; row < subjectList.size(); row++) {
            String subjectName = subjectList.get(row);

            // Row Header (Y-Axis)
            VBox rowHeader = new VBox(4);
            rowHeader.setAlignment(Pos.CENTER_LEFT);
            rowHeader.setPadding(new Insets(12, 14, 12, 14));
            rowHeader.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            rowHeader.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 8; " +
                              "-fx-border-color: #cbd5e1; -fx-border-radius: 8;");

            Label sNameLabel = new Label(subjectName);
            sNameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #0f172a;");
            rowHeader.getChildren().add(sNameLabel);

            grid.add(rowHeader, 0, row + 1);

            // Intersecting Cells: Topic / Activity
            for (int col = 0; col < timeSlots.size(); col++) {
                String timeSlot = timeSlots.get(col);

                // Find matching slot for this row and this time column
                TimetableSlot matchingSlot = findMatchingSlot(daySlots, subjectName, timeSlot);

                Node cellContent;
                if (matchingSlot != null) {
                    cellContent = buildActiveGridCell(matchingSlot);
                } else {
                    cellContent = buildEmptyGridCell();
                }

                grid.add(cellContent, col + 1, row + 1);
            }
        }

        // Wrap in ScrollPane so both horizontal and vertical scrolling are fluid
        ScrollPane scrollPane = new ScrollPane(grid);
        scrollPane.setFitToWidth(false);
        scrollPane.setFitToHeight(false);
        scrollPane.setStyle("-fx-background: #f8fafc; -fx-background-color: transparent; -fx-border-color: transparent;");

        centerContentArea.getChildren().add(scrollPane);
    }

    private TimetableSlot findMatchingSlot(List<TimetableSlot> daySlots, String rowCategory, String timeSlot) {
        for (TimetableSlot slot : daySlots) {
            String slotTime = slot.getStartTime() + " - " + slot.getEndTime();
            if (!slotTime.equals(timeSlot)) continue;

            if (rowCategory.equals("☕ Cognitive Breaks (5-10m)") && slot.getSlotType() == SlotType.BREAK) {
                return slot;
            }
            if (rowCategory.equals("🎥 Online Meetings (Zoom)") && slot.getSlotType() == SlotType.ZOOM_MEETING) {
                return slot;
            }
            if (slot.getSlotType() == SlotType.STUDY && rowCategory.equalsIgnoreCase(slot.getSubject())) {
                return slot;
            }
        }
        return null;
    }

    private VBox buildActiveGridCell(TimetableSlot slot) {
        VBox cell = new VBox(6);
        cell.setAlignment(Pos.CENTER_LEFT);
        cell.setPadding(new Insets(10, 12, 10, 12));
        cell.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        if (slot.getSlotType() == SlotType.BREAK) {
            // Cognitive Break Cell (Soft Slate/Gray)
            cell.setStyle("-fx-background-color: #f1f5f9; -fx-background-radius: 8; -fx-border-color: #cbd5e1; -fx-border-radius: 8;");

            Label title = new Label("☕ 10-Min Recharge");
            title.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #475569;");

            Label desc = new Label(slot.getTopic() != null ? slot.getTopic() : "Brain rest interval");
            desc.setStyle("-fx-font-size: 11px; -fx-font-style: italic; -fx-text-fill: #64748b;");
            desc.setWrapText(true);

            cell.getChildren().addAll(title, desc);

        } else if (slot.getSlotType() == SlotType.ZOOM_MEETING) {
            // Zoom Meeting Cell (Soft Lavender/Purple)
            cell.setStyle("-fx-background-color: #faf5ff; -fx-background-radius: 8; -fx-border-color: #c084fc; -fx-border-radius: 8; -fx-border-width: 1.5;");

            Label title = new Label("🎥 " + (slot.getTopic() != null ? slot.getTopic() : "Online Class"));
            title.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #7e22ce;");
            title.setWrapText(true);

            if (slot.getMeetingLink() != null && !slot.getMeetingLink().isBlank()) {
                Button joinBtn = new Button("Join Zoom 🔗");
                joinBtn.setStyle("-fx-background-color: #7e22ce; -fx-text-fill: white; -fx-font-size: 10px; " +
                                "-fx-font-weight: bold; -fx-padding: 3 8; -fx-background-radius: 4; -fx-cursor: hand;");
                joinBtn.setOnAction(e -> {
                    try {
                        Desktop.getDesktop().browse(new URI(slot.getMeetingLink()));
                    } catch (Exception ex) {
                        log.error("Failed to open Zoom link: {}", slot.getMeetingLink(), ex);
                    }
                });
                cell.getChildren().addAll(title, joinBtn);
            } else {
                cell.getChildren().add(title);
            }

        } else {
            // Study Block Cell (Color-Coded by Topic Priority)
            String topic = slot.getTopic() != null ? slot.getTopic() : "Topic Focus";

            // Soft Mint Green with refined border
            cell.setStyle("-fx-background-color: #f0fdf4; -fx-background-radius: 8; -fx-border-color: #86efac; -fx-border-radius: 8;");

            Label badge = new Label("📚 STUDY BLOCK");
            badge.setStyle("-fx-background-color: #dcfce7; -fx-text-fill: #15803d; -fx-font-size: 9px; " +
                          "-fx-font-weight: bold; -fx-padding: 2 6; -fx-background-radius: 4;");

            Label topicLabel = new Label(topic);
            topicLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #0f172a;");
            topicLabel.setWrapText(true);

            cell.getChildren().addAll(badge, topicLabel);
        }

        return cell;
    }

    private VBox buildEmptyGridCell() {
        VBox emptyCell = new VBox();
        emptyCell.setAlignment(Pos.CENTER);
        emptyCell.setPadding(new Insets(10));
        emptyCell.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        emptyCell.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 8; -fx-border-color: #f1f5f9; -fx-border-radius: 8;");

        Label dash = new Label("—");
        dash.setStyle("-fx-text-fill: #cbd5e1; -fx-font-size: 14px;");
        emptyCell.getChildren().add(dash);
        return emptyCell;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2. THE CHRONOLOGICAL LIST VIEW (Fallback / Sequential View)
    // ──────────────────────────────────────────────────────────────────────────

    private void renderChronologicalListView() {
        centerContentArea.getChildren().clear();

        TableView<TimetableSlot> tableView = new TableView<>();
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setStyle("-fx-background-color: white; -fx-border-color: #cbd5e1; -fx-border-radius: 8;");

        TableColumn<TimetableSlot, String> dayCol = new TableColumn<>("Day");
        dayCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDayOfWeek()));
        dayCol.setMinWidth(95);
        dayCol.setMaxWidth(120);

        TableColumn<TimetableSlot, String> startCol = new TableColumn<>("Start");
        startCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStartTime()));
        startCol.setMinWidth(75);
        startCol.setMaxWidth(90);

        TableColumn<TimetableSlot, String> endCol = new TableColumn<>("End");
        endCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getEndTime()));
        endCol.setMinWidth(75);
        endCol.setMaxWidth(90);

        TableColumn<TimetableSlot, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getSlotType() != null ? data.getValue().getSlotType().name() : ""));
        typeCol.setMinWidth(110);
        typeCol.setMaxWidth(130);

        TableColumn<TimetableSlot, String> subjectCol = new TableColumn<>("Subject");
        subjectCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getSubject() != null && !data.getValue().getSubject().isBlank()
                        ? data.getValue().getSubject() : "—"));
        subjectCol.setMinWidth(130);

        TableColumn<TimetableSlot, String> topicCol = new TableColumn<>("Topic / Details");
        topicCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getTopic() != null && !data.getValue().getTopic().isBlank()
                        ? data.getValue().getTopic() : "—"));
        topicCol.setMinWidth(220);

        TableColumn<TimetableSlot, String> linkCol = new TableColumn<>("Action");
        linkCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getMeetingLink()));
        linkCol.setMinWidth(130);
        linkCol.setMaxWidth(160);

        linkCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String link, boolean empty) {
                super.updateItem(link, empty);
                if (empty || link == null || link.isBlank()) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Button joinBtn = new Button("🎥 Open Zoom");
                    joinBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; " +
                                    "-fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 4 10; " +
                                    "-fx-background-radius: 6; -fx-cursor: hand;");
                    joinBtn.setOnAction(e -> {
                        try {
                            Desktop.getDesktop().browse(new URI(link));
                        } catch (Exception ex) {
                            log.error("Failed to open meeting link: {}", link, ex);
                        }
                    });
                    setGraphic(joinBtn);
                    setText(null);
                }
            }
        });

        tableView.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(TimetableSlot item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setStyle("");
                } else if (item.getSlotType() == SlotType.BREAK) {
                    setStyle("-fx-background-color: #f1f5f9; -fx-font-style: italic; -fx-text-fill: #64748b;");
                } else if (item.getSlotType() == SlotType.ZOOM_MEETING) {
                    setStyle("-fx-background-color: #faf5ff; -fx-font-weight: bold; -fx-text-fill: #6b21a8;");
                } else {
                    setStyle("-fx-background-color: #f0fdf4;");
                }
            }
        });

        tableView.getColumns().addAll(List.of(dayCol, startCol, endCol, typeCol, subjectCol, topicCol, linkCol));
        tableView.setItems(FXCollections.observableArrayList(allSlots));

        VBox box = new VBox(tableView);
        box.setPadding(new Insets(15, 30, 15, 30));
        VBox.setVgrow(tableView, Priority.ALWAYS);

        centerContentArea.getChildren().add(box);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Bottom Action Controls
    // ──────────────────────────────────────────────────────────────────────────

    private HBox buildFooterControls() {
        HBox footer = new HBox(12);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(14, 30, 16, 30));
        footer.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e2e8f0; -fx-border-width: 1 0 0 0;");

        Label exportLabel = new Label("Export Master Plan:");
        exportLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #334155;");

        Button exportPdfBtn = new Button("📄 Export to PDF");
        exportPdfBtn.setStyle("-fx-background-color: #dc2626; -fx-text-fill: white; -fx-font-weight: bold; " +
                              "-fx-padding: 8 16; -fx-background-radius: 6; -fx-cursor: hand;");
        exportPdfBtn.setOnAction(e -> handleExport("PDF"));

        Button exportExcelBtn = new Button("📊 Export to Excel (.xlsx)");
        exportExcelBtn.setStyle("-fx-background-color: #16a34a; -fx-text-fill: white; -fx-font-weight: bold; " +
                                "-fx-padding: 8 16; -fx-background-radius: 6; -fx-cursor: hand;");
        exportExcelBtn.setOnAction(e -> handleExport("EXCEL"));

        Button exportCsvBtn = new Button("📝 Export to CSV");
        exportCsvBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; " +
                              "-fx-padding: 8 16; -fx-background-radius: 6; -fx-cursor: hand;");
        exportCsvBtn.setOnAction(e -> handleExport("CSV"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        footer.getChildren().addAll(exportLabel, spacer, exportPdfBtn, exportExcelBtn, exportCsvBtn);
        return footer;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Data Loading & Export Logic
    // ──────────────────────────────────────────────────────────────────────────

    private void loadTimetableData() {
        TimetableSlotRepository slotRepo = springContext.getBean(TimetableSlotRepository.class);
        allSlots = slotRepo.findByUserOrderByDayOfWeekAscStartTimeAsc(user);
        log.info("TimetableGridView: Loaded {} slots for user '{}'", allSlots.size(), user.getUsername());

        // Select the first day with slots, or default to Monday
        if (!allSlots.isEmpty() && allSlots.get(0).getDayOfWeek() != null) {
            selectedDay = capitalize(allSlots.get(0).getDayOfWeek());
        }

        buildDaySelectorButtons();
        renderCurrentDayMatrix();
    }

    private void handleExport(String format) {
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Select Destination Folder for " + format + " Export");
        File selectedDir = dirChooser.showDialog(primaryStage);

        if (selectedDir == null) return;

        ExportService exportService = springContext.getBean(ExportService.class);
        String safeName = user.getUsername().replaceAll("[^a-zA-Z0-9_-]", "_");

        try {
            File exportedFile;
            switch (format) {
                case "PDF" -> {
                    File target = new File(selectedDir, "Timetable_" + safeName + ".pdf");
                    exportedFile = exportService.exportToPdf(user, allSlots, target);
                }
                case "EXCEL" -> {
                    File target = new File(selectedDir, "Timetable_" + safeName + ".xlsx");
                    exportedFile = exportService.exportToExcel(user, allSlots, target);
                }
                case "CSV" -> {
                    File target = new File(selectedDir, "Timetable_" + safeName + ".csv");
                    exportedFile = exportService.exportToCsv(user, allSlots, target);
                }
                default -> throw new IllegalArgumentException("Unknown format: " + format);
            }

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Export Succeeded");
            alert.setHeaderText(format + " Timetable Exported Successfully!");
            alert.setContentText("File saved to:\n" + exportedFile.getAbsolutePath());
            alert.showAndWait();

        } catch (Exception ex) {
            log.error("Failed to export timetable as {}", format, ex);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Export Failed");
            alert.setHeaderText("Error Exporting " + format);
            alert.setContentText(ex.getMessage());
            alert.showAndWait();
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isBlank()) return "Monday";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}
