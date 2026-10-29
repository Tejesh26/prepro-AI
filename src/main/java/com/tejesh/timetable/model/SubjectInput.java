package com.tejesh.timetable.model;

import java.util.Objects;

/**
 * Encapsulates all information a student provides about a single subject
 * before AI timetable generation.
 *
 * <p>This is a <em>transient value object</em> – it is NOT persisted to the
 * database directly. It is built in-memory from the UI / voice input and
 * passed to {@code AIBrainService} to construct the Gemini prompt.
 *
 * <p>Example usage:
 * <pre>{@code
 *   SubjectInput physics = new SubjectInput(
 *       "Physics",
 *       "Electrostatics, Motion in a Straight Line, Gravitation",
 *       ProficiencyLevel.GOOD
 *   );
 * }</pre>
 */
public class SubjectInput {

    // ──────────────────────────────────────────────────────────────────────────
    // Fields
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * The human-readable subject name (e.g., "Physics", "Mathematics", "Chemistry").
     * Used verbatim in the AI prompt and stored as {@link TimetableSlot#subject}.
     */
    private String subjectName;

    /**
     * A comma-separated list of topics within this subject that the student
     * wants to cover (e.g., "Matrices, Straight Lines, Sequences and Series").
     * The AI uses this list to fill the {@link TimetableSlot#topic} field.
     */
    private String topics;

    /**
     * How proficient the student is in this subject. Drives the time-allocation
     * weights enforced in the AI prompt (POOR→50%, AVERAGE→30%, GOOD→20%).
     */
    private ProficiencyLevel proficiencyLevel;

    // ──────────────────────────────────────────────────────────────────────────
    // Constructors
    // ──────────────────────────────────────────────────────────────────────────

    /** No-arg constructor required by Jackson deserialization. */
    public SubjectInput() {}

    /**
     * Full constructor.
     *
     * @param subjectName      name of the subject (must not be null or blank)
     * @param topics           comma-separated topic list
     * @param proficiencyLevel student's self-assessed level in this subject
     */
    public SubjectInput(String subjectName, String topics, ProficiencyLevel proficiencyLevel) {
        this.subjectName      = subjectName;
        this.topics           = topics;
        this.proficiencyLevel = proficiencyLevel;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Getters & Setters
    // ──────────────────────────────────────────────────────────────────────────

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    public String getTopics() {
        return topics;
    }

    public void setTopics(String topics) {
        this.topics = topics;
    }

    public ProficiencyLevel getProficiencyLevel() {
        return proficiencyLevel;
    }

    public void setProficiencyLevel(ProficiencyLevel proficiencyLevel) {
        this.proficiencyLevel = proficiencyLevel;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Object overrides
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SubjectInput that)) return false;
        return Objects.equals(subjectName, that.subjectName)
                && Objects.equals(topics, that.topics)
                && proficiencyLevel == that.proficiencyLevel;
    }

    @Override
    public int hashCode() {
        return Objects.hash(subjectName, topics, proficiencyLevel);
    }

    @Override
    public String toString() {
        return "SubjectInput{" +
                "subjectName='" + subjectName + '\'' +
                ", topics='" + topics + '\'' +
                ", proficiencyLevel=" + proficiencyLevel +
                '}';
    }
}
