package com.tejesh.timetable.model;

/**
 * Represents how well a student knows a subject.
 *
 * <p>Used by {@link AIBrainService} to calculate study-time priority weights:
 * <ul>
 *   <li>{@code POOR}    → ~50% of total study slots allocated</li>
 *   <li>{@code AVERAGE} → ~30% of total study slots allocated</li>
 *   <li>{@code GOOD}    → ~20% of total study slots (revision only)</li>
 * </ul>
 */
public enum ProficiencyLevel {

    /** Student struggles with this subject – highest priority. */
    POOR,

    /** Student has a moderate understanding – medium priority. */
    AVERAGE,

    /** Student is comfortable with this subject – revision / maintenance only. */
    GOOD
}
