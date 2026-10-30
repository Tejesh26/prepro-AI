package com.tejesh.timetable.config;

import com.tejesh.timetable.model.User;
import com.tejesh.timetable.repository.TimetableSlotRepository;
import com.tejesh.timetable.repository.UserRepository;
import com.tejesh.timetable.repository.ZoomMeetingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * <h2>DataInitializer – Production Environment Initializer</h2>
 *
 * <p>Handles startup database migration and cleanups:
 * <ul>
 *   <li>Migrates {@code timetable_slots.slot_type} to unconstrained VARCHAR(50) so new slot types
 *       ({@code LUNCH}, {@code SNACK}, {@code DINNER}, {@code MEAL}) are accepted without constraint errors.</li>
 *   <li>Ensures demo accounts are removed for production security.</li>
 * </ul>
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final TimetableSlotRepository slotRepository;
    private final ZoomMeetingRepository zoomMeetingRepository;
    private final JdbcTemplate jdbcTemplate;

    public DataInitializer(UserRepository userRepository,
                           TimetableSlotRepository slotRepository,
                           ZoomMeetingRepository zoomMeetingRepository,
                           JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.slotRepository = slotRepository;
        this.zoomMeetingRepository = zoomMeetingRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // 1. Schema migration: Ensure timetable_slots.slot_type accepts MEAL, LUNCH, SNACK, DINNER
        migrateSlotTypeColumn();

        // 2. Clean up demo accounts
        try {
            Optional<User> demoUser = userRepository.findByUsername("Tejesh");
            if (demoUser.isPresent()) {
                User user = demoUser.get();
                slotRepository.deleteByUser(user);
                zoomMeetingRepository.deleteAll(zoomMeetingRepository.findByUser(user));
                userRepository.delete(user);
                log.info("DataInitializer: Cleaned up legacy demo account 'Tejesh'. Production is ready for user registrations.");
            } else {
                log.info("DataInitializer: Production ready. No demo accounts seeded.");
            }
        } catch (Exception e) {
            log.warn("DataInitializer: Note on startup user initialization: {}", e.getMessage());
        }
    }

    private void migrateSlotTypeColumn() {
        try {
            // Drop any legacy check constraints on slot_type (e.g. ('BREAK','STUDY','ZOOM_MEETING'))
            List<String> checkConstraints = jdbcTemplate.queryForList(
                    "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.CONSTRAINTS WHERE TABLE_NAME = 'TIMETABLE_SLOTS' AND CONSTRAINT_TYPE = 'CHECK'",
                    String.class
            );
            for (String constraintName : checkConstraints) {
                try {
                    jdbcTemplate.execute("ALTER TABLE timetable_slots DROP CONSTRAINT " + constraintName);
                    log.info("DataInitializer: Dropped legacy check constraint '{}' from timetable_slots.", constraintName);
                } catch (Exception dropEx) {
                    log.debug("DataInitializer: Skipping constraint drop: {}", dropEx.getMessage());
                }
            }

            // Alter column to VARCHAR(50) to allow all current and future slot types
            jdbcTemplate.execute("ALTER TABLE timetable_slots ALTER COLUMN slot_type VARCHAR(50)");
            log.info("DataInitializer: ✅ timetable_slots.slot_type verified as VARCHAR(50).");
        } catch (Exception e) {
            log.debug("DataInitializer: slot_type migration note: {}", e.getMessage());
        }
    }
}
