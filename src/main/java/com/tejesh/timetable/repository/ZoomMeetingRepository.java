package com.tejesh.timetable.repository;

import com.tejesh.timetable.model.User;
import com.tejesh.timetable.model.ZoomMeeting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link ZoomMeeting} entities.
 *
 * <p>The Phase 3 {@code AutomationSchedulerService} queries this repository
 * every minute to check whether any meeting is starting within the next 10 minutes
 * and triggers a desktop notification / auto-launch accordingly.
 */
@Repository
public interface ZoomMeetingRepository extends JpaRepository<ZoomMeeting, Long> {

    /**
     * Retrieves all Zoom meetings registered for a specific user.
     *
     * @param user the logged-in student
     * @return list of meetings for this user; empty list if none registered
     */
    List<ZoomMeeting> findByUser(User user);

    /**
     * Finds ALL users' Zoom meetings for a given day and start time.
     * Used by {@code AutomationSchedulerService} for real-time schedule monitoring
     * across all registered users — no need to know which user is "active".
     *
     * <p>SQL: {@code SELECT * FROM zoom_meetings WHERE day_of_week = ? AND start_time = ?}
     *
     * @param dayOfWeek full English day name (e.g., "Monday")
     * @param startTime 24-hour "HH:mm" time string (e.g., "17:00")
     * @return list of matching meetings across all users; empty if none
     */
    List<ZoomMeeting> findByDayOfWeekIgnoreCaseAndStartTime(String dayOfWeek, String startTime);
}

