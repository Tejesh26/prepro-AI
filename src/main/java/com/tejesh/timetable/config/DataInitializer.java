package com.tejesh.timetable.config;

import com.tejesh.timetable.model.User;
import com.tejesh.timetable.repository.TimetableSlotRepository;
import com.tejesh.timetable.repository.UserRepository;
import com.tejesh.timetable.repository.ZoomMeetingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * <h2>DataInitializer – Production Environment Initializer</h2>
 *
 * <p>Demo accounts have been removed for production security.
 * All users must register their own personal account via {@code /register}.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final TimetableSlotRepository slotRepository;
    private final ZoomMeetingRepository zoomMeetingRepository;

    public DataInitializer(UserRepository userRepository,
                           TimetableSlotRepository slotRepository,
                           ZoomMeetingRepository zoomMeetingRepository) {
        this.userRepository = userRepository;
        this.slotRepository = slotRepository;
        this.zoomMeetingRepository = zoomMeetingRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
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
}
