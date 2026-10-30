package com.tejesh.timetable.repository;

import com.tejesh.timetable.model.SlotType;
import com.tejesh.timetable.model.TimetableSlot;
import com.tejesh.timetable.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class TimetableSlotRepositoryTest {

    @Autowired
    private TimetableSlotRepository slotRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Verify LUNCH, SNACK, DINNER slot types can be persisted and retrieved without constraint violation")
    void testSaveAndRetrieveMealSlots() {
        User user = new User("meal_test_user", "password123", "meal@test.com");
        user.setExamTarget("JEE 2026");
        user = userRepository.save(user);

        TimetableSlot lunchSlot = new TimetableSlot(
                null, "Monday", "13:00", "13:45",
                "🍽️ Lunch Break", "Nutritional Recovery", SlotType.LUNCH, null
        );
        lunchSlot.setUser(user);

        TimetableSlot snackSlot = new TimetableSlot(
                null, "Monday", "16:30", "16:45",
                "🍎 Snack Break", "Energy Refuel", SlotType.SNACK, null
        );
        snackSlot.setUser(user);

        TimetableSlot dinnerSlot = new TimetableSlot(
                null, "Monday", "20:00", "20:30",
                "🍲 Dinner Break", "Evening Nourishment", SlotType.DINNER, null
        );
        dinnerSlot.setUser(user);

        assertDoesNotThrow(() -> {
            slotRepository.saveAll(List.of(lunchSlot, snackSlot, dinnerSlot));
        });

        List<TimetableSlot> savedSlots = slotRepository.findByUserOrderByDayOfWeekAscStartTimeAsc(user);
        assertEquals(3, savedSlots.size());
        assertTrue(savedSlots.stream().anyMatch(s -> s.getSlotType() == SlotType.LUNCH));
        assertTrue(savedSlots.stream().anyMatch(s -> s.getSlotType() == SlotType.SNACK));
        assertTrue(savedSlots.stream().anyMatch(s -> s.getSlotType() == SlotType.DINNER));
    }
}
