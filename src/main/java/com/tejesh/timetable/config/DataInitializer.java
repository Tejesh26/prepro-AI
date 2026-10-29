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
 * <h2>DataInitializer – Demo Account Setup & Password Hash Upgrader</h2>
 *
 * <p>Ensures that the default student account ({@code Tejesh}) is available
 * with an encrypted BCrypt password so that fresh checkouts and presentations
 * run out-of-the-box.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        Optional<User> existing = userRepository.findByUsername("Tejesh");

        if (existing.isEmpty()) {
            User demoUser = new User("Tejesh", passwordEncoder.encode("password123"), "JEE Advanced");
            userRepository.save(demoUser);
            log.info("DataInitializer: Initialized default student user 'Tejesh' with BCrypt hashed password.");
        } else {
            User user = existing.get();
            // Upgrade legacy plain-text password to BCrypt hash if necessary
            if (!user.getPassword().startsWith("$2a$") && !user.getPassword().startsWith("$2b$")) {
                user.setPassword(passwordEncoder.encode("password123"));
                userRepository.save(user);
                log.info("DataInitializer: Upgraded student 'Tejesh' password to BCrypt hash.");
            }
        }
    }
}
