package com.tejesh.timetable.model;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Classifies what kind of activity is scheduled in a {@link TimetableSlot}.
 *
 * <ul>
 *   <li>{@code STUDY}        – a focused subject-study block (max 30 minutes continuous)</li>
 *   <li>{@code BREAK}        – a mandatory cognitive rest period (5–10 minutes) after each study block</li>
 *   <li>{@code ZOOM_MEETING} – a scheduled online class / meeting</li>
 *   <li>{@code MEAL}         – generic nourishment or meal break</li>
 *   <li>{@code LUNCH}        – midday meal break (default 45 minutes)</li>
 *   <li>{@code SNACK}        – healthy morning, evening, or night snack (10–20 minutes)</li>
 *   <li>{@code DINNER}       – evening dinner break (30–45 minutes)</li>
 * </ul>
 */
public enum SlotType {

    /** Active focused study session for a specific subject/topic. */
    STUDY,

    /** Mandatory cognitive rest break inserted after every study block. */
    BREAK,

    /**
     * A scheduled Zoom (or any video-conferencing) meeting.
     * The corresponding {@link TimetableSlot#getMeetingLink()} will contain the URL.
     */
    ZOOM_MEETING,

    /** Generic nourishment / meal break. */
    MEAL,

    /** Midday lunch break (default 45 minutes). */
    LUNCH,

    /** Morning, evening, or late-night light snack break (10–20 minutes). */
    SNACK,

    /** Evening dinner break (30–45 minutes). */
    DINNER;

    /**
     * Checks if this slot represents any nourishment or meal break.
     *
     * @return {@code true} if this slot is {@code MEAL}, {@code LUNCH}, {@code SNACK}, or {@code DINNER}.
     */
    public boolean isMeal() {
        return this == MEAL || this == LUNCH || this == SNACK || this == DINNER;
    }

    /**
     * Checks if this slot represents a major meal (Lunch or Dinner).
     */
    public boolean isMajorMeal() {
        return this == LUNCH || this == DINNER;
    }

    /**
     * Tolerant Jackson deserializer that prevents crashes when LLMs generate
     * creative slot labels like "REVISION", "LECTURE", "REST", "LUNCH", etc.
     *
     * @param value raw string from JSON
     * @return matching SlotType enum
     */
    @JsonCreator
    public static SlotType fromString(String value) {
        if (value == null || value.isBlank()) return STUDY;
        String upper = value.trim().toUpperCase();

        if (upper.contains("LUNCH")) return LUNCH;
        if (upper.contains("SNACK")) return SNACK;
        if (upper.contains("DINNER")) return DINNER;
        if (upper.contains("MEAL") || upper.contains("FOOD") || upper.contains("EAT") || upper.contains("BREAKFAST")) {
            return MEAL;
        }
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
