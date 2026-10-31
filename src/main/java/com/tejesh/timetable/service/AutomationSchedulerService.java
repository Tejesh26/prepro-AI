package com.tejesh.timetable.service;

import com.tejesh.timetable.model.SlotType;
import com.tejesh.timetable.model.TimetableSlot;
import com.tejesh.timetable.model.ZoomMeeting;
import com.tejesh.timetable.repository.TimetableSlotRepository;
import com.tejesh.timetable.repository.ZoomMeetingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.awt.Desktop;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * <h2>AutomationSchedulerService – Real-Time Alarm & Zoom Launcher Engine</h2>
 *
 * <p>Runs background scheduling jobs using Spring's {@code @Scheduled}.
 * Every minute, it checks the database for:
 * <ul>
 *   <li><b>10-Minute Pre-Event Warnings:</b> Study prep alerts and Zoom meeting confirmation dialogs.</li>
 *   <li><b>Exact-Time Actions:</b> Immediate study/break reminders and auto-opening Zoom URLs via {@link java.awt.Desktop}.</li>
 * </ul>
 */
@Service
public class AutomationSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(AutomationSchedulerService.class);

    private final TimetableSlotRepository slotRepository;
    private final ZoomMeetingRepository zoomMeetingRepository;
    private final NotificationService notificationService;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    // Tracking sets to prevent duplicate alerts/prompts
    // Key format: "eventId-date-type" e.g. "slot_12_2026-10-21_WARNING_10"
    private final Set<String> processedAlerts = Collections.synchronizedSet(new HashSet<>());
    
    // Meeting IDs confirmed by the user: "meetingId_date"
    private final Set<String> confirmedMeetings = Collections.synchronizedSet(new HashSet<>());
    // Meeting IDs explicitly declined by the user: "meetingId_date"
    private final Set<String> declinedMeetings = Collections.synchronizedSet(new HashSet<>());

    public AutomationSchedulerService(TimetableSlotRepository slotRepository,
                                      ZoomMeetingRepository zoomMeetingRepository,
                                      NotificationService notificationService) {
        this.slotRepository = slotRepository;
        this.zoomMeetingRepository = zoomMeetingRepository;
        this.notificationService = notificationService;
    }

    /**
     * Executes every minute at the start of the minute (second 0).
     */
    @Scheduled(cron = "0 * * * * *")
    public void runScheduleCheck() {
        LocalDate today = LocalDate.now();
        String currentDayOfWeek = today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        LocalTime now = LocalTime.now();
        String currentTimeStr = now.format(TIME_FORMATTER);
        String tenMinutesLaterStr = now.plusMinutes(10).format(TIME_FORMATTER);
        String fiveMinutesLaterStr = now.plusMinutes(5).format(TIME_FORMATTER);

        log.info("AutomationSchedulerService: [Active Monitor] Checking schedule for {} (Now: {}, 10-min target: {}, 5-min target: {})",
                currentDayOfWeek, currentTimeStr, tenMinutesLaterStr, fiveMinutesLaterStr);

        checkTenMinuteWarnings(currentDayOfWeek, today, tenMinutesLaterStr);
        checkFiveMinuteWarnings(currentDayOfWeek, today, fiveMinutesLaterStr);
        checkExactTimeActions(currentDayOfWeek, today, currentTimeStr);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 1. Pre-Event (10 Minutes & 5 Minutes Before) Checks
    // ──────────────────────────────────────────────────────────────────────────

    private void checkFiveMinuteWarnings(String dayOfWeek, LocalDate today, String targetStartTime) {
        // Remind the user 5 minutes before each meal break ("Lunch time in 5 minutes")
        List<TimetableSlot> upcomingSlots = slotRepository.findByDayOfWeekIgnoreCaseAndStartTime(dayOfWeek, targetStartTime);
        for (TimetableSlot slot : upcomingSlots) {
            String alertKey = "slot_" + slot.getId() + "_" + today + "_5MIN_WARN";
            if (processedAlerts.contains(alertKey)) continue;

            if (slot.getSlotType() != null && slot.getSlotType().isMeal()) {
                String mealTitle = (slot.getSubject() != null && !slot.getSubject().isBlank())
                        ? slot.getSubject() : "Meal Break";

                String reminderText = (slot.getSlotType() == SlotType.LUNCH)
                        ? "Lunch time in 5 minutes! Time to step away, eat, and recharge."
                        : mealTitle + " in 5 minutes! Step away and enjoy your nourishment.";

                notificationService.sendDesktopAlert("🍽️ " + mealTitle, reminderText);
                processedAlerts.add(alertKey);
            }
        }
    }

    private void checkTenMinuteWarnings(String dayOfWeek, LocalDate today, String targetStartTime) {
        // 1. Check Timetable Slots starting in 10 minutes
        List<TimetableSlot> upcomingSlots = slotRepository.findByDayOfWeekIgnoreCaseAndStartTime(dayOfWeek, targetStartTime);
        for (TimetableSlot slot : upcomingSlots) {
            String alertKey = "slot_" + slot.getId() + "_" + today + "_10MIN_WARN";
            if (processedAlerts.contains(alertKey)) continue;

            String username = (slot.getUser() != null) ? slot.getUser().getUsername() : "Student";

            if (slot.getSlotType() == SlotType.STUDY) {
                notificationService.sendDesktopAlert(
                        "⏰ 10-Min Smart Alarm",
                        "Hey " + username + ", your " + slot.getSubject() + " session starts in 10 minutes (at " + slot.getFormattedStartTime12() + ")! Ready to study, or would you like to continue, switch subjects, or take a break?"
                );
                processedAlerts.add(alertKey);
            } else if (slot.getSlotType() == SlotType.ZOOM_MEETING) {
                // If the slot is a Zoom meeting from timetable slots
                handleZoomWarning(slot.getId(), username, slot.getTopic(), slot.getFormattedStartTime12(), today, alertKey);
            }
        }

        // 2. Check dedicated ZoomMeeting entities starting in 10 minutes
        List<ZoomMeeting> upcomingMeetings = zoomMeetingRepository.findByDayOfWeekIgnoreCaseAndStartTime(dayOfWeek, targetStartTime);
        for (ZoomMeeting meeting : upcomingMeetings) {
            String alertKey = "meeting_" + meeting.getId() + "_" + today + "_10MIN_WARN";
            if (processedAlerts.contains(alertKey)) continue;

            String username = (meeting.getUser() != null) ? meeting.getUser().getUsername() : "Student";
            handleZoomWarning(meeting.getId(), username, meeting.getMeetingTitle(), targetStartTime, today, alertKey);
        }
    }

    private void handleZoomWarning(Long id, String username, String title, String startTime, LocalDate today, String alertKey) {
        String meetingSessionKey = id + "_" + today;
        new Thread(() -> {
            boolean accepted = notificationService.askMeetingConfirmation(username, title, startTime);
            if (accepted) {
                confirmedMeetings.add(meetingSessionKey);
                declinedMeetings.remove(meetingSessionKey);
            } else {
                declinedMeetings.add(meetingSessionKey);
                confirmedMeetings.remove(meetingSessionKey);
            }
        }).start();

        processedAlerts.add(alertKey);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2. Exact-Time Actions (Right Now)
    // ──────────────────────────────────────────────────────────────────────────

    private void checkExactTimeActions(String dayOfWeek, LocalDate today, String currentTimeStr) {
        // Check Timetable slots starting right now
        List<TimetableSlot> activeSlots = slotRepository.findByDayOfWeekIgnoreCaseAndStartTime(dayOfWeek, currentTimeStr);
        for (TimetableSlot slot : activeSlots) {
            String alertKey = "slot_" + slot.getId() + "_" + today + "_EXACT_NOW";
            if (processedAlerts.contains(alertKey)) continue;

            if (slot.getSlotType() == SlotType.STUDY) {
                notificationService.sendDesktopAlert(
                        "📚 Study Session Started!",
                        "Time to study: " + slot.getSubject() + " - " + slot.getTopic() + " (30 mins focus mode)"
                );
                processedAlerts.add(alertKey);
            } else if (slot.getSlotType() == SlotType.BREAK) {
                notificationService.sendDesktopAlert(
                        "☕ Brain Break Time!",
                        "Brain Break! Step away for 5 to 10 minutes to recharge."
                );
                processedAlerts.add(alertKey);
            } else if (slot.getSlotType() != null && slot.getSlotType().isMeal()) {
                String mealTitle = (slot.getSubject() != null && !slot.getSubject().isBlank())
                        ? slot.getSubject() : "Meal Break";
                notificationService.sendDesktopAlert(
                        "🍽️ " + mealTitle + " Started!",
                        "Time for " + mealTitle + "! Enjoy your food and recharge."
                );
                processedAlerts.add(alertKey);
            } else if (slot.getSlotType() == SlotType.ZOOM_MEETING) {
                String meetingSessionKey = slot.getId() + "_" + today;
                if (!declinedMeetings.contains(meetingSessionKey)) {
                    if (slot.getMeetingLink() != null && !slot.getMeetingLink().isBlank()) {
                        launchZoomMeeting(slot.getMeetingLink());
                    }
                }
                processedAlerts.add(alertKey);
            }
        }

        // Check dedicated ZoomMeeting entities starting right now
        List<ZoomMeeting> activeMeetings = zoomMeetingRepository.findByDayOfWeekIgnoreCaseAndStartTime(dayOfWeek, currentTimeStr);
        for (ZoomMeeting meeting : activeMeetings) {
            String alertKey = "meeting_" + meeting.getId() + "_" + today + "_EXACT_NOW";
            if (processedAlerts.contains(alertKey)) continue;

            String meetingSessionKey = meeting.getId() + "_" + today;
            if (!declinedMeetings.contains(meetingSessionKey)) {
                if (meeting.getZoomUrl() != null && !meeting.getZoomUrl().isBlank()) {
                    launchZoomMeeting(meeting.getZoomUrl());
                }
            }
            processedAlerts.add(alertKey);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3. Zoom Auto-Launch Engine
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Opens the provided Zoom/Meeting URL in the operating system's default browser.
     *
     * @param zoomUrl URL string (e.g. "https://zoom.us/j/123456789")
     */
    public void launchZoomMeeting(String zoomUrl) {
        log.info("AutomationSchedulerService: 🚀 Launching Zoom URL: {}", zoomUrl);
        notificationService.sendDesktopAlert(
                "🎥 Auto-Launching Zoom Meeting",
                "Opening your meeting link in the default browser..."
        );

        if (!Desktop.isDesktopSupported()) {
            log.warn("AutomationSchedulerService: java.awt.Desktop is NOT supported on this environment.");
            return;
        }

        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.BROWSE)) {
            log.warn("AutomationSchedulerService: Desktop.Action.BROWSE is NOT supported on this system.");
            return;
        }

        try {
            desktop.browse(new URI(zoomUrl));
            log.info("AutomationSchedulerService: ✅ Successfully opened Zoom link: {}", zoomUrl);
        } catch (Exception e) {
            log.error("AutomationSchedulerService: Failed to open Zoom URL: {}. Reason: {}", zoomUrl, e.getMessage(), e);
        }
    }
}
