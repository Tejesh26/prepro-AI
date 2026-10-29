package com.tejesh.timetable.dto;

import java.util.List;
import java.util.Map;

/**
 * <h2>CompanionStatusDto – Live Study Companion & Smart Alarm Status</h2>
 *
 * <p>Transfers real-time schedule state, countdowns, and dynamic AI prompts
 * to the web client for live audio/speech alarms and instant timetable updates.
 */
public class CompanionStatusDto {

    private boolean hasTimetable;
    private String currentDay;
    private String currentTime;
    private boolean warningActive;
    private String alertType; // "UPCOMING_SLOT", "SLOT_ENDING_SOON", "UPCOMING_MEETING", "ON_BREAK", "IDLE"
    private String smartPrompt;
    private String speechText;

    private Map<String, Object> currentSlot;
    private Map<String, Object> upcomingSlot;
    private Map<String, Object> nextImportantEvent;

    private List<String> availableSubjects;

    public CompanionStatusDto() {}

    public boolean isHasTimetable() { return hasTimetable; }
    public void setHasTimetable(boolean hasTimetable) { this.hasTimetable = hasTimetable; }

    public String getCurrentDay() { return currentDay; }
    public void setCurrentDay(String currentDay) { this.currentDay = currentDay; }

    public String getCurrentTime() { return currentTime; }
    public void setCurrentTime(String currentTime) { this.currentTime = currentTime; }

    public boolean isWarningActive() { return warningActive; }
    public void setWarningActive(boolean warningActive) { this.warningActive = warningActive; }

    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }

    public String getSmartPrompt() { return smartPrompt; }
    public void setSmartPrompt(String smartPrompt) { this.smartPrompt = smartPrompt; }

    public String getSpeechText() { return speechText; }
    public void setSpeechText(String speechText) { this.speechText = speechText; }

    public Map<String, Object> getCurrentSlot() { return currentSlot; }
    public void setCurrentSlot(Map<String, Object> currentSlot) { this.currentSlot = currentSlot; }

    public Map<String, Object> getUpcomingSlot() { return upcomingSlot; }
    public void setUpcomingSlot(Map<String, Object> upcomingSlot) { this.upcomingSlot = upcomingSlot; }

    public Map<String, Object> getNextImportantEvent() { return nextImportantEvent; }
    public void setNextImportantEvent(Map<String, Object> nextImportantEvent) { this.nextImportantEvent = nextImportantEvent; }

    public List<String> getAvailableSubjects() { return availableSubjects; }
    public void setAvailableSubjects(List<String> availableSubjects) { this.availableSubjects = availableSubjects; }
}
