package com.tejesh.timetable.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * <h2>GlobalExceptionHandler – Enterprise Exception Handler</h2>
 *
 * <p>Safeguards public-facing web routes against unhandled crashes (e.g., Gemini API timeouts,
 * database deadlocks, network disconnections).
 * Renders user-friendly, secure error views without exposing internal system architectures
 * or sensitive stack traces to the public.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Intercepts oversized PDF syllabus uploads to prevent server denial-of-service.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxUploadSizeExceeded(MaxUploadSizeExceededException exc,
                                             RedirectAttributes redirectAttributes) {
        log.warn("GlobalExceptionHandler: File upload limit exceeded: {}", exc.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage",
                "The uploaded syllabus PDF exceeds the maximum permitted file size (10 MB). Please upload a smaller PDF file.");
        return "redirect:/dashboard";
    }

    /**
     * Catches all unhandled exceptions across the application.
     * Logs the full trace internally for server diagnostics and renders a friendly, sanitized
     * error page for the end user.
     */
    @ExceptionHandler(Exception.class)
    public String handleGenericException(Exception exc,
                                         HttpServletRequest request,
                                         Model model) {
        log.error("GlobalExceptionHandler: Unhandled production exception at URL '{}'", request.getRequestURI(), exc);
        
        model.addAttribute("errorMessage",
                "We encountered an unexpected issue while processing your study plan. Please try again or return to your dashboard.");
        model.addAttribute("errorPath", request.getRequestURI());
        return "error";
    }
}
