package com.tejesh.timetable.service;

import com.tejesh.timetable.model.SlotType;
import com.tejesh.timetable.model.TimetableSlot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * <h2>Unit Tests for MealBreakService</h2>
 *
 * Validates automatic meal break insertion according to cognitive pacing rules:
 * <ul>
 *   <li><b>Test 1:</b> 9:00–17:00 (lunch + evening snack)</li>
 *   <li><b>Test 2:</b> 9:00–12:00 (no lunch)</li>
 *   <li><b>Test 3:</b> 6:00–22:00 (all meals)</li>
 *   <li><b>Test 4:</b> 18:00–23:00 (dinner + night snack)</li>
 *   <li><b>Test 5:</b> Window with a Zoom class at 13:00 (non-overlapping lunch placement)</li>
 *   <li><b>Test 6:</b> User control: "no lunch" instruction override and custom meal times</li>
 * </ul>
 */
class MealBreakServiceTest {

    private MealBreakService mealBreakService;

    @BeforeEach
    void setUp() {
        mealBreakService = new MealBreakService();
    }

    /**
     * Helper to generate contiguous 30m study + 10m break slots across a time window.
     */
    private List<TimetableSlot> buildPacedSlots(String day, LocalTime start, LocalTime end) {
        List<TimetableSlot> slots = new ArrayList<>();
        LocalTime cursor = start;
        int count = 1;
        while (cursor.plusMinutes(30).isBefore(end) || cursor.plusMinutes(30).equals(end)) {
            LocalTime studyEnd = cursor.plusMinutes(30);
            slots.add(new TimetableSlot(null, day, cursor.toString(), studyEnd.toString(),
                    "Subject " + count, "Topic " + count, SlotType.STUDY, null));
            count++;
            cursor = studyEnd;

            if (cursor.plusMinutes(10).isBefore(end) || cursor.plusMinutes(10).equals(end)) {
                LocalTime breakEnd = cursor.plusMinutes(10);
                slots.add(new TimetableSlot(null, day, cursor.toString(), breakEnd.toString(),
                        "", "☕ Cognitive Break", SlotType.BREAK, null));
                cursor = breakEnd;
            }
        }
        return slots;
    }

    @Test
    @DisplayName("1. Window 09:00–17:00 inserts Lunch (45m) and Evening Snack (15m)")
    void testNineToSeventeen_LunchAndEveningSnack() {
        LocalTime windowStart = LocalTime.of(9, 0);
        LocalTime windowEnd = LocalTime.of(17, 0);
        List<TimetableSlot> initialSlots = buildPacedSlots("Monday", windowStart, windowEnd);

        List<TimetableSlot> result = mealBreakService.insertMealBreaks(initialSlots, windowStart, windowEnd);

        assertNotNull(result, "Result slots should not be null");

        // Verify lunch is present (covers 12:00-14:30 and duration is 8h >= 4h)
        List<TimetableSlot> lunchSlots = result.stream()
                .filter(s -> s.getSlotType() == SlotType.LUNCH)
                .toList();
        assertEquals(1, lunchSlots.size(), "Should have exactly 1 lunch break");
        TimetableSlot lunch = lunchSlots.get(0);
        assertEquals("13:00", lunch.getStartTime(), "Default lunch should start at 13:00");
        assertEquals("13:45", lunch.getEndTime(), "Lunch should be 45 minutes (ends at 13:45)");

        // Verify evening snack is present (lasts past 16:30)
        List<TimetableSlot> snackSlots = result.stream()
                .filter(s -> s.getSlotType() == SlotType.SNACK)
                .toList();
        assertEquals(1, snackSlots.size(), "Should have exactly 1 evening snack break");
        TimetableSlot eveningSnack = snackSlots.get(0);
        assertEquals("16:30", eveningSnack.getStartTime(), "Evening snack starts at 16:30");
        assertEquals("16:45", eveningSnack.getEndTime(), "Evening snack lasts 15 minutes");

        // Verify NO morning snack (study started at 9:00, not before 9:00)
        boolean hasMorningSnack = result.stream()
                .anyMatch(s -> s.getSlotType() == SlotType.SNACK && s.getStartTime().startsWith("10:"));
        assertFalse(hasMorningSnack, "Should NOT have morning snack when window starts at 9:00");

        // Total meal breaks should be exactly 2 (Lunch + Evening Snack)
        long totalMeals = result.stream().filter(s -> s.getSlotType() != null && s.getSlotType().isMeal()).count();
        assertEquals(2, totalMeals, "Total meal slots should be exactly 2 (lunch + evening snack)");
    }

    @Test
    @DisplayName("2. Window 09:00–12:00 (Short 3-hour window) adds NO lunch break")
    void testNineToTwelve_NoLunch() {
        LocalTime windowStart = LocalTime.of(9, 0);
        LocalTime windowEnd = LocalTime.of(12, 0);
        List<TimetableSlot> initialSlots = buildPacedSlots("Tuesday", windowStart, windowEnd);

        List<TimetableSlot> result = mealBreakService.insertMealBreaks(initialSlots, windowStart, windowEnd);

        assertNotNull(result);

        // Window is exactly 3 hours (not >= 4h) and ends at 12:00 -> NO lunch
        boolean hasLunch = result.stream().anyMatch(s -> s.getSlotType() == SlotType.LUNCH);
        assertFalse(hasLunch, "3-hour window ending at 12:00 must NOT contain lunch break");

        // No meal breaks of any kind
        long mealCount = result.stream().filter(s -> s.getSlotType() != null && s.getSlotType().isMeal()).count();
        assertEquals(0, mealCount, "Should have 0 meal breaks for 9:00-12:00 window");
    }

    @Test
    @DisplayName("3. Window 06:00–22:00 (16-hour window) includes All Meals (Morning Snack, Lunch, Evening Snack, Dinner)")
    void testSixToTwentyTwo_AllMeals() {
        LocalTime windowStart = LocalTime.of(6, 0);
        LocalTime windowEnd = LocalTime.of(22, 0);
        List<TimetableSlot> initialSlots = buildPacedSlots("Wednesday", windowStart, windowEnd);

        List<TimetableSlot> result = mealBreakService.insertMealBreaks(initialSlots, windowStart, windowEnd);

        assertNotNull(result);

        // Morning Snack (started at 6:00 < 9:00, covers 10:30-11:00)
        boolean hasMorningSnack = result.stream()
                .anyMatch(s -> s.getSlotType() == SlotType.SNACK && s.getStartTime().equals("10:30"));
        assertTrue(hasMorningSnack, "Should have morning snack at 10:30");

        // Lunch Break (covers 12:00-14:30 and > 4h)
        boolean hasLunch = result.stream()
                .anyMatch(s -> s.getSlotType() == SlotType.LUNCH && s.getStartTime().equals("13:00"));
        assertTrue(hasLunch, "Should have lunch at 13:00");

        // Evening Snack (covers 16:30-17:30 and lasts past 16:30)
        boolean hasEveningSnack = result.stream()
                .anyMatch(s -> s.getSlotType() == SlotType.SNACK && s.getStartTime().equals("16:30"));
        assertTrue(hasEveningSnack, "Should have evening snack at 16:30");

        // Dinner Break (runs past 19:30)
        boolean hasDinner = result.stream()
                .anyMatch(s -> s.getSlotType() == SlotType.DINNER && s.getStartTime().equals("20:00"));
        assertTrue(hasDinner, "Should have dinner break at 20:00");

        // Total 4 meals
        long totalMeals = result.stream().filter(s -> s.getSlotType() != null && s.getSlotType().isMeal()).count();
        assertEquals(4, totalMeals, "Should include all 4 primary meals (morning snack, lunch, evening snack, dinner)");
    }

    @Test
    @DisplayName("4. Window 18:00–23:00 includes Dinner (20:00) and Night Snack (22:00)")
    void testEighteenToTwentyThree_DinnerAndNightSnack() {
        LocalTime windowStart = LocalTime.of(18, 0);
        LocalTime windowEnd = LocalTime.of(23, 0);
        List<TimetableSlot> initialSlots = buildPacedSlots("Thursday", windowStart, windowEnd);

        List<TimetableSlot> result = mealBreakService.insertMealBreaks(initialSlots, windowStart, windowEnd);

        assertNotNull(result);

        // No lunch or morning/evening snacks
        assertFalse(result.stream().anyMatch(s -> s.getSlotType() == SlotType.LUNCH), "Should NOT have lunch");

        // Dinner break around 20:00 (window runs past 19:30)
        boolean hasDinner = result.stream()
                .anyMatch(s -> s.getSlotType() == SlotType.DINNER && s.getStartTime().equals("20:00"));
        assertTrue(hasDinner, "Should have dinner at 20:00");

        // Night snack around 22:00 (window runs past 22:00)
        boolean hasNightSnack = result.stream()
                .anyMatch(s -> s.getSlotType() == SlotType.SNACK && s.getStartTime().equals("22:00"));
        assertTrue(hasNightSnack, "Should have night snack at 22:00");

        long totalMeals = result.stream().filter(s -> s.getSlotType() != null && s.getSlotType().isMeal()).count();
        assertEquals(2, totalMeals, "Should have exactly 2 meals (dinner + night snack)");
    }

    @Test
    @DisplayName("5. Window with a Zoom class at 13:00 places Lunch without overlapping Zoom class")
    void testWindowWithZoomAtThirteen_NoOverlap() {
        LocalTime windowStart = LocalTime.of(9, 0);
        LocalTime windowEnd = LocalTime.of(17, 0);
        List<TimetableSlot> initialSlots = buildPacedSlots("Friday", windowStart, windowEnd);

        // Add a fixed Zoom lecture from 13:00 to 14:00
        TimetableSlot zoomSlot = new TimetableSlot(null, "Friday", "13:00", "14:00",
                "Advanced Mathematics", "Calculus Live", SlotType.ZOOM_MEETING, "https://zoom.us/j/12345");
        initialSlots.add(zoomSlot);

        List<TimetableSlot> result = mealBreakService.insertMealBreaks(initialSlots, windowStart, windowEnd);

        assertNotNull(result);

        // 1. Verify Zoom meeting is preserved at exactly 13:00 - 14:00
        TimetableSlot resultZoom = result.stream()
                .filter(s -> s.getSlotType() == SlotType.ZOOM_MEETING)
                .findFirst()
                .orElse(null);
        assertNotNull(resultZoom, "Zoom meeting must be preserved");
        assertEquals("13:00", resultZoom.getStartTime());
        assertEquals("14:00", resultZoom.getEndTime());

        // 2. Verify Lunch break is present and does NOT overlap 13:00–14:00
        TimetableSlot lunch = result.stream()
                .filter(s -> s.getSlotType() == SlotType.LUNCH)
                .findFirst()
                .orElse(null);
        assertNotNull(lunch, "Lunch break must be scheduled");

        LocalTime lunchStart = LocalTime.parse(lunch.getStartTime());
        LocalTime lunchEnd = LocalTime.parse(lunch.getEndTime());

        boolean overlapsZoom = lunchStart.isBefore(LocalTime.of(14, 0)) && lunchEnd.isAfter(LocalTime.of(13, 0));
        assertFalse(overlapsZoom, "Lunch must NEVER overlap the 13:00-14:00 Zoom class");

        // Lunch is placed either before (12:15 - 13:00) or after (14:00 - 14:45)
        assertTrue(lunchEnd.equals(LocalTime.of(13, 0)) || lunchStart.equals(LocalTime.of(14, 0)),
                "Lunch should be placed adjacent to the Zoom meeting (before or after)");
    }

    @Test
    @DisplayName("6. User Control: 'no lunch' instruction suppresses lunch break")
    void testUserControl_NoLunchInstruction() {
        LocalTime windowStart = LocalTime.of(9, 0);
        LocalTime windowEnd = LocalTime.of(17, 0);
        List<TimetableSlot> initialSlots = buildPacedSlots("Monday", windowStart, windowEnd);

        List<TimetableSlot> result = mealBreakService.insertMealBreaks(
                initialSlots, windowStart, windowEnd, true, LocalTime.of(13, 0), 45,
                "I am fasting today, no lunch please."
        );

        // Lunch must be skipped
        boolean hasLunch = result.stream().anyMatch(s -> s.getSlotType() == SlotType.LUNCH);
        assertFalse(hasLunch, "User said 'no lunch', so lunch must be skipped");

        // Evening snack should still be retained
        boolean hasSnack = result.stream().anyMatch(s -> s.getSlotType() == SlotType.SNACK);
        assertTrue(hasSnack, "Evening snack should still be present");
    }

    @Test
    @DisplayName("7. User Control: Custom lunch time and duration")
    void testUserControl_CustomLunchTimeAndDuration() {
        LocalTime windowStart = LocalTime.of(9, 0);
        LocalTime windowEnd = LocalTime.of(17, 0);
        List<TimetableSlot> initialSlots = buildPacedSlots("Monday", windowStart, windowEnd);

        List<TimetableSlot> result = mealBreakService.insertMealBreaks(
                initialSlots, windowStart, windowEnd, true, LocalTime.of(12, 30), 30, null
        );

        TimetableSlot lunch = result.stream()
                .filter(s -> s.getSlotType() == SlotType.LUNCH)
                .findFirst()
                .orElse(null);
        assertNotNull(lunch, "Custom lunch must be present");
        assertEquals("12:30", lunch.getStartTime(), "Custom lunch starts at 12:30");
        assertEquals("13:00", lunch.getEndTime(), "Custom lunch of 30m ends at 13:00");
    }

    @Test
    @DisplayName("8. Verify that NO two breaks or slots ever overlap across entire day")
    void testNoTwoBreaksOrSlotsOverlap() {
        LocalTime windowStart = LocalTime.of(8, 0);
        LocalTime windowEnd = LocalTime.of(22, 0);
        List<TimetableSlot> initialSlots = buildPacedSlots("Monday", windowStart, windowEnd);

        List<TimetableSlot> result = mealBreakService.insertMealBreaks(initialSlots, windowStart, windowEnd);

        assertNotNull(result);
        assertFalse(result.isEmpty());

        for (int i = 0; i < result.size() - 1; i++) {
            TimetableSlot current = result.get(i);
            TimetableSlot next = result.get(i + 1);

            LocalTime currEnd = LocalTime.parse(current.getEndTime());
            LocalTime nextStart = LocalTime.parse(next.getStartTime());

            // Zero overlap rule: currEnd must be <= nextStart
            assertFalse(currEnd.isAfter(nextStart),
                    String.format("Overlap detected between slot %s (%s-%s) and slot %s (%s-%s)",
                            current.getSubject(), current.getStartTime(), current.getEndTime(),
                            next.getSubject(), next.getStartTime(), next.getEndTime()));

            // Verify two breaks (meal or cognitive) never directly abut or overlap
            boolean currentIsBreak = current.getSlotType() == SlotType.BREAK || (current.getSlotType() != null && current.getSlotType().isMeal());
            boolean nextIsBreak = next.getSlotType() == SlotType.BREAK || (next.getSlotType() != null && next.getSlotType().isMeal());

            assertFalse(currentIsBreak && nextIsBreak && currEnd.equals(nextStart),
                    String.format("Two breaks directly abut without study gap: %s (%s-%s) and %s (%s-%s)",
                            current.getSubject(), current.getStartTime(), current.getEndTime(),
                            next.getSubject(), next.getStartTime(), next.getEndTime()));
        }
    }

    @Test
    @DisplayName("9. Verify break types are cleanly categorized according to time (Lunch, Snack, Small Break)")
    void testBreakCategorizationAccordingToTime() {
        LocalTime windowStart = LocalTime.of(8, 0);
        LocalTime windowEnd = LocalTime.of(21, 0);
        List<TimetableSlot> initialSlots = buildPacedSlots("Tuesday", windowStart, windowEnd);

        List<TimetableSlot> result = mealBreakService.insertMealBreaks(initialSlots, windowStart, windowEnd);

        // Check Lunch Break
        TimetableSlot lunch = result.stream().filter(s -> s.getSlotType() == SlotType.LUNCH).findFirst().orElse(null);
        assertNotNull(lunch, "Lunch break must exist in 8:00-21:00 window");
        assertTrue(lunch.getSubject().contains("Lunch"));
        assertEquals("13:00", lunch.getStartTime());

        // Check Snack Break
        TimetableSlot snack = result.stream().filter(s -> s.getSlotType() == SlotType.SNACK).findFirst().orElse(null);
        assertNotNull(snack, "Snack break must exist");
        assertTrue(snack.getSubject().contains("Snack"));

        // Check Cognitive Small Break
        TimetableSlot smallBreak = result.stream().filter(s -> s.getSlotType() == SlotType.BREAK).findFirst().orElse(null);
        assertNotNull(smallBreak, "Small cognitive break must exist between study sessions");
        assertTrue(smallBreak.getTopic().contains("Cognitive") || smallBreak.getSubject().contains("Break"));
    }
}
