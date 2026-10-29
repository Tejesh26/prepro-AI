package com.tejesh.timetable.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tejesh.timetable.model.ProficiencyLevel;
import com.tejesh.timetable.model.SubjectInput;
import com.tejesh.timetable.model.TimetableSlot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <h2>AIBrainService – The Core Intelligence Engine</h2>
 *
 * <p>Responsible for:
 * <ol>
 *   <li>Building a detailed, psychology-backed prompt from student input.</li>
 *   <li>Sending a POST request to the Gemini 2.5 Flash REST API via
 *       Java's built-in {@link java.net.http.HttpClient} (no third-party HTTP lib).</li>
 *   <li>Parsing the strict JSON array response with Jackson
 *       {@link ObjectMapper} into {@code List<TimetableSlot>}.</li>
 *   <li>Robust error handling: logs the raw response and throws a meaningful
 *       {@link RuntimeException} on failure so the UI can show a user-friendly
 *       error dialog.</li>
 * </ol>
 *
 * <h3>Priority Weighting Rule (enforced in the prompt)</h3>
 * <ul>
 *   <li>{@code POOR}    → 50 % of total study slots</li>
 *   <li>{@code AVERAGE} → 30 % of total study slots</li>
 *   <li>{@code GOOD}    → 20 % of total study slots (revision)</li>
 * </ul>
 *
 * <h3>Psychological Pacing Rule (enforced in the prompt)</h3>
 * <p>Every study block is exactly 30 minutes, always followed by a
 * 5–10 minute {@code BREAK} slot before the next study block.
 */
@Service
public class AIBrainService {

    private static final Logger log = LoggerFactory.getLogger(AIBrainService.class);

    // ──────────────────────────────────────────────────────────────────────────
    // Spring-injected configuration values from application.properties
    // ──────────────────────────────────────────────────────────────────────────

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    private static final List<String> FALLBACK_MODELS = List.of(
            "gemini-3.5-flash",
            "gemini-3.1-flash-lite",
            "gemini-3-flash-preview",
            "gemini-3.8-flash"
    );

    // ──────────────────────────────────────────────────────────────────────────
    // Shared beans injected from AppConfig (thread-safe singletons)
    // ──────────────────────────────────────────────────────────────────────────

    private final ObjectMapper objectMapper;
    private final HttpClient   httpClient;

    /**
     * Constructor injection from {@link com.tejesh.timetable.config.AppConfig}.
     * Both beans are shared singletons — no extra instances created per service.
     */
    public AIBrainService(ObjectMapper objectMapper, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.httpClient   = httpClient;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Generates a fully personalized weekly study timetable by calling the
     * Gemini API and deserializing its JSON response.
     *
     * @param examTarget             e.g., "JEE Advanced", "NEET", "UPSC"
     * @param subjects               list of subjects with topics and proficiency
     * @param extraUserInstructions  free-text instructions (study hours, Zoom links, etc.)
     * @param extractedSyllabusText  raw text extracted from an uploaded syllabus PDF
     *                               (may be empty string "" if no PDF was uploaded)
     * @return parsed list of {@link TimetableSlot} objects ready for display/storage
     * @throws RuntimeException if the API call fails or the response cannot be parsed
     */
    public List<TimetableSlot> generatePersonalizedTimetable(
            String examTarget,
            List<SubjectInput> subjects,
            String extraUserInstructions,
            String extractedSyllabusText) {

        log.info("=== AIBrainService: Starting timetable generation for exam: {} ===", examTarget);

        // Step 1 – Build the structured prompt
        String prompt = buildPrompt(examTarget, subjects, extraUserInstructions, extractedSyllabusText);
        log.debug("Constructed prompt ({} chars):\n{}", prompt.length(), prompt);

        // Step 2 – Wrap the prompt in the Gemini request JSON body
        String requestBody = buildGeminiRequestBody(prompt);

        // Step 3 – Send HTTP POST to Gemini API
        String rawResponse = callGeminiApi(requestBody);
        log.debug("Raw Gemini API response:\n{}", rawResponse);

        // Step 4 – Extract the text content from Gemini's response envelope
        String jsonArray = extractJsonArrayFromResponse(rawResponse);
        log.debug("Extracted JSON array string:\n{}", jsonArray);

        // Step 5 – Parse JSON array → List<TimetableSlot>
        List<TimetableSlot> slots = parseSlots(jsonArray);
        log.info("=== AIBrainService: Successfully parsed {} timetable slots ===", slots.size());

        return slots;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Prompt Construction
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds the detailed system+user prompt that drives Gemini's output.
     *
     * <p>The prompt is carefully engineered to:
     * <ul>
     *   <li>Assign Gemini a clear expert persona.</li>
     *   <li>State all priority-weighting and pacing rules explicitly.</li>
     *   <li>Provide subject data in a structured, readable format.</li>
     *   <li>Demand strictly valid JSON with no markdown decoration.</li>
     * </ul>
     */
    private String buildPrompt(String examTarget,
                               List<SubjectInput> subjects,
                               String extraUserInstructions,
                               String extractedSyllabusText) {

        StringBuilder sb = new StringBuilder();

        // ── Persona & Role ────────────────────────────────────────────────────
        sb.append("You are an expert academic scheduler and cognitive-science specialist ");
        sb.append("who creates personalized study timetables for competitive exam aspirants.\n\n");

        // ── Mission ───────────────────────────────────────────────────────────
        sb.append("TASK: Generate a complete, personalized weekly study timetable for a student ");
        sb.append("preparing for the **").append(examTarget).append("** examination.\n\n");

        // ── Subject Data ──────────────────────────────────────────────────────
        sb.append("=== SUBJECT PROFICIENCY DATA ===\n");
        for (SubjectInput s : subjects) {
            sb.append(String.format("- Subject: %-14s | Proficiency: %-8s | Topics: %s\n",
                    s.getSubjectName(), s.getProficiencyLevel(), s.getTopics()));
        }
        sb.append("\n");

        // ── Priority Weighting Rule ───────────────────────────────────────────
        sb.append("=== RULE 1: PRIORITY TIME ALLOCATION (STRICTLY ENFORCE) ===\n");
        sb.append("Calculate the total number of STUDY slots across the entire week, ");
        sb.append("then distribute them according to the student's proficiency level:\n");
        sb.append("  • POOR    subjects → receive approximately 50% of all STUDY slots.\n");
        sb.append("  • AVERAGE subjects → receive approximately 30% of all STUDY slots.\n");
        sb.append("  • GOOD    subjects → receive approximately 20% of all STUDY slots (revision only).\n\n");

        // Print the actual subject-level mappings for this student
        long poorCount    = subjects.stream().filter(s -> s.getProficiencyLevel() == ProficiencyLevel.POOR).count();
        long avgCount     = subjects.stream().filter(s -> s.getProficiencyLevel() == ProficiencyLevel.AVERAGE).count();
        long goodCount    = subjects.stream().filter(s -> s.getProficiencyLevel() == ProficiencyLevel.GOOD).count();

        sb.append("For this student specifically:\n");
        sb.append("  • POOR subjects    (").append(poorCount)    .append(" subject(s)): ");
        subjects.stream().filter(s -> s.getProficiencyLevel() == ProficiencyLevel.POOR)
                .forEach(s -> sb.append(s.getSubjectName()).append(" "));
        sb.append("\n");
        sb.append("  • AVERAGE subjects (").append(avgCount)     .append(" subject(s)): ");
        subjects.stream().filter(s -> s.getProficiencyLevel() == ProficiencyLevel.AVERAGE)
                .forEach(s -> sb.append(s.getSubjectName()).append(" "));
        sb.append("\n");
        sb.append("  • GOOD subjects    (").append(goodCount)    .append(" subject(s)): ");
        subjects.stream().filter(s -> s.getProficiencyLevel() == ProficiencyLevel.GOOD)
                .forEach(s -> sb.append(s.getSubjectName()).append(" "));
        sb.append("\n\n");

        // ── Psychological Pacing Rule ─────────────────────────────────────────
        sb.append("=== RULE 2: PSYCHOLOGICAL PACING – THE 30-MINUTE RULE (NON-NEGOTIABLE) ===\n");
        sb.append("The human brain cannot maintain deep focus for more than 30 minutes continuously.\n");
        sb.append("Therefore you MUST follow this pattern without exception:\n");
        sb.append("  1. Schedule one STUDY slot of exactly 30 minutes.\n");
        sb.append("  2. IMMEDIATELY follow it with one BREAK slot of 5 to 10 minutes.\n");
        sb.append("  3. Repeat: the NEXT STUDY slot begins only after the BREAK ends.\n");
        sb.append("  ✗ NEVER schedule two consecutive STUDY slots with no BREAK between them.\n\n");

        // ── Custom Instructions ───────────────────────────────────────────────
        sb.append("=== RULE 3: CUSTOM STUDENT INSTRUCTIONS (PARSE AND APPLY EXACTLY) ===\n");
        sb.append("The student has provided the following personal constraints and preferences.\n");
        sb.append("You MUST respect every instruction here (study window, meetings, rest days, etc.):\n");
        sb.append("---\n");
        sb.append(extraUserInstructions.isBlank() ? "(No extra instructions provided)" : extraUserInstructions);
        sb.append("\n---\n\n");

        // Zoom Meeting parsing sub-rule
        sb.append("ZOOM MEETING SUB-RULE:\n");
        sb.append("If the instructions above mention a video/Zoom meeting on a specific day and time:\n");
        sb.append("  • Insert a slot with slotType = \"ZOOM_MEETING\" at that exact day and time.\n");
        sb.append("  • Set the 'meetingLink' field to the URL mentioned (e.g., https://zoom.us/j/...).\n");
        sb.append("  • Set 'subject' to \"Online Class\" and 'topic' to the meeting description.\n");
        sb.append("  • Do NOT schedule any STUDY slot that overlaps with a ZOOM_MEETING slot.\n\n");

        // ── Syllabus Text (optional) ──────────────────────────────────────────
        if (!extractedSyllabusText.isBlank()) {
            sb.append("=== ADDITIONAL CONTEXT: SYLLABUS TEXT (from uploaded PDF) ===\n");
            // Limit syllabus text to avoid exceeding token limits (Gemini 2.5 Flash is generous but safe)
            int limit = Math.min(extractedSyllabusText.length(), 3000);
            sb.append(extractedSyllabusText, 0, limit);
            if (extractedSyllabusText.length() > 3000) sb.append("\n[... syllabus truncated for brevity ...]");
            sb.append("\n\n");
        }

        // ── JSON Output Specification ─────────────────────────────────────────
        sb.append("=== OUTPUT FORMAT: STRICT JSON ONLY ===\n");
        sb.append("Return ONLY a raw JSON array. Do NOT include:\n");
        sb.append("  ✗ Markdown code fences (no ```json or ```)\n");
        sb.append("  ✗ Explanatory text before or after the JSON\n");
        sb.append("  ✗ Comments inside the JSON\n\n");

        sb.append("Each element of the JSON array must have EXACTLY these fields:\n");
        sb.append("{\n");
        sb.append("  \"dayOfWeek\":   \"Monday\"          (String – full English day name),\n");
        sb.append("  \"startTime\":   \"16:00\"           (String – 24-hour HH:mm format),\n");
        sb.append("  \"endTime\":     \"16:30\"           (String – 24-hour HH:mm format),\n");
        sb.append("  \"subject\":     \"Mathematics\"     (String – subject name, or empty string for BREAK),\n");
        sb.append("  \"topic\":       \"Matrices\"        (String – specific topic, or \"Rest\" for BREAK),\n");
        sb.append("  \"slotType\":    \"STUDY\"           (String – one of: STUDY, BREAK, ZOOM_MEETING),\n");
        sb.append("  \"meetingLink\": null               (String or null – only for ZOOM_MEETING slots)\n");
        sb.append("}\n\n");

        sb.append("Generate the full weekly timetable now. Begin the JSON array immediately:");

        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Build Gemini API Request Body
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Wraps the prompt string in the JSON structure required by the Gemini
     * {@code generateContent} REST endpoint.
     *
     * <p>Structure reference:
     * <pre>
     * {
     *   "contents": [{
     *     "parts": [{ "text": "<prompt>" }]
     *   }],
     *   "generationConfig": {
     *     "temperature": 0.4,
     *     "maxOutputTokens": 8192
     *   }
     * }
     * </pre>
     * Lower temperature (0.4) keeps the output deterministic and format-compliant.
     */
    private String buildGeminiRequestBody(String prompt) {
        try {
            // Use Jackson ObjectNode for safe JSON construction (avoids manual escaping bugs)
            var rootNode      = objectMapper.createObjectNode();
            var contentsArray = objectMapper.createArrayNode();
            var contentObj    = objectMapper.createObjectNode();
            var partsArray    = objectMapper.createArrayNode();
            var partObj       = objectMapper.createObjectNode();

            partObj.put("text", prompt);
            partsArray.add(partObj);
            contentObj.set("parts", partsArray);
            contentsArray.add(contentObj);
            rootNode.set("contents", contentsArray);

            // Generation configuration
            var genConfig = objectMapper.createObjectNode();
            genConfig.put("temperature", 0.4);
            genConfig.put("maxOutputTokens", 8192);
            // Enforce strict JSON output from Gemini token decoder
            genConfig.put("response_mime_type", "application/json");
            rootNode.set("generationConfig", genConfig);

            return objectMapper.writeValueAsString(rootNode);

        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize Gemini request body", e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. HTTP Call to Gemini API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Sends the POST request to the Gemini API with automatic model failover.
     *
     * <p>If the primary model returns 503 (high demand spike), 429 (rate limit),
     * or 404, it seamlessly cascades through high-capacity backup models
     * (e.g. gemini-3.5-flash -> gemini-3.1-flash-lite -> gemini-3-flash-preview)
     * so that transient server spikes never disrupt timetable generation.
     *
     * @throws RuntimeException if all candidate models fail
     */
    private String callGeminiApi(String requestBody) {
        // Build candidate endpoints list (configured endpoint first, then fallbacks)
        java.util.List<String> candidateUrls = new java.util.ArrayList<>();
        if (geminiApiUrl != null && !geminiApiUrl.isBlank()) {
            candidateUrls.add(geminiApiUrl.trim());
        }

        for (String model : FALLBACK_MODELS) {
            String fallbackUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent";
            if (!candidateUrls.contains(fallbackUrl)) {
                candidateUrls.add(fallbackUrl);
            }
        }

        String lastErrorMessage = "Unknown error";
        int lastStatusCode = 500;

        for (int i = 0; i < candidateUrls.size(); i++) {
            String targetUrl = candidateUrls.get(i);
            String urlWithKey = targetUrl + "?key=" + geminiApiKey;

            log.info("Sending request to Gemini API (attempt {}/{}): {}", i + 1, candidateUrls.size(), targetUrl);

            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(urlWithKey))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .timeout(Duration.ofSeconds(120))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int statusCode = response.statusCode();

                if (statusCode == 200) {
                    log.info("Gemini API call succeeded via: {}", targetUrl);
                    return response.body();
                }

                lastStatusCode = statusCode;
                String detailedError = "";
                try {
                    JsonNode errNode = objectMapper.readTree(response.body());
                    detailedError = errNode.path("error").path("message").asText("");
                } catch (Exception ignored) {}

                lastErrorMessage = !detailedError.isBlank() ? detailedError : ("HTTP status " + statusCode);
                log.warn("Gemini endpoint '{}' returned HTTP {} ({}). Cascading to next fallback model...",
                        targetUrl, statusCode, lastErrorMessage);

                // 1-second jitter before next model
                if (i < candidateUrls.size() - 1) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

            } catch (Exception e) {
                lastErrorMessage = e.getMessage();
                log.warn("Network error with endpoint '{}': {}. Cascading to next fallback model...",
                        targetUrl, e.getMessage());
            }
        }

        log.error("All Gemini candidate models exhausted without success. Last error: {}", lastErrorMessage);
        throw new RuntimeException("Gemini API Error (" + lastStatusCode + "): " + lastErrorMessage);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Extract JSON Array from Gemini Response Envelope
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Navigates the Gemini response JSON envelope to extract the raw text that
     * the model generated, then strips any accidental markdown code fences.
     *
     * <p>Gemini response structure:
     * <pre>
     * {
     *   "candidates": [{
     *     "content": {
     *       "parts": [{ "text": "[...JSON array here...]" }]
     *     }
     *   }]
     * }
     * </pre>
     *
     * @param rawResponse the full JSON string from the Gemini API
     * @return the clean JSON array string ready for Jackson deserialization
     */
    private String extractJsonArrayFromResponse(String rawResponse) {
        try {
            JsonNode root       = objectMapper.readTree(rawResponse);
            JsonNode candidates = root.path("candidates");

            if (candidates.isMissingNode() || candidates.isEmpty()) {
                // Check for promptFeedback with BLOCK_REASON (content filtered)
                JsonNode feedback = root.path("promptFeedback");
                if (!feedback.isMissingNode()) {
                    String blockReason = feedback.path("blockReason").asText("UNKNOWN");
                    throw new RuntimeException(
                            "Gemini API blocked the prompt. Block reason: " + blockReason +
                            ". Try rephrasing the instructions.");
                }
                throw new RuntimeException(
                        "Gemini API response contains no candidates. Full response:\n" + rawResponse);
            }

            // Navigate: candidates[0] → content → parts[0] → text
            String generatedText = candidates
                    .get(0)
                    .path("content")
                    .path("parts")
                    .get(0)
                    .path("text")
                    .asText();

            if (generatedText.isBlank()) {
                throw new RuntimeException("Gemini returned an empty text response.");
            }

            // Strip accidental markdown code fences & isolate the JSON array
            String cleaned = extractArraySubstring(generatedText);

            log.info("Cleaned JSON from Gemini ({} chars)", cleaned.length());
            return cleaned;

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Gemini API response envelope: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts pure JSON text between the first '[' and last ']' and strips
     * any markdown fences or conversational preambles/postambles.
     */
    private String extractArraySubstring(String raw) {
        if (raw == null || raw.isBlank()) return "[]";
        String trimmed = raw.strip();

        // Strip markdown backticks if present
        if (trimmed.contains("```")) {
            trimmed = trimmed.replaceAll("(?s)```[a-zA-Z]*", "").replace("```", "").strip();
        }

        // Locate outermost JSON array boundaries [ ... ]
        int firstBracket = trimmed.indexOf('[');
        int lastBracket  = trimmed.lastIndexOf(']');

        if (firstBracket != -1 && lastBracket != -1 && lastBracket > firstBracket) {
            return trimmed.substring(firstBracket, lastBracket + 1).strip();
        }

        // Fallback to outermost object boundaries { ... } if returned as an object
        int firstBrace = trimmed.indexOf('{');
        int lastBrace  = trimmed.lastIndexOf('}');
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1).strip();
        }

        return trimmed;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Parse JSON Array → List<TimetableSlot>
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Deserializes the cleaned JSON string into a typed list of {@link TimetableSlot} objects.
     * Supports both raw JSON arrays [ ... ] and wrapper objects like {"schedule": [ ... ]}.
     *
     * @param jsonContent a valid JSON string
     * @return non-null list of slots
     */
    private List<TimetableSlot> parseSlots(String jsonContent) {
        try {
            // Fast path: direct JSON array deserialization
            if (jsonContent.startsWith("[")) {
                return objectMapper.readValue(
                        jsonContent,
                        new TypeReference<List<TimetableSlot>>() {}
                );
            }

            // Fallback path: parse tree to handle wrapper objects like {"timetable": [...]}
            JsonNode rootNode = objectMapper.readTree(jsonContent);
            if (rootNode.isArray()) {
                return objectMapper.convertValue(rootNode, new TypeReference<List<TimetableSlot>>() {});
            }

            // Search for an array property inside the root object
            var fields = rootNode.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                if (entry.getValue().isArray()) {
                    return objectMapper.convertValue(entry.getValue(), new TypeReference<List<TimetableSlot>>() {});
                }
            }

            throw new RuntimeException("No timetable slots array found in the Gemini response.");

        } catch (Exception e) {
            log.error("Jackson failed to parse the following JSON as List<TimetableSlot>:\n{}", jsonContent);
            throw new RuntimeException(
                    "Failed to deserialize Gemini JSON output into TimetableSlot list: " + e.getMessage(), e);
        }
    }
}
