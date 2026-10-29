package com.tejesh.timetable.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.AWTException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * <h2>NotificationService – Native Desktop Alerts & User Confirmation Dialogs</h2>
 *
 * <p>Provides two categories of user notifications:
 *
 * <h3>1. Passive Alerts ({@link #sendDesktopAlert})</h3>
 * <ul>
 *   <li>Audible beep via {@code Toolkit.getDefaultToolkit().beep()}</li>
 *   <li>Native OS system-tray toast notification (when SystemTray is supported)</li>
 *   <li>Console log fallback (always fires, even on headless systems)</li>
 * </ul>
 *
 * <h3>2. Interactive Confirmation ({@link #askMeetingConfirmation})</h3>
 * <ul>
 *   <li>Always-on-top {@code JOptionPane} dialog on the AWT Event Dispatch Thread</li>
 *   <li>Returns {@code true} if user confirms, {@code false} if they decline</li>
 *   <li>Used by the automation scheduler 10 minutes before a Zoom meeting
 *       to decide whether to auto-launch the URL</li>
 * </ul>
 *
 * <p><b>Prerequisite:</b> {@code System.setProperty("java.awt.headless", "false")}
 * must be called in {@code main()} before Spring Boot starts. If AWT is in headless
 * mode, SystemTray will be unavailable and the service gracefully falls back to
 * console-only output.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /** Singleton TrayIcon — added to SystemTray on startup, removed on shutdown. */
    private TrayIcon trayIcon;

    // ══════════════════════════════════════════════════════════════════════════
    //  Lifecycle
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Initializes the {@link SystemTray} icon when the Spring context starts.
     *
     * <p>Uses a programmatically generated 16×16 icon (a filled blue circle)
     * so no external image files are required. Falls back gracefully if
     * SystemTray is unsupported on the current platform.
     */
    @PostConstruct
    public void initSystemTray() {
        if (!SystemTray.isSupported()) {
            log.warn("NotificationService: SystemTray is NOT supported on this platform. " +
                     "Toast notifications will fall back to console output only.");
            return;
        }
        try {
            BufferedImage icon = buildTrayIconImage();
            trayIcon = new TrayIcon(icon, "AI Study Assistant");
            trayIcon.setImageAutoSize(true);
            trayIcon.setToolTip("AI Personalized Timetable & Study Assistant");
            SystemTray.getSystemTray().add(trayIcon);
            log.info("NotificationService: ✅ SystemTray icon initialized successfully.");
        } catch (AWTException e) {
            log.warn("NotificationService: Failed to add icon to SystemTray: {}. " +
                     "Continuing with console-only notifications.", e.getMessage());
            trayIcon = null;
        }
    }

    /**
     * Removes the tray icon cleanly when the Spring context shuts down.
     * Prevents orphaned icons remaining in the system tray after the app exits.
     */
    @PreDestroy
    public void cleanupSystemTray() {
        if (trayIcon != null && SystemTray.isSupported()) {
            SystemTray.getSystemTray().remove(trayIcon);
            log.info("NotificationService: SystemTray icon removed on shutdown.");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Sends a passive desktop alert: plays a system beep AND displays a
     * native OS toast notification (if SystemTray is available).
     *
     * <p>This method is non-blocking — it triggers the notification and
     * returns immediately. The tray notification auto-dismisses after a
     * platform-defined timeout (typically 3–5 seconds on Windows).
     *
     * @param title   short notification title (e.g., "📚 Time to Study!")
     * @param message body text shown in the notification (e.g., subject + topic)
     */
    public void sendDesktopAlert(String title, String message) {
        // ── 1. Audible system beep ────────────────────────────────────────────
        try {
            Toolkit.getDefaultToolkit().beep();
        } catch (Exception e) {
            log.debug("NotificationService: Beep not available — {}", e.getMessage());
        }

        // ── 2. Console log (always fires) ─────────────────────────────────────
        log.info("\n" +
                 "╔══════════════════════════════════════════════════════╗\n" +
                 "║  🔔 ALERT: {}\n" +
                 "║  {}\n" +
                 "╚══════════════════════════════════════════════════════╝",
                 title, message);

        // ── 3. System tray toast notification ────────────────────────────────
        if (trayIcon != null) {
            // displayMessage is thread-safe and non-blocking
            trayIcon.displayMessage(title, message, TrayIcon.MessageType.INFO);
        } else {
            log.debug("NotificationService: TrayIcon unavailable — console log shown only.");
        }
    }

    /**
     * Displays an interactive, always-on-top confirmation dialog asking the
     * student whether they want to join an upcoming Zoom meeting.
     *
     * <p>Runs the Swing dialog on the AWT Event Dispatch Thread using
     * {@link SwingUtilities#invokeAndWait} — this blocks the calling thread
     * (the scheduler) until the user responds, which is acceptable since the
     * scheduler thread is separate from the main application thread.
     *
     * @param username     student's display name (e.g., "Tejesh")
     * @param meetingTitle title of the Zoom meeting (e.g., "Weekly Physics Lecture")
     * @param startTime    meeting start time in "HH:mm" format (e.g., "17:00")
     * @return {@code true} if user clicks "Yes / Join", {@code false} if "No / Skip"
     */
    public boolean askMeetingConfirmation(String username, String meetingTitle, String startTime) {
        // Beep to draw attention before the dialog appears
        try {
            Toolkit.getDefaultToolkit().beep();
            Thread.sleep(200);
            Toolkit.getDefaultToolkit().beep(); // double-beep for meeting alerts
        } catch (Exception ignored) {}

        String dialogMessage =
                "Hey " + username + "! 📅\n\n" +
                "You have an upcoming Zoom meeting:\n" +
                "\"" + meetingTitle + "\"\n" +
                "Scheduled at: " + startTime + "\n\n" +
                "Would you like to join? Clicking YES will automatically\n" +
                "open the meeting link in your browser/Zoom client.";

        log.info("NotificationService: Showing Zoom confirmation dialog for '{}' at {}",
                meetingTitle, startTime);

        AtomicBoolean userConfirmed = new AtomicBoolean(false);

        try {
            SwingUtilities.invokeAndWait(() -> {
                // Create an always-on-top parent frame so the dialog floats above all windows
                JFrame parentFrame = new JFrame("Study Assistant – Meeting Alert");
                parentFrame.setAlwaysOnTop(true);
                parentFrame.setUndecorated(true);
                parentFrame.setSize(1, 1);
                parentFrame.setLocationRelativeTo(null); // center on screen
                parentFrame.setVisible(true);
                parentFrame.toFront();
                parentFrame.requestFocus();

                int choice = JOptionPane.showConfirmDialog(
                        parentFrame,
                        dialogMessage,
                        "🎥 Upcoming Zoom Meeting – Study Assistant",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE
                );

                userConfirmed.set(choice == JOptionPane.YES_OPTION);
                parentFrame.dispose();
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("NotificationService: Meeting confirmation dialog interrupted — defaulting to YES.");
            userConfirmed.set(true);
        } catch (Exception e) {
            log.error("NotificationService: Failed to show confirmation dialog: {}. Defaulting to YES.",
                    e.getMessage(), e);
            userConfirmed.set(true); // Default: launch the meeting if dialog fails
        }

        String decision = userConfirmed.get() ? "✅ YES (will auto-launch)" : "❌ NO (skipping auto-launch)";
        log.info("NotificationService: User decision for '{}' → {}", meetingTitle, decision);

        return userConfirmed.get();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Builds a 16×16 programmatic icon for the system tray.
     *
     * <p>Draws a filled gradient circle (blue centre, white ring) without
     * requiring any external image file — pure Java AWT {@link Graphics2D}.
     */
    private BufferedImage buildTrayIconImage() {
        int size = 16;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Outer white ring
        g.setColor(Color.WHITE);
        g.fillOval(0, 0, size - 1, size - 1);

        // Inner blue filled circle (study assistant brand colour)
        g.setColor(new Color(33, 97, 200)); // Material Design Blue 700
        g.fillOval(2, 2, size - 5, size - 5);

        // Small white book symbol suggestion (3 horizontal lines)
        g.setColor(Color.WHITE);
        g.fillRect(5, 5,  6, 1);
        g.fillRect(5, 7,  6, 1);
        g.fillRect(5, 9,  6, 1);

        g.dispose();
        return image;
    }
}
