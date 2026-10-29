package com.tejesh.timetable.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

/**
 * JPA Entity representing a registered student in the Study Assistant.
 *
 * <p>Each {@code User} owns a set of {@link TimetableSlot}s and {@link ZoomMeeting}s
 * linked via {@code @ManyToOne} relationships on those entities.
 *
 * <p>Table: {@code users} in the embedded H2 file database.
 */
@Entity
@Table(name = "users")
public class User {

    // ──────────────────────────────────────────────────────────────────────────
    // Fields
    // ──────────────────────────────────────────────────────────────────────────

    /** Auto-incremented surrogate primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique login identifier chosen by the student (e.g., "Tejesh").
     * Used as the primary look-up key in {@code UserRepository.findByUsername()}.
     */
    @Column(name = "username", unique = true, nullable = false, length = 100)
    private String username;

    /**
     * Hashed or plain-text password (plain-text for Phase 2; will be hashed
     * with BCrypt when the full auth layer is added in Phase 4+).
     */
    @Column(name = "password", nullable = false)
    private String password;

    /**
     * Competitive exam this student is preparing for (e.g., "JEE Advanced",
     * "NEET", "UPSC"). Passed to {@code AIBrainService} as the
     * {@code examTarget} parameter.
     */
    @Column(name = "exam_target", nullable = false, length = 200)
    private String examTarget;

    // ──────────────────────────────────────────────────────────────────────────
    // Constructors
    // ──────────────────────────────────────────────────────────────────────────

    /** No-arg constructor required by JPA. */
    public User() {}

    /**
     * Full constructor for programmatic creation.
     *
     * @param username   unique login name
     * @param password   student's password
     * @param examTarget competitive exam being prepared for
     */
    public User(String username, String password, String examTarget) {
        this.username   = username;
        this.password   = password;
        this.examTarget = examTarget;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Getters & Setters
    // ──────────────────────────────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getExamTarget() { return examTarget; }
    public void setExamTarget(String examTarget) { this.examTarget = examTarget; }

    // ──────────────────────────────────────────────────────────────────────────
    // Object overrides
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User that)) return false;
        return Objects.equals(id, that.id) && Objects.equals(username, that.username);
    }

    @Override
    public int hashCode() { return Objects.hash(id, username); }

    @Override
    public String toString() {
        return "User{id=" + id + ", username='" + username + "', examTarget='" + examTarget + "'}";
    }
}
