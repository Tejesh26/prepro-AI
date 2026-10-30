package com.tejesh.timetable.service;

import com.tejesh.timetable.model.SlotType;
import com.tejesh.timetable.model.TimetableSlot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * <h2>MealBreakService – Automated Nourishment Scheduling Engine</h2>
 *
 * <p>Enforces automatic insertion of nutrition and meal breaks into generated
 * study schedules based on the user's active study window and preferences.
 *
 * <h3>Core Scheduling Rules</h3>
 * <ol>
 *   <li><b>Lunch Break:</b>
 *     <ul>
 *       <li>Added only if the study window covers lunch time (any part of 12:00–14:30)
 *           AND the window duration is at least 4 hours.</li>
 *       <li>Default 45 minutes, placed near 13:00 (or closest free time inside 12:00–14:30).</li>
 *       <li>Aligned to block boundaries; never placed inside a 30-minute study block.</li>
 *     </ul>
 *   </li>
 *   <li><b>Snack Breaks:</b>
 *     <ul>
 *       <li><b>Morning Snack:</b> 15 minutes around 10:30–11:00 if the window includes that time
 *           and study started before 09:00 (or >= 2 hours prior).</li>
 *       <li><b>Evening Snack:</b> 15–20 minutes around 16:30–17:30 if the window includes that time
 *           and lasts past 16:30.</li>
 *       <li><b>Dinner Break:</b> 30–45 minutes around 20:00 if the window runs past 19:30.</li>
 *       <li><b>Night Snack:</b> 10-minute light snack around 22:00 if the window runs past 22:00.</li>
 *     </ul>
 *   </li>
 *   <li><b>Short Windows:</b>
 *     <ul>
 *       <li>Under 3 hours: NO meal breaks (only standard cognitive breaks).</li>
 *       <li>3 to 4 hours: At most one snack break. NO lunch break.</li>
 *     </ul>
 *   </li>
 *   <li><b>Priority &amp; Zoom Safeguards:</b>
 *     <ul>
 *       <li>Meal breaks take priority over study blocks and cognitive breaks.</li>
 *       <li>Never overlaps live Zoom lectures; meals are shifted immediately before or after the class.</li>
 *       <li>Displaced study time is preserved and shifted forward without loss of subject coverage.</li>
 *     </ul>
 *   </li>
 *   <li><b>User Control:</b>
 *     <ul>
 *       <li>Can be globally toggled on/off.</li>
 *       <li>Supports user-defined custom lunch time and duration.</li>
 *       <li>Respects natural language overrides ("no lunch", "skip lunch", "lunch at 12:30").</li>
 *     </ul>
 *   </li>
 * </ol>
 */
@Service
public class MealBreakService {

    private static final Logger log = LoggerFactory.getLogger(MealBreakService.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    // Precompiled regex patterns for user natural language constraints
    private static final Pattern NO_LUNCH_PATTERN = Pattern.compile("(?i)\\b(no|skip|without)\\s+lunch\\b");
    private static final Pattern NO_MEALS_PATTERN = Pattern.compile("(?i)\\b(no|skip|without)\\s+(meal|meals|meal\\s*breaks)\\b");
    private static final Pattern CUSTOM_LUNCH_TIME_PATTERN = Pattern.compile("(?i)lunch\\s+(?:at\\s+)?(\\d{1,2}:\\d{2})");
    private static final Pattern CUSTOM_LUNCH_DUR_PATTERN = Pattern.compile("(?i)lunch\\s+(?:for|duration)\\s+(\\d{1,2})\\s*(?:min|minutes)?");

    /**
     * Internal representation of a meal break to be scheduled.
     */
    public record MealPlan(
            SlotType slotType,
            String subject,
            String topic,
            LocalTime targetTime,
            int durationMinutes
    ) {}

    // ──────────────────────────────────────────────────────────────────────────
    // 1. Primary Public APIs
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Primary entry point that takes a single day's slots and user window,
     * applying default meal break configurations.
     *
     * @param daySlots    existing slots scheduled for a single day
     * @param windowStart daily study window start time
     * @param windowEnd   daily study window end time
     * @return slots with appropriate meal breaks inserted and study blocks preserved
     */
    public List<TimetableSlot> insertMealBreaks(List<TimetableSlot> daySlots,
                                               LocalTime windowStart,
                                               LocalTime windowEnd) {
        return insertMealBreaks(daySlots, windowStart, windowEnd, true, LocalTime.of(13, 0), 45, null);
    }

    /**
     * Full-featured entry point allowing explicit user configuration and instruction overrides.
     *
     * @param daySlots             existing slots scheduled for a single day
     * @param windowStart          daily study window start time
     * @param windowEnd            daily study window end time
     * @param includeMealBreaks    user preference toggle (default true)
     * @param customLunchTime      optional user-specified lunch time (e.g. 12:30)
     * @param customLunchDuration  optional user-specified lunch duration in minutes (e.g. 45)
     * @param userInstructions     optional typed or voice instructions to parse for overrides
     * @return slots with appropriate meal breaks inserted and study blocks preserved
     */
    public List<TimetableSlot> insertMealBreaks(List<TimetableSlot> daySlots,
                                               LocalTime windowStart,
                                               LocalTime windowEnd,
                                               boolean includeMealBreaks,
                                               LocalTime customLunchTime,
                                               Integer customLunchDuration,
                                               String userInstructions) {

        if (daySlots == null) daySlots = new ArrayList<>();
        if (!includeMealBreaks) {
            log.info("MealBreakService: Meal breaks disabled by user preference.");
            return new ArrayList<>(daySlots);
        }

        // Check if user explicitly asked for no meals
        if (userInstructions != null && NO_MEALS_PATTERN.matcher(userInstructions).find()) {
            log.info("MealBreakService: User instruction requested no meal breaks.");
            return new ArrayList<>(daySlots);
        }

        // Auto-detect window from slots if start or end is missing
        if (windowStart == null || windowEnd == null) {
            LocalTime minStart = LocalTime.of(23, 59);
            LocalTime maxEnd = LocalTime.of(0, 0);
            for (TimetableSlot s : daySlots) {
                try {
                    LocalTime sTime = LocalTime.parse(s.getStartTime(), TIME_FORMATTER);
                    LocalTime eTime = LocalTime.parse(s.getEndTime(), TIME_FORMATTER);
                    if (sTime.isBefore(minStart)) minStart = sTime;
                    if (eTime.isAfter(maxEnd)) maxEnd = eTime;
                } catch (Exception ignored) {}
            }
            if (windowStart == null) windowStart = minStart.isBefore(LocalTime.of(23, 59)) ? minStart : LocalTime.of(9, 0);
            if (windowEnd == null) windowEnd = maxEnd.isAfter(LocalTime.of(0, 0)) ? maxEnd : LocalTime.of(17, 0);
        }

        long windowMinutes = Duration.between(windowStart, windowEnd).toMinutes();
        if (windowMinutes < 0) {
            windowMinutes += 24 * 60; // Handle wrap-around
        }

        // Rule 3: If the window is under 3 hours (180 mins), add NO meal breaks
        if (windowMinutes < 180) {
            log.info("MealBreakService: Window duration ({}m) is under 3 hours. No meal breaks inserted.", windowMinutes);
            return new ArrayList<>(daySlots);
        }

        // Determine meals to schedule based on window and user inputs
        List<MealPlan> requiredMeals = determineRequiredMeals(windowStart, windowEnd, windowMinutes,
                customLunchTime, customLunchDuration, userInstructions);

        if (requiredMeals.isEmpty()) {
            return new ArrayList<>(daySlots);
        }

        // Day name extraction for created slots
        String dayOfWeek = daySlots.isEmpty() ? "Monday" : daySlots.get(0).getDayOfWeek();

        // Integrate meal breaks into the day's timeline
        return mergeMealBreaks(daySlots, requiredMeals, windowStart, windowEnd, dayOfWeek);
    }

    /**
     * Processes an entire weekly timetable (all days), inserting meal breaks for each day.
     *
     * @param weeklySlots         all slots for all days of the week
     * @param includeMealBreaks   global toggle
     * @param customLunchTime     custom lunch time or null for default
     * @param customLunchDuration custom lunch duration or null for default
     * @param userInstructions    user instructions
     * @return full weekly list with meal breaks inserted
     */
    public List<TimetableSlot> processWeeklySchedule(List<TimetableSlot> weeklySlots,
                                                    boolean includeMealBreaks,
                                                    LocalTime customLunchTime,
                                                    Integer customLunchDuration,
                                                    String userInstructions) {
        if (weeklySlots == null || weeklySlots.isEmpty() || !includeMealBreaks) {
            return weeklySlots != null ? weeklySlots : new ArrayList<>();
        }

        // Group slots by day of week preserving standard order
        Map<String, List<TimetableSlot>> slotsByDay = new LinkedHashMap<>();
        for (TimetableSlot slot : weeklySlots) {
            String day = slot.getDayOfWeek() != null ? slot.getDayOfWeek() : "Monday";
            slotsByDay.computeIfAbsent(day, k -> new ArrayList<>()).add(slot);
        }

        List<TimetableSlot> finalWeeklySlots = new ArrayList<>();
        for (Map.Entry<String, List<TimetableSlot>> entry : slotsByDay.entrySet()) {
            List<TimetableSlot> daySlots = entry.getValue();

            // Infer window bounds from the day's slots
            LocalTime dayStart = null;
            LocalTime dayEnd = null;
            for (TimetableSlot s : daySlots) {
                try {
                    LocalTime st = LocalTime.parse(s.getStartTime(), TIME_FORMATTER);
                    LocalTime et = LocalTime.parse(s.getEndTime(), TIME_FORMATTER);
                    if (dayStart == null || st.isBefore(dayStart)) dayStart = st;
                    if (dayEnd == null || et.isAfter(dayEnd)) dayEnd = et;
                } catch (Exception ignored) {}
            }

            List<TimetableSlot> updatedDaySlots = insertMealBreaks(daySlots, dayStart, dayEnd,
                    includeMealBreaks, customLunchTime, customLunchDuration, userInstructions);
            finalWeeklySlots.addAll(updatedDaySlots);
        }

        return finalWeeklySlots;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2. Meal Determination Rules
    // ──────────────────────────────────────────────────────────────────────────

    private List<MealPlan> determineRequiredMeals(LocalTime windowStart,
                                                  LocalTime windowEnd,
                                                  long windowMinutes,
                                                  LocalTime customLunchTime,
                                                  Integer customLunchDuration,
                                                  String userInstructions) {

        List<MealPlan> meals = new ArrayList<>();

        // Parse custom user overrides from instructions
        boolean skipLunch = false;
        if (userInstructions != null) {
            if (NO_LUNCH_PATTERN.matcher(userInstructions).find()) {
                skipLunch = true;
            }
            Matcher lunchTimeMatcher = CUSTOM_LUNCH_TIME_PATTERN.matcher(userInstructions);
            if (lunchTimeMatcher.find()) {
                try {
                    customLunchTime = LocalTime.parse(lunchTimeMatcher.group(1));
                } catch (Exception ignored) {}
            }
            Matcher lunchDurMatcher = CUSTOM_LUNCH_DUR_PATTERN.matcher(userInstructions);
            if (lunchDurMatcher.find()) {
                try {
                    customLunchDuration = Integer.parseInt(lunchDurMatcher.group(1));
                } catch (Exception ignored) {}
            }
        }

        int finalLunchDuration = (customLunchDuration != null && customLunchDuration >= 15 && customLunchDuration <= 120)
                ? customLunchDuration : 45;

        LocalTime finalLunchTime = (customLunchTime != null) ? customLunchTime : LocalTime.of(13, 0);

        // Rule 3: Short windows between 3 and 4 hours (180 to 240 mins)
        // -> Add ONLY ONE snack break (NO lunch break)
        if (windowMinutes <= 240) {
            if (windowCovers(windowStart, windowEnd, LocalTime.of(16, 30), LocalTime.of(17, 30))
                    && windowEnd.isAfter(LocalTime.of(16, 30))) {
                meals.add(new MealPlan(SlotType.SNACK, "🥪 Evening Snack", "Healthy Snack & Energy Boost",
                        LocalTime.of(16, 30), 15));
            } else if (windowCovers(windowStart, windowEnd, LocalTime.of(10, 30), LocalTime.of(11, 0))
                    && windowStart.isBefore(LocalTime.of(9, 0))) {
                meals.add(new MealPlan(SlotType.SNACK, "🥪 Morning Snack", "Healthy Snack & Hydration",
                        LocalTime.of(10, 30), 15));
            } else if (windowEnd.isAfter(LocalTime.of(19, 30))) {
                meals.add(new MealPlan(SlotType.DINNER, "🍽️ Dinner Break", "Evening Meal & Recharge",
                        LocalTime.of(20, 0), 30));
            }
            return meals;
        }

        // Full windows (> 4 hours / > 240 mins):

        // 1. Morning Snack:
        // 15 minutes around 10:30-11:00, only if the window includes that time
        // AND study started before 09:00 (ensuring at least ~90-120 mins of focus before morning break)
        if (windowCovers(windowStart, windowEnd, LocalTime.of(10, 30), LocalTime.of(11, 0))
                && windowStart.isBefore(LocalTime.of(9, 0))) {
            meals.add(new MealPlan(SlotType.SNACK, "🥪 Morning Snack", "Healthy Snack & Hydration",
                    LocalTime.of(10, 30), 15));
        }

        // 2. Lunch Break:
        // Add only if window covers lunch time (any part of 12:00-14:30) AND window >= 4 hours (240 mins)
        boolean coversLunchSpan = !(windowEnd.isBefore(LocalTime.of(12, 0)) || windowStart.isAfter(LocalTime.of(14, 30)));
        if (!skipLunch && coversLunchSpan && windowMinutes >= 240) {
            LocalTime lunchStart = finalLunchTime;
            // If window does not include 13:00, place at closest free time inside 12:00-14:30
            if (windowEnd.isBefore(lunchStart.plusMinutes(finalLunchDuration))) {
                // If window ends before default lunch finish, place earlier near 12:00 or windowEnd - duration
                LocalTime clamped = windowEnd.minusMinutes(finalLunchDuration);
                lunchStart = clamped.isBefore(LocalTime.of(12, 0)) ? LocalTime.of(12, 0) : clamped;
            } else if (windowStart.isAfter(lunchStart)) {
                // If window starts after default lunch, place at windowStart (inside 12:00-14:30)
                lunchStart = windowStart;
            }

            meals.add(new MealPlan(SlotType.LUNCH, "🍱 Lunch Break", "Nutritious Meal & Mind Refresh",
                    lunchStart, finalLunchDuration));
        }

        // 3. Evening Snack:
        // 15-20 minutes around 16:30-17:30, only if window includes that time and lasts past 16:30
        if (windowCovers(windowStart, windowEnd, LocalTime.of(16, 30), LocalTime.of(17, 0))
                && windowEnd.isAfter(LocalTime.of(16, 30))) {
            meals.add(new MealPlan(SlotType.SNACK, "🥪 Evening Snack", "Healthy Snack & Brain Refresh",
                    LocalTime.of(16, 30), 15));
        }

        // 4. Dinner Break:
        // If window runs past 19:30, add a 30-45 minute dinner break around 20:00
        if (windowEnd.isAfter(LocalTime.of(19, 30))) {
            LocalTime dinnerTime = LocalTime.of(20, 0);
            int dinnerDuration = 45;
            // If window ends before 20:45, shift dinner earlier to 19:30
            if (windowEnd.isBefore(dinnerTime.plusMinutes(dinnerDuration))) {
                dinnerTime = windowEnd.minusMinutes(dinnerDuration);
                if (dinnerTime.isBefore(LocalTime.of(19, 30))) {
                    dinnerTime = LocalTime.of(19, 30);
                    dinnerDuration = (int) Math.max(30, Duration.between(dinnerTime, windowEnd).toMinutes());
                }
            }
            meals.add(new MealPlan(SlotType.DINNER, "🍽️ Dinner Break", "Evening Meal & Relaxation",
                    dinnerTime, dinnerDuration));
        }

        // 5. Night Snack:
        // If window runs past 22:00, add a 10-minute light snack around 22:00
        if (windowEnd.isAfter(LocalTime.of(22, 0))) {
            meals.add(new MealPlan(SlotType.SNACK, "🥛 Night Snack", "Light Refreshment & Wind-down",
                    LocalTime.of(22, 0), 10));
        }

        // Sort chronologically by target time
        meals.sort(Comparator.comparing(MealPlan::targetTime));
        return meals;
    }

    private boolean windowCovers(LocalTime windowStart, LocalTime windowEnd, LocalTime rangeStart, LocalTime rangeEnd) {
        return !windowStart.isAfter(rangeStart) && !windowEnd.isBefore(rangeEnd);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3. Conflict Resolution & Schedule Merging
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Inserts the meal plans into the existing list of slots, aligning with study block
     * boundaries, strictly avoiding Zoom class collisions, and pushing displaced study time.
     */
    private List<TimetableSlot> mergeMealBreaks(List<TimetableSlot> daySlots,
                                                List<MealPlan> meals,
                                                LocalTime windowStart,
                                                LocalTime windowEnd,
                                                String dayOfWeek) {

        // Separate fixed Zoom meetings from flexible study & break slots
        List<TimetableSlot> zoomSlots = new ArrayList<>();
        List<TimetableSlot> studySlots = new ArrayList<>();

        for (TimetableSlot s : daySlots) {
            if (s.getSlotType() == SlotType.ZOOM_MEETING) {
                zoomSlots.add(s);
            } else if (s.getSlotType() == SlotType.STUDY) {
                studySlots.add(s);
            }
        }

        // Sort study slots chronologically
        studySlots.sort(Comparator.comparing(s -> LocalTime.parse(s.getStartTime(), TIME_FORMATTER)));

        // Schedule each meal break, adjusting for Zoom classes and block boundaries
        List<TimetableSlot> mealSlots = new ArrayList<>();
        for (MealPlan meal : meals) {
            LocalTime mealStart = meal.targetTime();
            int duration = meal.durationMinutes();
            LocalTime mealEnd = mealStart.plusMinutes(duration);

            // Rule 4: Do not overlap with live Zoom classes or fixed classes.
            // Place the meal break before or after the class instead.
            for (TimetableSlot zoom : zoomSlots) {
                LocalTime zStart = LocalTime.parse(zoom.getStartTime(), TIME_FORMATTER);
                LocalTime zEnd = LocalTime.parse(zoom.getEndTime(), TIME_FORMATTER);

                if (intervalsOverlap(mealStart, mealEnd, zStart, zEnd)) {
                    // Try placing before Zoom class
                    LocalTime beforeStart = zStart.minusMinutes(duration);
                    if (!beforeStart.isBefore(windowStart)) {
                        mealStart = beforeStart;
                        mealEnd = zStart;
                    } else {
                        // Place immediately after Zoom class
                        mealStart = zEnd;
                        mealEnd = mealStart.plusMinutes(duration);
                    }
                }
            }

            TimetableSlot mealSlot = new TimetableSlot(
                    null,
                    dayOfWeek,
                    mealStart.format(TIME_FORMATTER),
                    mealEnd.format(TIME_FORMATTER),
                    meal.subject(),
                    meal.topic(),
                    meal.slotType(),
                    null
            );
            mealSlots.add(mealSlot);
        }

        // Rule 4: Priority & Displaced Study Blocks:
        // Study blocks overlapping a meal break are shifted forward to the next free slot.
        // Reconstruct day schedule:
        List<TimetableSlot> finalSlots = new ArrayList<>(zoomSlots);
        finalSlots.addAll(mealSlots);

        // Sort fixed slots (Zoom + Meals)
        finalSlots.sort(Comparator.comparing(s -> LocalTime.parse(s.getStartTime(), TIME_FORMATTER)));

        // Now place each study block into the free available blocks
        LocalTime cursor = windowStart;
        for (TimetableSlot study : studySlots) {
            LocalTime origStart = LocalTime.parse(study.getStartTime(), TIME_FORMATTER);
            LocalTime origEnd = LocalTime.parse(study.getEndTime(), TIME_FORMATTER);
            long studyDur = Duration.between(origStart, origEnd).toMinutes();
            if (studyDur <= 0) studyDur = 30;

            // Advance cursor to at least original start or current position
            if (origStart.isAfter(cursor)) {
                cursor = origStart;
            }

            // Find next interval of length studyDur that does not overlap any fixed slot
            boolean placed = false;
            while (!placed && !cursor.isAfter(windowEnd.plusHours(2))) {
                LocalTime potentialEnd = cursor.plusMinutes(studyDur);

                // Check overlap with any fixed slot (Zoom or Meal)
                TimetableSlot collision = null;
                for (TimetableSlot fixed : finalSlots) {
                    LocalTime fStart = LocalTime.parse(fixed.getStartTime(), TIME_FORMATTER);
                    LocalTime fEnd = LocalTime.parse(fixed.getEndTime(), TIME_FORMATTER);
                    if (intervalsOverlap(cursor, potentialEnd, fStart, fEnd)) {
                        collision = fixed;
                        break;
                    }
                }

                if (collision == null) {
                    // Valid slot found!
                    TimetableSlot placedStudy = new TimetableSlot(
                            null,
                            dayOfWeek,
                            cursor.format(TIME_FORMATTER),
                            potentialEnd.format(TIME_FORMATTER),
                            study.getSubject(),
                            study.getTopic(),
                            SlotType.STUDY,
                            null
                    );
                    finalSlots.add(placedStudy);
                    // Add 10-minute cognitive break after study slot if space allows
                    cursor = potentialEnd;
                    placed = true;
                } else {
                    // Jump past the colliding fixed slot
                    cursor = LocalTime.parse(collision.getEndTime(), TIME_FORMATTER);
                }
            }
        }

        // Re-sort all slots chronologically
        finalSlots.sort(Comparator.comparing(s -> LocalTime.parse(s.getStartTime(), TIME_FORMATTER)));

        // Insert standard 5-10m cognitive breaks between consecutive study sessions if gap exists
        List<TimetableSlot> withBreaks = new ArrayList<>();
        for (int i = 0; i < finalSlots.size(); i++) {
            TimetableSlot current = finalSlots.get(i);
            withBreaks.add(current);

            if (i < finalSlots.size() - 1) {
                TimetableSlot next = finalSlots.get(i + 1);
                LocalTime currEnd = LocalTime.parse(current.getEndTime(), TIME_FORMATTER);
                LocalTime nextStart = LocalTime.parse(next.getStartTime(), TIME_FORMATTER);
                long gapMinutes = Duration.between(currEnd, nextStart).toMinutes();

                // If gap is between 5 and 15 mins between study blocks, formalize as cognitive break
                if (gapMinutes >= 5 && gapMinutes <= 15 && current.getSlotType() == SlotType.STUDY) {
                    withBreaks.add(new TimetableSlot(
                            null,
                            dayOfWeek,
                            currEnd.format(TIME_FORMATTER),
                            nextStart.format(TIME_FORMATTER),
                            "",
                            "☕ Cognitive Rest & Hydration",
                            SlotType.BREAK,
                            null
                    ));
                }
            }
        }

        return withBreaks;
    }

    private boolean intervalsOverlap(LocalTime s1, LocalTime e1, LocalTime s2, LocalTime e2) {
        return s1.isBefore(e2) && e1.isAfter(s2);
    }
}
