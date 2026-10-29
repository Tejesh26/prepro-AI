package com.tejesh.timetable.repository;

import com.tejesh.timetable.model.TimetableSlot;
import com.tejesh.timetable.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Spring Data JPA repository for {@link TimetableSlot} entities.
 */
@Repository
public interface TimetableSlotRepository extends JpaRepository<TimetableSlot, Long> {

    /**
     * Retrieves all timetable slots belonging to a user in chronological order.
     */
    List<TimetableSlot> findByUserOrderByDayOfWeekAscStartTimeAsc(User user);

    /**
     * Deletes every timetable slot belonging to a user.
     */
    @Modifying
    @Transactional
    void deleteByUser(User user);

    /**
     * Finds ALL users' slots for a given day and start time.
     * Used by {@code AutomationSchedulerService} to fire cross-user schedule checks
     * without needing to know which user is currently "active".
     *
     * <p>SQL: {@code SELECT * FROM timetable_slots WHERE day_of_week = ? AND start_time = ?}
     *
     * @param dayOfWeek full English day name (e.g., "Monday")
     * @param startTime 24-hour "HH:mm" time string (e.g., "17:00")
     * @return list of matching slots across all users; empty if none
     */
    List<TimetableSlot> findByDayOfWeekIgnoreCaseAndStartTime(String dayOfWeek, String startTime);
}

