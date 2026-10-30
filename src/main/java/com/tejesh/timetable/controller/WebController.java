package com.tejesh.timetable.controller;

import com.tejesh.timetable.dto.CompanionStatusDto;
import com.tejesh.timetable.model.ProficiencyLevel;
import com.tejesh.timetable.model.SlotType;
import com.tejesh.timetable.model.SubjectInput;
import com.tejesh.timetable.model.TimetableSlot;
import com.tejesh.timetable.model.User;
import com.tejesh.timetable.repository.TimetableSlotRepository;
import com.tejesh.timetable.repository.UserRepository;
import com.tejesh.timetable.service.ExportService;
import com.tejesh.timetable.service.NotificationService;
import com.tejesh.timetable.service.TimetableManagerService;
import com.tejesh.timetable.service.VoiceInputService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.File;
import java.io.IOException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <h2>WebController – Spring Boot MVC & Thymeleaf Web Controller</h2>
 *
 * <p>Serves the web application frontend routes:
 * <ul>
 *   <li>{@code /login} &amp; {@code /register} – User authentication &amp; registration.</li>
 *   <li>{@code /dashboard} – Multi-format input (subjects, topics, PDF upload, study constraints).</li>
 *   <li>{@code /generate} – Submits inputs to the Gemini AI Brain and redirects to the timetable.</li>
 *   <li>{@code /timetable} – Renders the Fixed Master Matrix Grid (X: Time Slots, Y: Subjects).</li>
 *   <li>{@code /export/*} – Direct browser file downloads for PDF, Excel (.xlsx), and CSV.</li>
 * </ul>
 */
@Controller
public class WebController {

    private static final Logger log = LoggerFactory.getLogger(WebController.class);

    private final TimetableManagerService timetableManagerService;
    private final TimetableSlotRepository slotRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ExportService exportService;
    private final NotificationService notificationService;
    private final VoiceInputService voiceInputService;

    public WebController(TimetableManagerService timetableManagerService,
                          TimetableSlotRepository slotRepository,
                          UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          ExportService exportService,
                          NotificationService notificationService,
                          VoiceInputService voiceInputService) {
        this.timetableManagerService = timetableManagerService;
        this.slotRepository = slotRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.exportService = exportService;
        this.notificationService = notificationService;
        this.voiceInputService = voiceInputService;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helper: Resolve Logged-in User
    // ──────────────────────────────────────────────────────────────────────────

    private User getLoggedInUser(HttpSession session, Authentication authentication) {
        User user = (User) session.getAttribute("user");
        if (user == null && authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getName())) {
            user = userRepository.findByUsername(authentication.getName()).orElse(null);
            if (user != null) {
                session.setAttribute("user", user);
            }
        }
        return user;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 1. Input Dashboard
    // ──────────────────────────────────────────────────────────────────────────

    @GetMapping("/dashboard")
    public String showDashboard(Authentication authentication, HttpSession session, Model model) {
        User user = getLoggedInUser(session, authentication);
        if (user == null) {
            return "redirect:/login";
        }

        model.addAttribute("user", user);
        model.addAttribute("proficiencyLevels", ProficiencyLevel.values());

        // Check if user already has an existing timetable
        List<TimetableSlot> existingSlots = slotRepository.findByUserOrderByDayOfWeekAscStartTimeAsc(user);
        model.addAttribute("hasExistingTimetable", !existingSlots.isEmpty());
        model.addAttribute("slotCount", existingSlots.size());

        return "dashboard";
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3. AI Timetable Generation & Voice Transcription
    // ──────────────────────────────────────────────────────────────────────────

    @PostMapping("/api/transcribe-audio")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> transcribeAudio(
            @RequestParam("audio") MultipartFile audioFile,
            Authentication authentication,
            HttpSession session) {

        User user = getLoggedInUser(session, authentication);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Unauthorized"));
        }

        if (audioFile == null || audioFile.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "No audio clip detected. Please speak or upload an audio file."));
        }

        File tempAudio = null;
        try {
            String originalFilename = audioFile.getOriginalFilename();
            String ext = ".wav";
            if (originalFilename != null && originalFilename.contains(".")) {
                ext = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            tempAudio = File.createTempFile("voice_input_", ext);
            audioFile.transferTo(tempAudio);

            String mimeType = audioFile.getContentType();
            log.info("WebController: Transcribing uploaded audio '{}' ({} bytes, MIME: {}) via Gemini...",
                    originalFilename, tempAudio.length(), mimeType);

            String transcript = voiceInputService.transcribeAudioWithGemini(tempAudio, mimeType);

            if (transcript != null && !transcript.isBlank()) {
                log.info("WebController: Voice transcribed successfully: \"{}\"", transcript);
                return ResponseEntity.ok(Map.of("success", true, "transcript", transcript));
            } else {
                return ResponseEntity.ok(Map.of("success", false, "error", "Audio received, but no speech could be recognized. Please try speaking closer to the microphone."));
            }
        } catch (Exception e) {
            log.error("WebController: Audio transcription error", e);
            return ResponseEntity.status(500).body(Map.of("success", false, "error", "Transcription error: " + e.getMessage()));
        } finally {
            if (tempAudio != null && tempAudio.exists()) {
                tempAudio.delete();
            }
        }
    }

    @PostMapping("/generate")
    public String handleGenerate(@RequestParam(required = false) List<String> subjectName,
                                 @RequestParam(required = false) List<String> topics,
                                 @RequestParam(required = false) List<ProficiencyLevel> proficiency,
                                 @RequestParam(value = "pdfFile", required = false) MultipartFile pdfFile,
                                 @RequestParam(value = "voiceFile", required = false) MultipartFile voiceFile,
                                 @RequestParam(value = "extraInstructions", required = false) String extraInstructions,
                                 @RequestParam(value = "includeMealBreaks", defaultValue = "true") boolean includeMealBreaks,
                                 @RequestParam(value = "lunchTime", required = false, defaultValue = "13:00") String lunchTime,
                                 @RequestParam(value = "lunchDuration", required = false, defaultValue = "45") Integer lunchDuration,
                                 Authentication authentication,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes) {

        User user = getLoggedInUser(session, authentication);
        if (user == null) {
            return "redirect:/login";
        }

        // Assemble subjects list
        List<SubjectInput> subjects = new ArrayList<>();
        if (subjectName != null && !subjectName.isEmpty()) {
            for (int i = 0; i < subjectName.size(); i++) {
                String name = subjectName.get(i).trim();
                String topicStr = (topics != null && i < topics.size()) ? topics.get(i).trim() : "";
                ProficiencyLevel level = (proficiency != null && i < proficiency.size()) ? proficiency.get(i) : ProficiencyLevel.AVERAGE;

                if (!name.isEmpty()) {
                    subjects.add(new SubjectInput(name, topicStr, level));
                }
            }
        }

        if (subjects.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please provide at least one subject with topics.");
            return "redirect:/dashboard";
        }

        File tempPdf = null;
        File tempVoice = null;
        try {
            // Save uploaded PDF temporarily if present
            if (pdfFile != null && !pdfFile.isEmpty()) {
                tempPdf = File.createTempFile("uploaded_syllabus_", ".pdf");
                pdfFile.transferTo(tempPdf);
                log.info("WebController: Uploaded PDF saved to '{}' ({} bytes)",
                        tempPdf.getAbsolutePath(), tempPdf.length());
            }

            // Save uploaded Voice file temporarily if present
            if (voiceFile != null && !voiceFile.isEmpty()) {
                String vName = voiceFile.getOriginalFilename();
                String vExt = (vName != null && vName.contains(".")) ? vName.substring(vName.lastIndexOf(".")) : ".wav";
                tempVoice = File.createTempFile("uploaded_voice_", vExt);
                voiceFile.transferTo(tempVoice);
                log.info("WebController: Uploaded Voice note saved to '{}' ({} bytes)",
                        tempVoice.getAbsolutePath(), tempVoice.length());
            }

            log.info("WebController: Triggering AI timetable generation for user '{}' with {} subjects (Meal breaks: {})...",
                    user.getUsername(), subjects.size(), includeMealBreaks);

            LocalTime parsedLunchTime = null;
            if (lunchTime != null && !lunchTime.isBlank()) {
                try {
                    parsedLunchTime = LocalTime.parse(lunchTime.trim());
                } catch (Exception ignored) {}
            }
            if (parsedLunchTime == null) parsedLunchTime = LocalTime.of(13, 0);

            timetableManagerService.processInputsAndSaveTimetable(
                    user,
                    subjects,
                    tempPdf,
                    tempVoice,
                    extraInstructions,
                    includeMealBreaks,
                    parsedLunchTime,
                    lunchDuration != null ? lunchDuration : 45
            );

            log.info("WebController: AI generation completed successfully.");
            return "redirect:/timetable";

        } catch (Exception e) {
            log.error("WebController: AI timetable generation failed", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Generation failed: " + e.getMessage());
            return "redirect:/dashboard";
        } finally {
            if (tempPdf != null && tempPdf.exists()) {
                tempPdf.delete();
            }
            if (tempVoice != null && tempVoice.exists()) {
                tempVoice.delete();
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 4. Fixed Master Timetable Grid (X: Time Slots, Y: Subjects)
    // ──────────────────────────────────────────────────────────────────────────

    @GetMapping("/timetable")
    public String showTimetable(@RequestParam(required = false) String day,
                                Authentication authentication,
                                HttpSession session,
                                Model model) {

        User user = getLoggedInUser(session, authentication);
        if (user == null) {
            return "redirect:/login";
        }

        List<TimetableSlot> allSlots = slotRepository.findByUserOrderByDayOfWeekAscStartTimeAsc(user);
        if (allSlots.isEmpty()) {
            return "redirect:/dashboard";
        }

        // Days list
        List<String> allDays = List.of("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday");

        // Determine selected day (default to first day with slots or Monday)
        String activeDay = (day != null && !day.isBlank()) ? capitalize(day) : capitalize(allSlots.get(0).getDayOfWeek());
        if (activeDay == null || activeDay.isBlank()) activeDay = "Monday";

        // Filter slots for active day
        final String targetDay = activeDay;
        List<TimetableSlot> daySlots = allSlots.stream()
                .filter(s -> s.getDayOfWeek() != null && s.getDayOfWeek().equalsIgnoreCase(targetDay))
                .sorted((a, b) -> a.getStartTime().compareTo(b.getStartTime()))
                .collect(Collectors.toList());

        // Extract X-Axis: Unique Time Slots
        List<String> timeSlots = daySlots.stream()
                .map(s -> s.getStartTime() + " - " + s.getEndTime())
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        // Extract Y-Axis: Unique Subject / Category Rows
        Set<String> subjectRows = new LinkedHashSet<>();
        for (TimetableSlot s : daySlots) {
            if (s.getSlotType() == SlotType.STUDY && s.getSubject() != null && !s.getSubject().isBlank()) {
                subjectRows.add(s.getSubject());
            }
        }
        if (daySlots.stream().anyMatch(s -> s.getSlotType() != null && s.getSlotType().isMeal())) {
            subjectRows.add("🍽️ Nourishment & Meal Breaks");
        }
        if (daySlots.stream().anyMatch(s -> s.getSlotType() == SlotType.ZOOM_MEETING)) {
            subjectRows.add("🎥 Online Meetings (Zoom)");
        }
        if (daySlots.stream().anyMatch(s -> s.getSlotType() == SlotType.BREAK)) {
            subjectRows.add("☕ Cognitive Breaks (5-10m)");
        }

        // Build 2D Matrix Grid: Map<SubjectRow, Map<TimeSlot, TimetableSlot>>
        Map<String, Map<String, TimetableSlot>> matrix = new LinkedHashMap<>();
        for (String subjectRow : subjectRows) {
            Map<String, TimetableSlot> colMap = new LinkedHashMap<>();
            for (String ts : timeSlots) {
                TimetableSlot match = findMatchingSlot(daySlots, subjectRow, ts);
                colMap.put(ts, match);
            }
            matrix.put(subjectRow, colMap);
        }

        // Statistics
        long studyCount = allSlots.stream().filter(s -> s.getSlotType() == SlotType.STUDY).count();
        long breakCount = allSlots.stream().filter(s -> s.getSlotType() == SlotType.BREAK).count();
        long zoomCount  = allSlots.stream().filter(s -> s.getSlotType() == SlotType.ZOOM_MEETING).count();
        long mealCount  = allSlots.stream().filter(s -> s.getSlotType() != null && s.getSlotType().isMeal()).count();

        model.addAttribute("user", user);
        model.addAttribute("allSlots", allSlots);
        model.addAttribute("daySlots", daySlots);
        model.addAttribute("allDays", allDays);
        model.addAttribute("activeDay", activeDay);
        model.addAttribute("timeSlots", timeSlots);
        model.addAttribute("matrix", matrix);
        model.addAttribute("studyCount", studyCount);
        model.addAttribute("breakCount", breakCount);
        model.addAttribute("zoomCount", zoomCount);
        model.addAttribute("mealCount", mealCount);

        return "timetable";
    }

    private TimetableSlot findMatchingSlot(List<TimetableSlot> daySlots, String rowCategory, String timeSlot) {
        for (TimetableSlot slot : daySlots) {
            String slotTime = slot.getStartTime() + " - " + slot.getEndTime();
            if (!slotTime.equals(timeSlot)) continue;

            if (rowCategory.equals("🍽️ Nourishment & Meal Breaks") && slot.getSlotType() != null && slot.getSlotType().isMeal()) {
                return slot;
            }
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

    // ──────────────────────────────────────────────────────────────────────────
    // 5. Browser File Downloads (PDF, Excel, CSV)
    // ──────────────────────────────────────────────────────────────────────────

    @GetMapping("/export/pdf")
    public ResponseEntity<Resource> exportPdf(Authentication authentication, HttpSession session) throws IOException {
        User user = getLoggedInUser(session, authentication);
        if (user == null) return ResponseEntity.badRequest().build();

        List<TimetableSlot> slots = slotRepository.findByUserOrderByDayOfWeekAscStartTimeAsc(user);
        File tempFile = File.createTempFile("Timetable_" + user.getUsername() + "_", ".pdf");
        exportService.exportToPdf(user, slots, tempFile);

        return buildFileResponse(tempFile, "Timetable_" + user.getUsername() + ".pdf", "application/pdf");
    }

    @GetMapping("/export/excel")
    public ResponseEntity<Resource> exportExcel(Authentication authentication, HttpSession session) throws IOException {
        User user = getLoggedInUser(session, authentication);
        if (user == null) return ResponseEntity.badRequest().build();

        List<TimetableSlot> slots = slotRepository.findByUserOrderByDayOfWeekAscStartTimeAsc(user);
        File tempFile = File.createTempFile("Timetable_" + user.getUsername() + "_", ".xlsx");
        exportService.exportToExcel(user, slots, tempFile);

        return buildFileResponse(tempFile, "Timetable_" + user.getUsername() + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    @GetMapping("/export/csv")
    public ResponseEntity<Resource> exportCsv(Authentication authentication, HttpSession session) throws IOException {
        User user = getLoggedInUser(session, authentication);
        if (user == null) return ResponseEntity.badRequest().build();

        List<TimetableSlot> slots = slotRepository.findByUserOrderByDayOfWeekAscStartTimeAsc(user);
        File tempFile = File.createTempFile("Timetable_" + user.getUsername() + "_", ".csv");
        exportService.exportToCsv(user, slots, tempFile);

        return buildFileResponse(tempFile, "Timetable_" + user.getUsername() + ".csv", "text/csv");
    }

    private ResponseEntity<Resource> buildFileResponse(File file, String filename, String contentType) {
        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 6. Native Alarm Trigger Test
    // ──────────────────────────────────────────────────────────────────────────

    @PostMapping("/test-alarm")
    public String triggerTestAlarm(Authentication authentication, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = getLoggedInUser(session, authentication);
        if (user != null) {
            notificationService.sendDesktopAlert(
                    "🔔 Study Alert Test",
                    "Native desktop alarm & audio beep triggered from the Web Application for " + user.getUsername() + "!"
            );
            redirectAttributes.addFlashAttribute("successMessage", "Desktop audio beep and system alert sent!");
        }
        return "redirect:/timetable";
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 7. Live Study Companion & Dynamic Timetable APIs
    // ──────────────────────────────────────────────────────────────────────────

    @GetMapping("/api/companion/status")
    @ResponseBody
    public ResponseEntity<CompanionStatusDto> getCompanionStatus(
            @RequestParam(value = "day", required = false) String clientDay,
            @RequestParam(value = "time", required = false) String clientTime,
            Authentication authentication,
            HttpSession session) {

        User user = getLoggedInUser(session, authentication);
        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        CompanionStatusDto status = timetableManagerService.getCurrentAndUpcomingStatus(user, clientDay, clientTime);
        return ResponseEntity.ok(status);
    }

    @PostMapping("/api/companion/extend")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> extendSlot(
            @RequestParam("slotId") Long slotId,
            @RequestParam(value = "minutes", defaultValue = "30") int minutes,
            Authentication authentication,
            HttpSession session) {

        User user = getLoggedInUser(session, authentication);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Unauthorized"));
        }

        try {
            TimetableSlot updated = timetableManagerService.extendSlot(user, slotId, minutes);
            log.info("WebController: Successfully extended slot {} by {}m for {}", slotId, minutes, user.getUsername());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "slotId", updated.getId(),
                    "endTime", updated.getEndTime(),
                    "message", "Extended " + updated.getSubject() + " until " + updated.getEndTime() + "!"
            ));
        } catch (Exception e) {
            log.error("WebController: Failed to extend slot", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/api/companion/switch")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> switchSlotSubject(
            @RequestParam("slotId") Long slotId,
            @RequestParam("subject") String newSubject,
            @RequestParam(value = "topic", required = false) String newTopic,
            Authentication authentication,
            HttpSession session) {

        User user = getLoggedInUser(session, authentication);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Unauthorized"));
        }

        try {
            TimetableSlot updated = timetableManagerService.switchSlotSubject(user, slotId, newSubject, newTopic);
            log.info("WebController: Successfully switched slot {} to '{}' for {}", slotId, newSubject, user.getUsername());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "slotId", updated.getId(),
                    "subject", updated.getSubject(),
                    "topic", updated.getTopic(),
                    "message", "Switched session to " + updated.getSubject() + " (" + updated.getTopic() + ")!"
            ));
        } catch (Exception e) {
            log.error("WebController: Failed to switch slot subject", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/api/companion/break")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> convertToBreak(
            @RequestParam("slotId") Long slotId,
            @RequestParam(value = "minutes", defaultValue = "10") int minutes,
            Authentication authentication,
            HttpSession session) {

        User user = getLoggedInUser(session, authentication);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Unauthorized"));
        }

        try {
            TimetableSlot updated = timetableManagerService.convertSlotToBreak(user, slotId, minutes);
            log.info("WebController: Successfully converted slot {} to break for {}", slotId, user.getUsername());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "slotId", updated.getId(),
                    "slotType", "BREAK",
                    "endTime", updated.getEndTime(),
                    "message", "Break started! Enjoy your " + minutes + "-minute cognitive refresh."
            ));
        } catch (Exception e) {
            log.error("WebController: Failed to convert slot to break", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isBlank()) return "Monday";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ENGLISH);
    }
}
