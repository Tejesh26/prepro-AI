package com.tejesh.timetable.service;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
    private final PdfExtractorService      pdfExtractorService;
    private final VoiceInputService        voiceInputService;
    private final ExportService            exportService;
    private final UserRepository           userRepository;
    private final TimetableSlotRepository  slotRepository;
    private final ZoomMeetingRepository    zoomMeetingRepository;
    private final PasswordEncoder          passwordEncoder;

    public TimetableManagerService(AIBrainService aiBrainService,
                                   PdfExtractorService pdfExtractorService,
                                   VoiceInputService voiceInputService,
                                   ExportService exportService,
                                   UserRepository userRepository,
                                   TimetableSlotRepository slotRepository,
                                   ZoomMeetingRepository zoomMeetingRepository,
                                   PasswordEncoder passwordEncoder) {
        this.aiBrainService        = aiBrainService;
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
}
