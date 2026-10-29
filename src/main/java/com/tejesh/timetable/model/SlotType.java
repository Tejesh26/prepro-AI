package com.tejesh.timetable.model;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Classifies what kind of activity is scheduled in a {@link TimetableSlot}.
 *
 * <ul>
 *   <li>{@code STUDY}        – a focused subject-study block (max 30 minutes continuous)</li>
 *   <li>{@code BREAK}        – a mandatory rest period (5–10 minutes) after each study block</li>
 *   <li>{@code ZOOM_MEETING} – a scheduled online class / meeting; the slot's
 *                              {@code meetingLink} field must be populated</li>
 * </ul>
 */
public enum SlotType {

    /** Active focused study session for a specific subject/topic. */
    STUDY,

    /** Mandatory cognitive rest break inserted after every study block. */
    BREAK,

    /**
     * A scheduled Zoom (or any video-conferencing) meeting.
     * The corresponding {@link TimetableSlot#getMeetingLink()} will contain the URL
     * that {@code java.awt.Desktop} will open automatically at the slot's start time.
     */
    ZOOM_MEETING;

    /**
     * Tolerant Jackson deserializer that prevents crashes when LLMs generate
     * creative slot labels like "REVISION", "LECTURE", "REST", or "ONLINE_CLASS".
     *
     * @param value raw string from JSON
     * @return matching SlotType enum
     */
    @JsonCreator
    public static SlotType fromString(String value) {
        if (value == null || value.isBlank()) return STUDY;
        String upper = value.trim().toUpperCase();

        if (upper.contains("BREAK") || upper.contains("REST") || upper.contains("RECHARGE") || upper.contains("PAUSE")) {
            return BREAK;
        }
        if (upper.contains("ZOOM") || upper.contains("MEETING") || upper.contains("ONLINE") || upper.contains("CLASS")) {
            return ZOOM_MEETING;
        }
        // Maps STUDY, REVISION, PRACTICE, LAB, LECTURE, HOMEWORK, etc. safely to STUDY
        return STUDY;
    }
}
