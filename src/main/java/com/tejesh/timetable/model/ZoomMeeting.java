package com.tejesh.timetable.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;

/**
 * JPA Entity representing a scheduled recurring or one-time Zoom (video)
 * meeting that the student has registered in the Study Assistant.
 *
 * <p>Zoom meetings are extracted from {@link TimetableSlot}s whose
 * {@code slotType == SlotType.ZOOM_MEETING} and persisted here so the
 * Phase 5 {@code AutomationSchedulerService} can independently query them
 * and trigger {@code java.awt.Desktop.browse()} at the correct time.
 *
 * <p>Table: {@code zoom_meetings} in the embedded H2 file database.
 */
@Entity
@Table(name = "zoom_meetings")
public class ZoomMeeting {

    // ──────────────────────────────────────────────────────────────────────────
    // Fields
    // ──────────────────────────────────────────────────────────────────────────

    /** Auto-incremented surrogate primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Human-readable label for the meeting (e.g., "Weekly Physics Lecture",
     * "Online Class"). Derived from the corresponding {@code TimetableSlot.topic}.
     */
    @Column(name = "meeting_title", nullable = false, length = 300)
    private String meetingTitle;

    /**
     * Full English day name on which this meeting occurs
     * (e.g., "Monday", "Wednesday").
     */
    @Column(name = "day_of_week", nullable = false, length = 20)
    private String dayOfWeek;

    /**
     * 24-hour clock start time in "HH:mm" format (e.g., "17:00").
     * Used by the Phase 5 scheduler to determine when to open the Zoom URL.
     */
    @Column(name = "start_time", nullable = false, length = 10)
    private String startTime;

    /**
     * Full Zoom (or any video-conferencing) URL.
     * e.g., {@code https://zoom.us/j/99999} or {@code https://meet.google.com/abc}.
     * Opened automatically by {@code java.awt.Desktop.browse()} at {@code startTime}.
     */
    @Column(name = "zoom_url", nullable = false, length = 1000)
    private String zoomUrl;

    /**
     * {@code true} if this meeting repeats every week on the same day/time.
     * {@code false} for one-time meetings. Defaults to {@code true} since most
     * class meetings are recurring.
     */
    @Column(name = "is_recurring", nullable = false)
    private boolean recurring;

    /**
     * The student who owns this meeting.
     * {@code @JsonIgnore} prevents circular serialization when returning JSON.
     * Loaded lazily since we rarely need the full User graph when fetching meetings.
     */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // ──────────────────────────────────────────────────────────────────────────
    // Constructors
    // ──────────────────────────────────────────────────────────────────────────

    /** No-arg constructor required by JPA. */
    public ZoomMeeting() {}

    /**
     * Full constructor for programmatic creation from a {@link TimetableSlot}.
     */
    public ZoomMeeting(String meetingTitle,
                       String dayOfWeek,
                       String startTime,
                       String zoomUrl,
                       boolean recurring,
                       User user) {
        this.meetingTitle = meetingTitle;
        this.dayOfWeek    = dayOfWeek;
        this.startTime    = startTime;
        this.zoomUrl      = zoomUrl;
        this.recurring    = recurring;
        this.user         = user;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Getters & Setters
    // ──────────────────────────────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getMeetingTitle() { return meetingTitle; }
    public void setMeetingTitle(String meetingTitle) { this.meetingTitle = meetingTitle; }

    public String getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(String dayOfWeek) { this.dayOfWeek = dayOfWeek; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getZoomUrl() { return zoomUrl; }
    public void setZoomUrl(String zoomUrl) { this.zoomUrl = zoomUrl; }

    public boolean isRecurring() { return recurring; }
    public void setRecurring(boolean recurring) { this.recurring = recurring; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    // ──────────────────────────────────────────────────────────────────────────
    // Object overrides
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ZoomMeeting that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "ZoomMeeting{id=" + id
                + ", title='" + meetingTitle + '\''
                + ", day=" + dayOfWeek
                + ", time=" + startTime
                + ", url='" + zoomUrl + '\''
                + ", recurring=" + recurring + '}';
    }
}
