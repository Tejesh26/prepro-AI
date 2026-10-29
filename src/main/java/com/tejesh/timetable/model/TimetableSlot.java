package com.tejesh.timetable.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;

/**
 * Represents a single time-block in the generated weekly study timetable.
 *
 * <p><b>Dual-purpose class (updated in Phase 2):</b>
 * <ol>
 *   <li><b>AI Response DTO:</b> Jackson deserializes the Gemini JSON array
 *       directly into {@code List<TimetableSlot>} using the no-arg constructor.</li>
 *   <li><b>JPA Entity:</b> Persisted to the {@code timetable_slots} table in H2,
 *       linked to a {@link User} via a {@code @ManyToOne} foreign key.</li>
 * </ol>
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} ensures that if Gemini
 * adds extra JSON fields, Jackson will not throw an error during deserialization.
 *
 * <p>The {@code user} field carries {@code @JsonIgnore} to prevent infinite
 * recursion when the entity is serialized back to JSON (User → TimetableSlots
 * → User → ...).
 */
@Entity
@Table(name = "timetable_slots")
@JsonIgnoreProperties(ignoreUnknown = true)
public class TimetableSlot {

    // ──────────────────────────────────────────────────────────────────────────
    // Fields
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Auto-incremented surrogate primary key.
     * Populated by H2 upon {@code save()}; {@code null} before persistence.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Full English day name: "Monday", "Tuesday", …, "Sunday".
     * Used by the Phase 5 scheduler to match calendar day.
     */
    @Column(name = "day_of_week", nullable = false, length = 20)
    private String dayOfWeek;

    /**
     * 24-hour clock start time in "HH:mm" format (e.g., "16:00").
     * Used by the alarm/automation service for trigger calculation.
     */
    @Column(name = "start_time", nullable = false, length = 10)
    private String startTime;

    /**
     * 24-hour clock end time in "HH:mm" format (e.g., "16:30").
     */
    @Column(name = "end_time", nullable = false, length = 10)
    private String endTime;

    /**
     * Subject label exactly as provided in {@link SubjectInput#subjectName}
     * (e.g., "Physics"). Blank string for {@link SlotType#BREAK} slots.
     */
    @Column(name = "subject", length = 200)
    private String subject;

    /**
     * Specific topic studied in this slot (e.g., "Electrostatics").
     * "Rest" or blank for {@link SlotType#BREAK} slots.
     */
    @Column(name = "topic", length = 500)
    private String topic;

    /**
     * Activity type: {@code STUDY}, {@code BREAK}, or {@code ZOOM_MEETING}.
     * Stored as a VARCHAR string in H2 for human-readability.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "slot_type", nullable = false, length = 20)
    private SlotType slotType;

    /**
     * Full video-conferencing URL (e.g., {@code https://zoom.us/j/99999}).
     * {@code null} for {@code STUDY} and {@code BREAK} slots.
     * Opened automatically by {@code java.awt.Desktop.browse()} at {@code startTime}.
     */
    @Column(name = "meeting_link", length = 1000)
    private String meetingLink;

    /**
     * The student who owns this timetable slot.
     *
     * <p>{@code @JsonIgnore} — excluded from JSON serialization to prevent
     * circular reference (User ↔ TimetableSlot).
     * <p>{@code FetchType.LAZY} — User data is only loaded when explicitly accessed.
     */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // ──────────────────────────────────────────────────────────────────────────
    // Constructors
    // ──────────────────────────────────────────────────────────────────────────

    /** No-arg constructor required by JPA and Jackson. */
    public TimetableSlot() {}

    /** Full constructor for programmatic creation and unit tests. */
    public TimetableSlot(Long id,
                         String dayOfWeek,
                         String startTime,
                         String endTime,
                         String subject,
                         String topic,
                         SlotType slotType,
                         String meetingLink) {
        this.id          = id;
        this.dayOfWeek   = dayOfWeek;
        this.startTime   = startTime;
        this.endTime     = endTime;
        this.subject     = subject;
        this.topic       = topic;
        this.slotType    = slotType;
        this.meetingLink = meetingLink;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Getters & Setters
    // ──────────────────────────────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(String dayOfWeek) { this.dayOfWeek = dayOfWeek; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public SlotType getSlotType() { return slotType; }
    public void setSlotType(SlotType slotType) { this.slotType = slotType; }

    public String getMeetingLink() { return meetingLink; }
    public void setMeetingLink(String meetingLink) { this.meetingLink = meetingLink; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    // ──────────────────────────────────────────────────────────────────────────
    // Object overrides
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TimetableSlot that)) return false;
        return Objects.equals(id, that.id)
                && Objects.equals(dayOfWeek, that.dayOfWeek)
                && Objects.equals(startTime, that.startTime)
                && slotType == that.slotType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, dayOfWeek, startTime, slotType);
    }

    /**
     * Compact one-liner for console output and logging.
     * Example: {@code [Monday | 16:00-16:30 | STUDY        | Mathematics  – Matrices]}
     */
    @Override
    public String toString() {
        String subjectPart = (subject != null && !subject.isBlank()) ? subject : "—";
        String topicPart   = (topic   != null && !topic.isBlank())   ? topic   : "—";
        String linkPart    = (meetingLink != null) ? " | " + meetingLink : "";
        return String.format("[%-9s | %s-%s | %-12s | %-14s – %s%s]",
                dayOfWeek, startTime, endTime, slotType, subjectPart, topicPart, linkPart);
    }
}
