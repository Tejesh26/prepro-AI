package com.tejesh.timetable.config;

import com.tejesh.timetable.model.User;
import com.tejesh.timetable.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

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

    public DataInitializer(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) {
        // Explicitly remove demo account 'Tejesh' if present from prior runs
        Optional<User> demoUser = userRepository.findByUsername("Tejesh");
        if (demoUser.isPresent()) {
            userRepository.delete(demoUser.get());
            log.info("DataInitializer: Cleaned up legacy demo account 'Tejesh'. Production is ready for user registrations.");
        } else {
            log.info("DataInitializer: Production ready. No demo accounts seeded.");
        }
    }
}
