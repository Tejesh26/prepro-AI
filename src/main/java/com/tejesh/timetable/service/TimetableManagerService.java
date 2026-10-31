package com.tejesh.timetable.service;

import com.tejesh.timetable.dto.CompanionStatusDto;
import com.tejesh.timetable.model.SlotType;
import com.tejesh.timetable.model.SubjectInput;
import com.tejesh.timetable.model.TimetableSlot;
import com.tejesh.timetable.model.User;
import com.tejesh.timetable.model.ZoomMeeting;
import com.tejesh.timetable.repository.TimetableSlotRepository;
import com.tejesh.timetable.repository.UserRepository;
import com.tejesh.timetable.repository.ZoomMeetingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <h2>TimetableManagerService – Central Workflow Coordinator</h2>
 *
 * <p>This service is the single orchestration point that ties together every
 * Phase 1 and Phase 2 component into one end-to-end pipeline:
 *
 * <pre>
 *  [User Auth]  →  [PDF/Voice Input Parsing]  →  [AI Brain]  →  [DB Persistence]
 *       ↑                    ↑                        ↑                ↑
 *  UserRepository     PdfExtractorService      AIBrainService   TimetableSlotRepository
 *                     VoiceInputService                         ZoomMeetingRepository
 * </pre>
 *
 * <h3>Responsibilities</h3>
 * <ol>
 *   <li><b>Authentication:</b> {@link #loginOrRegister} — checks H2 for an existing
 *       User; creates one if missing (register-on-first-login pattern).</li>
 *   <li><b>Pipeline:</b> {@link #processInputsAndSaveTimetable} — combines PDF text,
 *       voice transcript, and typed instructions; calls the AI Brain; saves results
 *       to H2; extracts and persists Zoom meetings separately.</li>
 * </ol>
 */
@Service
public class TimetableManagerService {

    private static final Logger log = LoggerFactory.getLogger(TimetableManagerService.class);

    // ──────────────────────────────────────────────────────────────────────────
    // Injected dependencies (constructor injection — preferred for testability)
    // ──────────────────────────────────────────────────────────────────────────

    private final AIBrainService           aiBrainService;
    private final MealBreakService         mealBreakService;
    private final PdfExtractorService      pdfExtractorService;
    private final VoiceInputService        voiceInputService;
    private final ExportService            exportService;
    private final UserRepository           userRepository;
    private final TimetableSlotRepository  slotRepository;
    private final ZoomMeetingRepository    zoomMeetingRepository;
    private final PasswordEncoder          passwordEncoder;

    public TimetableManagerService(AIBrainService aiBrainService,
                                   MealBreakService mealBreakService,
                                   PdfExtractorService pdfExtractorService,
                                   VoiceInputService voiceInputService,
                                   ExportService exportService,
                                   UserRepository userRepository,
                                   TimetableSlotRepository slotRepository,
                                   ZoomMeetingRepository zoomMeetingRepository,
                                   PasswordEncoder passwordEncoder) {
        this.aiBrainService        = aiBrainService;
        this.mealBreakService      = mealBreakService;
        this.pdfExtractorService   = pdfExtractorService;
        this.voiceInputService     = voiceInputService;
        this.exportService         = exportService;
        this.userRepository        = userRepository;
        this.slotRepository        = slotRepository;
        this.zoomMeetingRepository = zoomMeetingRepository;
        this.passwordEncoder       = passwordEncoder;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  1. AUTHENTICATION – Login or Register
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Implements a register-on-first-login pattern:
     * <ul>
     *   <li>If a {@link User} with {@code username} exists in H2 → returns it
     *       (password match is skipped here; full auth is Phase 4).</li>
     *   <li>If not found → creates a new {@link User} and persists it to H2.</li>
     * </ul>
     *
     * @param username    unique student identifier (e.g., "Tejesh")
     * @param password    student's password (plain-text in Phase 2)
     * @param examTarget  exam the student is preparing for (e.g., "JEE Advanced")
     * @return the persisted {@link User} entity (with generated {@code id})
     */
    @Transactional
    public User loginOrRegister(String username, String password, String examTarget) {
        log.info("TimetableManagerService: loginOrRegister called for username='{}'", username);

        Optional<User> existing = userRepository.findByUsername(username);

        if (existing.isPresent()) {
            User user = existing.get();
            log.info("TimetableManagerService: Existing user found → {}", user);
            return user;
        }

        // New user — register them with encoded password
        String hashed = (passwordEncoder != null) ? passwordEncoder.encode(password) : password;
        User newUser = new User(username, hashed, examTarget);
        User saved   = userRepository.save(newUser);
        log.info("TimetableManagerService: New user registered → {}", saved);
        return saved;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  2. MAIN PIPELINE – Process Inputs → AI → Save to DB
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * The complete input-to-persistence pipeline:
     *
     * <ol>
     *   <li><b>PDF extraction</b> — if {@code optionalPdfFile} is provided, extract
     *       syllabus text with {@link PdfExtractorService}.</li>
     *   <li><b>Voice transcription</b> — if {@code optionalVoiceFile} is provided,
     *       transcribe it with Gemini multimodal via {@link VoiceInputService}
     *       and append the transcript to the user's typed instructions.</li>
     *   <li><b>AI generation</b> — call {@link AIBrainService#generatePersonalizedTimetable}
     *       with all combined inputs.</li>
     *   <li><b>Clear old schedule</b> — delete any previously saved slots for this
     *       user from H2 so old data doesn't accumulate.</li>
     *   <li><b>Link & save slots</b> — set {@code user} on every generated
     *       {@link TimetableSlot} and batch-save via {@link TimetableSlotRepository}.</li>
     *   <li><b>Extract & save Zoom meetings</b> — iterate the saved slots, pull out
     *       any with {@code slotType == ZOOM_MEETING}, and persist them to
     *       {@link ZoomMeetingRepository} for the Phase 5 automation scheduler.</li>
     * </ol>
     *
     * @param user                 the authenticated (or newly registered) student
     * @param subjects             list of subjects with topics and proficiency levels
     * @param optionalPdfFile      syllabus PDF to extract (pass {@code null} to skip)
     * @param optionalVoiceFile    recorded voice WAV to transcribe (pass {@code null} to skip)
     * @param textInstructions     typed constraints (study window, extra notes, Zoom links)
     * @return the complete list of {@link TimetableSlot}s as saved in the database
     */
    @Transactional
    public List<TimetableSlot> processInputsAndSaveTimetable(User user,
                                                            List<SubjectInput> subjects,
                                                            File optionalPdfFile,
                                                            File optionalVoiceFile,
                                                            String textInstructions) {
        return processInputsAndSaveTimetable(user, subjects, optionalPdfFile, optionalVoiceFile,
                textInstructions, true, LocalTime.of(13, 0), 45);
    }

    /**
     * Executes the complete end-to-end timetable generation pipeline:
     * <ol>
     *   <li>Extracts text from uploaded PDF syllabus (if provided).</li>
     *   <li>Transcribes voice note to text with Gemini (if provided).</li>
     *   <li>Calls {@link AIBrainService} to generate the initial study/break schedule.</li>
     *   <li>Inserts automatic meal breaks (Lunch, Snacks, Dinner) via {@link MealBreakService}.</li>
     *   <li>Deletes previous slots and Zoom meetings from H2.</li>
     *   <li>Persists the new {@link TimetableSlot} entities linked to {@code user}.</li>
     *   <li>Extracts and saves Zoom meetings for automated launching.</li>
     * </ol>
     */
    @Transactional
    public List<TimetableSlot> processInputsAndSaveTimetable(User user,
                                                            List<SubjectInput> subjects,
                                                            File optionalPdfFile,
                                                            File optionalVoiceFile,
                                                            String textInstructions,
                                                            boolean includeMealBreaks,
                                                            LocalTime customLunchTime,
                                                            Integer customLunchDuration) {

        log.info("=== TimetableManagerService: Starting full pipeline for user '{}' ===",
                user.getUsername());

        // ── Step 1: PDF Extraction ────────────────────────────────────────────
        String syllabusText = "";
        if (optionalPdfFile != null) {
            log.info("Pipeline Step 1: Extracting PDF text from '{}'", optionalPdfFile.getName());
            syllabusText = pdfExtractorService.extractTextFromPdf(optionalPdfFile);
            log.info("Pipeline Step 1: Extracted {} chars from PDF.", syllabusText.length());
        } else {
            log.info("Pipeline Step 1: No PDF provided — skipping extraction.");
        }

        // ── Step 2: Voice Transcription ───────────────────────────────────────
        String combinedInstructions = (textInstructions != null) ? textInstructions.strip() : "";
        if (optionalVoiceFile != null) {
            log.info("Pipeline Step 2: Transcribing voice file '{}'", optionalVoiceFile.getName());
            String voiceTranscript = voiceInputService.transcribeAudioWithGemini(optionalVoiceFile);
            if (!voiceTranscript.isBlank()) {
                combinedInstructions = combinedInstructions.isBlank()
                        ? voiceTranscript
                        : combinedInstructions + "\n\nAdditional voice instructions:\n" + voiceTranscript;
                log.info("Pipeline Step 2: Voice transcript appended ({} chars).", voiceTranscript.length());
            } else {
                log.warn("Pipeline Step 2: Voice transcription returned empty — using text instructions only.");
            }
        } else {
            log.info("Pipeline Step 2: No voice file provided — skipping transcription.");
        }

        // ── Step 3: AI Brain — Generate Timetable ────────────────────────────
        log.info("Pipeline Step 3: Calling AIBrainService for exam '{}'...", user.getExamTarget());
        List<TimetableSlot> generatedSlots = aiBrainService.generatePersonalizedTimetable(
                user.getExamTarget(),
                subjects,
                combinedInstructions,
                syllabusText
        );
        log.info("Pipeline Step 3: AI Brain returned {} slot(s).", generatedSlots.size());

        // ── Step 3B: Meal Break Insertion (Automatic Lunch, Snacks, Dinner) ──
        if (includeMealBreaks) {
            log.info("Pipeline Step 3B: Inserting automatic meal breaks into generated schedule...");
            generatedSlots = mealBreakService.processWeeklySchedule(
                    generatedSlots,
                    includeMealBreaks,
                    customLunchTime,
                    customLunchDuration,
                    combinedInstructions
            );
            log.info("Pipeline Step 3B: Timetable after meal break insertion contains {} slot(s).",
                    generatedSlots.size());
        } else {
            // Deconflict slots to ensure no two breaks ever overlap even if meal breaks are disabled
            Map<String, List<TimetableSlot>> byDay = new LinkedHashMap<>();
            for (TimetableSlot s : generatedSlots) {
                String d = s.getDayOfWeek() != null ? s.getDayOfWeek() : "Monday";
                byDay.computeIfAbsent(d, k -> new ArrayList<>()).add(s);
            }
            List<TimetableSlot> deconflicted = new ArrayList<>();
            for (List<TimetableSlot> dSlots : byDay.values()) {
                deconflicted.addAll(mealBreakService.deconflictSchedule(dSlots));
            }
            generatedSlots = deconflicted;
        }

        // ── Step 4: Clear Old Schedule ────────────────────────────────────────
        log.info("Pipeline Step 4: Clearing previous timetable and Zoom meetings for user '{}'...",
                user.getUsername());
        slotRepository.deleteByUser(user);
        zoomMeetingRepository.deleteAll(zoomMeetingRepository.findByUser(user));
        log.info("Pipeline Step 4: Old data cleared.");

        // ── Step 5: Link Slots to User & Save ────────────────────────────────
        log.info("Pipeline Step 5: Persisting {} slot(s) to H2 database...", generatedSlots.size());
        List<TimetableSlot> slotsToSave = new ArrayList<>();
        for (TimetableSlot slot : generatedSlots) {
            // Reset any ID the AI might have set — H2 assigns its own auto-increment ID
            slot.setId(null);
            slot.setUser(user);
            slotsToSave.add(slot);
        }
        List<TimetableSlot> savedSlots = slotRepository.saveAll(slotsToSave);
        log.info("Pipeline Step 5: {} slot(s) saved to H2.", savedSlots.size());

        // ── Step 6: Extract & Persist Zoom Meetings ───────────────────────────
        log.info("Pipeline Step 6: Extracting and persisting ZOOM_MEETING slots...");
        List<ZoomMeeting> zoomMeetings = extractZoomMeetings(savedSlots, user);
        if (!zoomMeetings.isEmpty()) {
            zoomMeetingRepository.saveAll(zoomMeetings);
            log.info("Pipeline Step 6: {} Zoom meeting(s) saved to H2.", zoomMeetings.size());
        } else {
            log.info("Pipeline Step 6: No ZOOM_MEETING slots found in generated timetable.");
        }

        log.info("=== TimetableManagerService: Pipeline complete. {} slot(s) ready. ===",
                savedSlots.size());
        return savedSlots;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Scans the saved timetable slots, finds all {@code ZOOM_MEETING} entries,
     * and maps them to {@link ZoomMeeting} entities ready for persistence.
     *
     * <p>Default assumption: all AI-generated Zoom meetings are <em>recurring</em>
     * (i.e., they repeat every week). The user can mark specific ones as non-recurring
     * via the Phase 4 UI.
     *
     * @param slots the complete saved list of timetable slots
     * @param user  the owning user
     * @return list of {@link ZoomMeeting} entities to persist (may be empty)
     */
    private List<ZoomMeeting> extractZoomMeetings(List<TimetableSlot> slots, User user) {
        List<ZoomMeeting> meetings = new ArrayList<>();

        for (TimetableSlot slot : slots) {
            if (slot.getSlotType() != SlotType.ZOOM_MEETING) continue;

            String url   = slot.getMeetingLink();
            String title = (slot.getTopic()   != null && !slot.getTopic().isBlank())
                           ? slot.getTopic()
                           : (slot.getSubject() != null ? slot.getSubject() : "Online Meeting");

            // Guard: a ZOOM_MEETING slot without a link is incomplete but still worth recording
            if (url == null || url.isBlank()) {
                log.warn("Pipeline Step 6: ZOOM_MEETING slot on {} {} has no meetingLink — saving with placeholder URL.",
                        slot.getDayOfWeek(), slot.getStartTime());
                url = "https://zoom.us"; // placeholder
            }

            ZoomMeeting meeting = new ZoomMeeting(
                    title,
                    slot.getDayOfWeek(),
                    slot.getStartTime(),
                    url,
                    true,   // recurring by default
                    user
            );
            meetings.add(meeting);
            log.debug("Pipeline Step 6: Mapped ZOOM_MEETING → {}", meeting);
        }

        return meetings;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  3. EXPORT ORCHESTRATION
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Exports the user's current saved timetable in all three supported formats
     * (CSV, Excel .xlsx, and PDF) into the specified target directory.
     *
     * @param user               the user whose timetable is to be exported
     * @param targetDirectoryPath directory path where export files will be saved
     */
    public void exportUserTimetableAllFormats(User user, String targetDirectoryPath) {
        log.info("TimetableManagerService: Exporting timetable in all formats for user '{}' to '{}'",
                user.getUsername(), targetDirectoryPath);

        List<TimetableSlot> slots = slotRepository.findByUserOrderByDayOfWeekAscStartTimeAsc(user);
        if (slots.isEmpty()) {
            log.warn("TimetableManagerService: No timetable slots found for user '{}' — skipping export.",
                    user.getUsername());
            return;
        }

        File targetDir = new File(targetDirectoryPath);
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }

        String safeUsername = user.getUsername().replaceAll("[^a-zA-Z0-9_-]", "_");

        File csvFile = new File(targetDir, "Timetable_" + safeUsername + ".csv");
        File xlsxFile = new File(targetDir, "Timetable_" + safeUsername + ".xlsx");
        File pdfFile = new File(targetDir, "Timetable_" + safeUsername + ".pdf");

        try {
            exportService.exportToCsv(user, slots, csvFile);
            log.info("TimetableManagerService: Successfully generated CSV -> {}", csvFile.getAbsolutePath());
        } catch (Exception e) {
            log.error("TimetableManagerService: Failed to export CSV: {}", e.getMessage(), e);
        }

        try {
            exportService.exportToExcel(user, slots, xlsxFile);
            log.info("TimetableManagerService: Successfully generated Excel -> {}", xlsxFile.getAbsolutePath());
        } catch (Exception e) {
            log.error("TimetableManagerService: Failed to export Excel: {}", e.getMessage(), e);
        }

        try {
            exportService.exportToPdf(user, slots, pdfFile);
            log.info("TimetableManagerService: Successfully generated PDF -> {}", pdfFile.getAbsolutePath());
        } catch (Exception e) {
            log.error("TimetableManagerService: Failed to export PDF: {}", e.getMessage(), e);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  4. CREATIVE DYNAMIC TIMETABLE & SMART ALARM COMPANION
    // ══════════════════════════════════════════════════════════════════════════

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Calculates the real-time status of the student's study day, including:
     * - Current active slot & remaining minutes
     * - Upcoming slot starting in 1-10 minutes (pre-event smart alarm trigger)
     * - Dynamic prompts letting the user extend, switch, or break
     */
    public CompanionStatusDto getCurrentAndUpcomingStatus(User user, String clientDay, String clientTime) {
        CompanionStatusDto dto = new CompanionStatusDto();

        String day = (clientDay != null && !clientDay.isBlank())
                ? clientDay
                : LocalDate.now().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);

        String timeStr = (clientTime != null && !clientTime.isBlank())
                ? clientTime
                : LocalTime.now().format(HH_MM);

        dto.setCurrentDay(day);
        dto.setCurrentTime(timeStr);

        LocalTime now;
        try {
            now = LocalTime.parse(timeStr);
        } catch (Exception e) {
            now = LocalTime.now();
            timeStr = now.format(HH_MM);
            dto.setCurrentTime(timeStr);
        }

        List<TimetableSlot> allUserSlots = slotRepository.findByUserOrderByDayOfWeekAscStartTimeAsc(user);
        if (allUserSlots.isEmpty()) {
            dto.setHasTimetable(false);
            dto.setSmartPrompt("No timetable generated yet. Head over to the Input Dashboard to create your AI study plan!");
            dto.setSpeechText("");
            dto.setWarningActive(false);
            dto.setAlertType("IDLE");
            return dto;
        }

        dto.setHasTimetable(true);

        // Collect available subjects for user
        List<String> subjects = allUserSlots.stream()
                .filter(s -> s.getSlotType() == SlotType.STUDY && s.getSubject() != null && !s.getSubject().isBlank())
                .map(TimetableSlot::getSubject)
                .distinct()
                .collect(Collectors.toList());
        dto.setAvailableSubjects(subjects);

        // Filter slots for the current active day
        final String matchDay = day;
        List<TimetableSlot> daySlots = allUserSlots.stream()
                .filter(s -> s.getDayOfWeek() != null && s.getDayOfWeek().equalsIgnoreCase(matchDay))
                .sorted((a, b) -> a.getStartTime().compareTo(b.getStartTime()))
                .collect(Collectors.toList());

        TimetableSlot activeSlot = null;
        TimetableSlot upcomingSlot = null;
        long closestStartsIn = Long.MAX_VALUE;

        for (TimetableSlot s : daySlots) {
            try {
                LocalTime start = LocalTime.parse(s.getStartTime());
                LocalTime end = LocalTime.parse(s.getEndTime());

                // Check active slot (inclusive start, exclusive end)
                if ((now.equals(start) || now.isAfter(start)) && now.isBefore(end)) {
                    activeSlot = s;
                }

                // Check upcoming slot starting in the future
                if (start.isAfter(now)) {
                    long diff = Duration.between(now, start).toMinutes();
                    if (diff < closestStartsIn) {
                        closestStartsIn = diff;
                        upcomingSlot = s;
                    }
                }
            } catch (Exception ignored) {}
        }

        // Populate current slot details
        if (activeSlot != null) {
            LocalTime start = LocalTime.parse(activeSlot.getStartTime());
            LocalTime end = LocalTime.parse(activeSlot.getEndTime());
            long remaining = Duration.between(now, end).toMinutes();
            long total = Duration.between(start, end).toMinutes();
            int percent = (total > 0) ? Math.min(100, Math.max(0, (int) (((double) (total - remaining) / total) * 100))) : 0;

            Map<String, Object> map = new HashMap<>();
            map.put("id", activeSlot.getId());
            map.put("subject", activeSlot.getSubject());
            map.put("topic", activeSlot.getTopic());
            map.put("startTime", activeSlot.getStartTime());
            map.put("endTime", activeSlot.getEndTime());
            map.put("slotType", activeSlot.getSlotType().name());
            map.put("remainingMinutes", Math.max(0, remaining));
            map.put("percentCompleted", percent);
            dto.setCurrentSlot(map);
        }

        // Populate upcoming slot details
        if (upcomingSlot != null) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", upcomingSlot.getId());
            map.put("subject", upcomingSlot.getSubject());
            map.put("topic", upcomingSlot.getTopic());
            map.put("startTime", upcomingSlot.getStartTime());
            map.put("endTime", upcomingSlot.getEndTime());
            map.put("slotType", upcomingSlot.getSlotType().name());
            map.put("startsInMinutes", closestStartsIn);
            dto.setUpcomingSlot(map);
        }

        // Build Smart Alerts & Contextual Prompts
        String username = user.getUsername();

        // 1. Pre-Slot Alert: 1 to 10 minutes before upcoming slot
        if (upcomingSlot != null && closestStartsIn >= 1 && closestStartsIn <= 10) {
            dto.setWarningActive(true);
            dto.setAlertType("UPCOMING_SLOT");
            String sub = (upcomingSlot.getSubject() != null && !upcomingSlot.getSubject().isBlank())
                    ? upcomingSlot.getSubject()
                    : upcomingSlot.getSlotType().name();

            if (upcomingSlot.getSlotType() != null && upcomingSlot.getSlotType().isMeal()) {
                dto.setSmartPrompt(String.format("🍽️ Nourishment Alert: %s in %d minute%s (%s - %s)! Time to wrap up and enjoy your food.",
                        sub, closestStartsIn, (closestStartsIn == 1 ? "" : "s"), upcomingSlot.getStartTime(), upcomingSlot.getEndTime()));
                dto.setSpeechText(String.format("Heads up %s, %s in %d minutes.", username, sub, closestStartsIn));
            } else {
                dto.setSmartPrompt(String.format("⏰ Heads up %s! Your next session [%s] starts in %d minutes (%s - %s). Are you ready to dive into %s, or do you want to switch or adjust?",
                        username, sub, closestStartsIn, upcomingSlot.getStartTime(), upcomingSlot.getEndTime(),
                        (upcomingSlot.getTopic() != null ? upcomingSlot.getTopic() : "study topic")));

                dto.setSpeechText(String.format("Hey %s! Your %s session starts in %d minutes. Are you ready to continue, switch subjects, or take a break?",
                        username, sub, closestStartsIn));
            }

        // 2. Ending Soon Alert: Active slot has 1 to 10 minutes remaining
        } else if (activeSlot != null) {
            LocalTime end = LocalTime.parse(activeSlot.getEndTime());
            long remaining = Duration.between(now, end).toMinutes();

            if (remaining >= 0 && remaining <= 10) {
                dto.setWarningActive(true);
                dto.setAlertType("SLOT_ENDING_SOON");
                String sub = (activeSlot.getSubject() != null && !activeSlot.getSubject().isBlank())
                        ? activeSlot.getSubject()
                        : "current session";

                if (activeSlot.getSlotType() == SlotType.STUDY) {
                    dto.setSmartPrompt(String.format("⚡ Smart Focus Alert, %s: Only %d minute%s left in %s! In the flow? Extend this subject until you're bored (+30m / +60m), switch to another subject, or take a brain break!",
                            username, remaining, (remaining == 1 ? "" : "s"), sub));

                    dto.setSpeechText(String.format("Hey %s, you have %d minutes left in %s. Do you want to continue this subject, switch subjects, or take a break?",
                            username, remaining, sub));
                } else if (activeSlot.getSlotType() != null && activeSlot.getSlotType().isMeal()) {
                    dto.setSmartPrompt(String.format("🍽️ %s ending in %d minute%s! Finish up, hydrate, and prepare for your upcoming study session.",
                            sub, remaining, (remaining == 1 ? "" : "s")));
                    dto.setSpeechText(String.format("Hey %s, %d minutes left in your meal break.", username, remaining));
                } else {
                    dto.setSmartPrompt(String.format("☕ Break time ending in %d minute%s! Drink water and get ready for your next study block.",
                            remaining, (remaining == 1 ? "" : "s")));
                    dto.setSpeechText(String.format("Hey %s, %d minutes left in your break.", username, remaining));
                }
            } else if (activeSlot.getSlotType() != null && activeSlot.getSlotType().isMeal()) {
                dto.setWarningActive(false);
                dto.setAlertType("ACTIVE_MEAL");
                dto.setSmartPrompt(String.format("🍽️ Enjoying %s (%s). %d minutes remaining until %s.",
                        activeSlot.getSubject(), activeSlot.getTopic(), remaining, activeSlot.getEndTime()));
                dto.setSpeechText("");
            } else {
                dto.setWarningActive(false);
                dto.setAlertType("ACTIVE_STUDY");
                dto.setSmartPrompt(String.format("📖 Studying %s: %s. %d minutes remaining until %s.",
                        activeSlot.getSubject(), activeSlot.getTopic(), remaining, activeSlot.getEndTime()));
                dto.setSpeechText("");
            }
        } else if (upcomingSlot != null) {
            dto.setWarningActive(false);
            dto.setAlertType("BETWEEN_SLOTS");
            dto.setSmartPrompt(String.format("✨ Up next on %s: %s starting at %s (in %d minutes).",
                    day, upcomingSlot.getSubject(), upcomingSlot.getStartTime(), closestStartsIn));
            dto.setSpeechText("");
        } else {
            dto.setWarningActive(false);
            dto.setAlertType("IDLE");
            dto.setSmartPrompt(String.format("🎉 Great job %s! All scheduled study sessions for %s are complete.", username, day));
            dto.setSpeechText("");
        }

        return dto;
    }

    /**
     * Extends a timetable slot by {@code extraMinutes} (e.g. 30m or 60m),
     * automatically shifting subsequent slots on that day to avoid conflicts.
     */
    @Transactional
    public TimetableSlot extendSlot(User user, Long slotId, int extraMinutes) {
        log.info("TimetableManagerService: Extending slot {} by {} minutes for user '{}'", slotId, extraMinutes, user.getUsername());
        TimetableSlot slot = slotRepository.findByIdAndUser(slotId, user)
                .orElseThrow(() -> new IllegalArgumentException("Slot not found or unauthorized"));

        LocalTime oldEnd = LocalTime.parse(slot.getEndTime());
        LocalTime newEnd = oldEnd.plusMinutes(extraMinutes);
        slot.setEndTime(newEnd.format(HH_MM));

        // Push forward any subsequent slots on the same day that start before or at the new end time
        List<TimetableSlot> daySlots = slotRepository.findByUserAndDayOfWeekIgnoreCaseOrderByStartTimeAsc(user, slot.getDayOfWeek());
        for (TimetableSlot other : daySlots) {
            if (other.getId().equals(slot.getId())) continue;

            LocalTime otherStart = LocalTime.parse(other.getStartTime());
            if (!otherStart.isBefore(oldEnd)) {
                LocalTime otherEnd = LocalTime.parse(other.getEndTime());
                other.setStartTime(otherStart.plusMinutes(extraMinutes).format(HH_MM));
                other.setEndTime(otherEnd.plusMinutes(extraMinutes).format(HH_MM));
                slotRepository.save(other);
            }
        }

        return slotRepository.save(slot);
    }

    /**
     * Swaps the subject of an existing slot (e.g., if the user doesn't like Math right now,
     * they can switch to Physics or Chemistry).
     */
    @Transactional
    public TimetableSlot switchSlotSubject(User user, Long slotId, String newSubject, String newTopic) {
        log.info("TimetableManagerService: Switching slot {} to subject '{}' for user '{}'", slotId, newSubject, user.getUsername());
        TimetableSlot slot = slotRepository.findByIdAndUser(slotId, user)
                .orElseThrow(() -> new IllegalArgumentException("Slot not found or unauthorized"));

        slot.setSlotType(SlotType.STUDY);
        slot.setSubject(newSubject);
        slot.setTopic((newTopic != null && !newTopic.isBlank()) ? newTopic : "Active Study Session");
        return slotRepository.save(slot);
    }

    /**
     * Converts an active or upcoming slot into a Cognitive Break.
     */
    @Transactional
    public TimetableSlot convertSlotToBreak(User user, Long slotId, int breakMinutes) {
        log.info("TimetableManagerService: Converting slot {} to break ({}m) for user '{}'", slotId, breakMinutes, user.getUsername());
        TimetableSlot slot = slotRepository.findByIdAndUser(slotId, user)
                .orElseThrow(() -> new IllegalArgumentException("Slot not found or unauthorized"));

        slot.setSlotType(SlotType.BREAK);
        slot.setSubject("");
        slot.setTopic("☕ Cognitive Break / Mind Reset (" + breakMinutes + "m)");
        LocalTime start = LocalTime.parse(slot.getStartTime());
        slot.setEndTime(start.plusMinutes(breakMinutes).format(HH_MM));
        return slotRepository.save(slot);
    }
}
