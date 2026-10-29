package com.tejesh.timetable.controller;

import com.tejesh.timetable.dto.UserRegistrationDto;
import com.tejesh.timetable.model.User;
import com.tejesh.timetable.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * <h2>AuthController – Dedicated Authentication &amp; Registration Controller</h2>
 *
 * <p>Handles public entry points for user onboarding and session management:
 * <ul>
 *   <li>{@code GET /login} – Serves the secure sign-in page.</li>
 *   <li>{@code GET /register} – Serves the student registration page.</li>
 *   <li>{@code POST /register} – Validates inputs, hashes credentials via BCrypt, and persists new students.</li>
 * </ul>
 */
@Controller
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Root URL redirector: sends authenticated users to the dashboard,
     * unauthenticated guests to the login page.
     */
    @GetMapping("/")
    public String handleRoot(Authentication authentication) {
        if (isAuthenticated(authentication)) {
            return "redirect:/dashboard";
        }
        return "redirect:/login";
    }

    /**
     * Renders the production login page.
     * Redirects to dashboard if the user is already authenticated.
     */
    @GetMapping("/login")
    public String showLoginPage(@RequestParam(value = "error", required = false) String error,
                                @RequestParam(value = "success", required = false) String success,
                                @RequestParam(value = "logout", required = false) String logout,
                                Authentication authentication,
                                Model model) {
        if (isAuthenticated(authentication)) {
            return "redirect:/dashboard";
        }

        if (error != null) {
            model.addAttribute("loginError", "Invalid username or password. Please try again.");
        }
        if (success != null && success.equalsIgnoreCase("registered")) {
            model.addAttribute("loginSuccess", "Account created successfully! Please sign in with your credentials.");
        }
        if (logout != null) {
            model.addAttribute("loginInfo", "You have been securely signed out.");
        }

        return "login";
    }

    /**
     * Renders the student registration page.
     * Redirects to dashboard if the user is already logged in.
     */
    @GetMapping("/register")
    public String showRegisterPage(@RequestParam(value = "error", required = false) String error,
                                   Authentication authentication,
                                   Model model) {
        if (isAuthenticated(authentication)) {
            return "redirect:/dashboard";
        }

        if (!model.containsAttribute("registrationDto")) {
            model.addAttribute("registrationDto", new UserRegistrationDto());
        }

        if (error != null) {
            switch (error) {
                case "username_taken" -> model.addAttribute("registrationError",
                        "This username is already taken. Please choose another username.");
                case "password_mismatch" -> model.addAttribute("registrationError",
                        "Passwords do not match. Please ensure both password fields are identical.");
                case "blank_fields" -> model.addAttribute("registrationError",
                        "Username and password fields cannot be blank.");
                case "short_password" -> model.addAttribute("registrationError",
                        "Password must be at least 6 characters long.");
                default -> model.addAttribute("registrationError",
                        "Registration failed. Please check your inputs and try again.");
            }
        }

        return "register";
    }

    /**
     * Processes student registration:
     * 1. Validates inputs & password confirmation.
     * 2. Checks username uniqueness.
     * 3. BCrypt-hashes password.
     * 4. Persists the new User entity.
     */
    @PostMapping("/register")
    public String handleRegister(@ModelAttribute("registrationDto") UserRegistrationDto dto,
                                 RedirectAttributes redirectAttributes) {

        String username = dto.getUsername() != null ? dto.getUsername().trim() : "";
        String password = dto.getPassword() != null ? dto.getPassword() : "";
        String confirmPassword = dto.getConfirmPassword() != null ? dto.getConfirmPassword() : "";
        String examTarget = (dto.getExamTarget() != null && !dto.getExamTarget().isBlank())
                ? dto.getExamTarget().trim()
                : "JEE Advanced";

        // Validation 1: Blank checks
        if (username.isEmpty() || password.isEmpty()) {
            return "redirect:/register?error=blank_fields";
        }

        // Validation 2: Password length
        if (password.length() < 6) {
            return "redirect:/register?error=short_password";
        }

        // Validation 3: Password match
        if (!password.equals(confirmPassword)) {
            return "redirect:/register?error=password_mismatch";
        }

        // Validation 4: Username uniqueness
        if (userRepository.findByUsername(username).isPresent()) {
            log.warn("AuthController: Registration rejected – username '{}' already exists.", username);
            return "redirect:/register?error=username_taken";
        }

        // Hash password securely with BCrypt
        String hashedPassword = passwordEncoder.encode(password);

        User newUser = new User(username, hashedPassword, examTarget);
        userRepository.save(newUser);

        log.info("AuthController: New student registered successfully: username='{}', exam='{}'",
                username, examTarget);

        return "redirect:/login?success=registered";
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getName());
    }
}
