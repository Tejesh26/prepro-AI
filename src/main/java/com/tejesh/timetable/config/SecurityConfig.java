package com.tejesh.timetable.config;

import com.tejesh.timetable.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * <h2>SecurityConfig – Production-Grade Web Security Configuration</h2>
 *
 * <p>Enforces strict authentication, CSRF protection, and BCrypt encryption
 * for the public-facing PREPRO AI web application:
 * <ul>
 *   <li>Permits public access to {@code /}, {@code /login}, {@code /register}, and static assets.</li>
 *   <li>Requires authentication for {@code /dashboard}, {@code /generate}, {@code /timetable}, and {@code /export/**}.</li>
 *   <li>Enforces Cross-Site Request Forgery (CSRF) protection on all form submissions.</li>
 *   <li>Configures standard {@link BCryptPasswordEncoder} (plain-text passwords strictly forbidden).</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final UserRepository userRepository;

    public SecurityConfig(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Production password encoder: BCrypt (work factor 10).
     * Enforces that passwords are encrypted before storage.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * On successful login, loads the authenticated User entity from the database
     * and sets it into the HTTP Session so downstream services and views can access it.
     */
    @Bean
    public AuthenticationSuccessHandler customAuthenticationSuccessHandler() {
        return (request, response, authentication) -> {
            String username = authentication.getName();
            userRepository.findByUsername(username).ifPresent(user -> {
                request.getSession().setAttribute("user", user);
                log.info("SecurityConfig: Session user set for '{}' (exam: '{}')",
                        user.getUsername(), user.getExamTarget());
            });
            response.sendRedirect("/dashboard");
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/",
                    "/login",
                    "/register",
                    "/health",
                    "/css/**",
                    "/js/**",
                    "/images/**",
                    "/favicon.ico",
                    "/h2-console/**"
                ).permitAll()
                .requestMatchers(
                    "/dashboard/**",
                    "/timetable/**",
                    "/generate/**",
                    "/export/**",
                    "/test-alarm/**",
                    "/api/**"
                ).authenticated()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .successHandler(customAuthenticationSuccessHandler())
                .failureUrl("/login?error=invalid_credentials")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                .logoutSuccessUrl("/login?logout=success")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            .csrf(csrf -> csrf
                // Bypass embedded H2 console and authenticated REST API endpoints:
                .ignoringRequestMatchers(
                    new AntPathRequestMatcher("/h2-console/**"),
                    new AntPathRequestMatcher("/api/**")
                )
            )
            .headers(headers -> headers
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
            );

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}
