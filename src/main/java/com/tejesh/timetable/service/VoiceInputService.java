package com.tejesh.timetable.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Base64;

/**
 * <h2>VoiceInputService – Microphone Recording & AI Transcription</h2>
 *
 * <p>This service has two independent capabilities:
 *
 * <h3>1. Voice Recording ({@link #recordVoiceClip})</h3>
 * <p>Uses the Java Sound API ({@code javax.sound.sampled}) to capture audio
 * from the default system microphone and saves it as a standard WAV file.
 * The recording format matches what Gemini expects for audio transcription:
 * 16 kHz sample rate, 16-bit depth, mono channel.
 *
 * <h3>2. AI Transcription ({@link #transcribeAudioWithGemini})</h3>
 * <p>Reads the WAV file, encodes it to Base64, and submits it to the Gemini
 * multimodal API as {@code inline_data} with {@code mime_type: "audio/wav"}.
 * Gemini returns the spoken words as plain text, which is then appended to
 * the student's text instructions and forwarded to the AI Brain.
 *
 * <h3>Error Handling Policy</h3>
 * <ul>
 *   <li>Microphone unavailable → logs a warning, returns {@code null}.</li>
 *   <li>Transcription failure  → logs the error, returns empty string {@code ""}
 *       so the timetable generation continues without voice input.</li>
 * </ul>
 */
@Service
public class VoiceInputService {

    private static final Logger log = LoggerFactory.getLogger(VoiceInputService.class);

    // ──────────────────────────────────────────────────────────────────────────
    // Audio format constants (must match Gemini audio expectations)
    // ──────────────────────────────────────────────────────────────────────────

    /** Sample rate in Hz — 16 kHz is standard for speech recognition. */
    private static final float SAMPLE_RATE     = 16_000f;

    /** Bit depth per sample — 16-bit signed integers. */
    private static final int   SAMPLE_SIZE_BITS = 16;

    /** Mono channel — sufficient for voice capture, halves file size. */
    private static final int   CHANNELS         = 1;

    /** Signed PCM samples — standard for WAV audio. */
    private static final boolean SIGNED          = true;

    /** Little-endian byte order — matches WAV format specification. */
    private static final boolean BIG_ENDIAN      = false;

    /** Output file name for the captured voice clip. */
    private static final String VOICE_FILE_NAME  = "user_voice_input.wav";

    // ──────────────────────────────────────────────────────────────────────────
    // Injected dependencies
    // ──────────────────────────────────────────────────────────────────────────

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    private final ObjectMapper objectMapper;
    private final HttpClient   httpClient;

    public VoiceInputService(ObjectMapper objectMapper, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.httpClient   = httpClient;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Records audio from the system's default microphone for the specified
     * number of seconds and saves it as a WAV file.
     *
     * <p>The recording blocks the calling thread for {@code durationInSeconds}.
     * In Phase 4, the JavaFX UI will call this on a background thread to keep
     * the UI responsive.
     *
     * @param durationInSeconds how many seconds of audio to capture (e.g., 10)
     * @return the saved {@link File} pointing to {@code user_voice_input.wav},
     *         or {@code null} if the microphone is unavailable
     */
    public File recordVoiceClip(int durationInSeconds) {
        log.info("VoiceInputService: Starting microphone capture for {} second(s)...", durationInSeconds);

        AudioFormat audioFormat = buildAudioFormat();
        DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, audioFormat);

        // ── Check microphone availability ─────────────────────────────────────
        if (!AudioSystem.isLineSupported(dataLineInfo)) {
            log.warn("VoiceInputService: Microphone (TargetDataLine) is not supported on this system.");
            return null;
        }

        TargetDataLine microphone = null;
        try {
            microphone = (TargetDataLine) AudioSystem.getLine(dataLineInfo);
            microphone.open(audioFormat);
            microphone.start();

            log.info("VoiceInputService: 🎤 Recording... (speak now for {} seconds)", durationInSeconds);

            // Calculate total bytes to capture: sampleRate × bytesPerSample × channels × seconds
            int bytesPerSecond = (int)(SAMPLE_RATE * (SAMPLE_SIZE_BITS / 8) * CHANNELS);
            int totalBytes     = bytesPerSecond * durationInSeconds;
            byte[] audioBuffer = new byte[totalBytes];

            int bytesRead = 0;
            while (bytesRead < totalBytes) {
                int chunk = microphone.read(audioBuffer, bytesRead, totalBytes - bytesRead);
                if (chunk < 0) break; // EOF / line closed
                bytesRead += chunk;
            }

            microphone.stop();
            log.info("VoiceInputService: Recording complete. Captured {} bytes.", bytesRead);

            // ── Save to WAV file ──────────────────────────────────────────────
            File outputFile = new File(VOICE_FILE_NAME);
            byte[] actualAudio = (bytesRead == totalBytes) ? audioBuffer
                    : java.util.Arrays.copyOf(audioBuffer, bytesRead);

            try (ByteArrayInputStream bais   = new ByteArrayInputStream(actualAudio);
                 AudioInputStream     stream = new AudioInputStream(
                         bais, audioFormat, actualAudio.length / audioFormat.getFrameSize())) {
                AudioSystem.write(stream, AudioFileFormat.Type.WAVE, outputFile);
            }

            log.info("VoiceInputService: WAV file saved → '{}'  ({} bytes)",
                    outputFile.getAbsolutePath(), outputFile.length());
            return outputFile;

        } catch (LineUnavailableException e) {
            log.error("VoiceInputService: Could not open microphone line: {}", e.getMessage(), e);
            return null;
        } catch (IOException e) {
            log.error("VoiceInputService: Failed to write WAV file: {}", e.getMessage(), e);
            return null;
        } finally {
            if (microphone != null && microphone.isOpen()) {
                microphone.close();
            }
        }
    }

    /**
     * Transcribes a WAV audio file using the Gemini multimodal API.
     *
     * <p>The file is Base64-encoded and sent as {@code inline_data} in a
     * multimodal Gemini request. Gemini returns the spoken text which is then
     * usable as the student's extra instructions.
     *
     * @param wavFile the WAV file to transcribe (should be ≤ ~10 MB)
     * @return the transcribed plain-text string; empty string {@code ""} on failure
     */
    /**
     * Transcribes an audio file (WAV, MP3, WEBM, OGG, M4A) using the Gemini multimodal API.
     *
     * @param audioFile the audio file to transcribe (should be ≤ ~10 MB)
     * @return the transcribed plain-text string; empty string {@code ""} on failure
     */
    public String transcribeAudioWithGemini(File audioFile) {
        String mimeType = determineMimeType(audioFile);
        return transcribeAudioWithGemini(audioFile, mimeType);
    }

    /**
     * Transcribes an audio file with an explicitly provided MIME type.
     *
     * @param audioFile the audio file to transcribe
     * @param mimeType  the MIME type (e.g. "audio/wav", "audio/mp3", "audio/webm")
     * @return the transcribed plain-text string; empty string {@code ""} on failure
     */
    public String transcribeAudioWithGemini(File audioFile, String mimeType) {
        if (audioFile == null || !audioFile.exists()) {
            log.warn("VoiceInputService: transcribeAudioWithGemini called with null/missing file.");
            return "";
        }

        String actualMime = (mimeType != null && !mimeType.isBlank()) ? mimeType.trim() : determineMimeType(audioFile);
        log.info("VoiceInputService: Transcribing '{}' ({} bytes, MIME: {}) via Gemini multimodal API...",
                audioFile.getName(), audioFile.length(), actualMime);

        try {
            // Step 1: Read audio bytes and Base64-encode
            byte[] audioBytes    = Files.readAllBytes(audioFile.toPath());
            String base64Audio   = Base64.getEncoder().encodeToString(audioBytes);

            // Step 2: Build the multimodal Gemini request body
            String requestBody   = buildTranscriptionRequestBody(base64Audio, actualMime);

            // Step 3: Call Gemini API with automatic model fallback
            java.util.List<String> candidateUrls = new java.util.ArrayList<>();
            if (geminiApiUrl != null && !geminiApiUrl.isBlank()) candidateUrls.add(geminiApiUrl.trim());
            candidateUrls.add("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent");
            candidateUrls.add("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent");
            candidateUrls.add("https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent");

            for (String targetUrl : candidateUrls) {
                try {
                    String urlWithKey = targetUrl + "?key=" + geminiApiKey;
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(urlWithKey))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                            .timeout(Duration.ofSeconds(60))
                            .build();

                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() == 200) {
                        String transcription = extractTextFromGeminiResponse(response.body());
                        log.info("VoiceInputService: Transcription result via {} → \"{}\"", targetUrl, transcription);
                        return transcription;
                    }

                    log.warn("VoiceInputService: {} returned HTTP {}. Trying next model...", targetUrl, response.statusCode());
                } catch (Exception ex) {
                    log.warn("VoiceInputService: Failed with {}: {}", targetUrl, ex.getMessage());
                }
            }

            return "";

        } catch (Exception e) {
            log.error("VoiceInputService: Transcription failed: {}", e.getMessage(), e);
            return "";
        }
    }

    private String determineMimeType(File file) {
        if (file == null) return "audio/wav";
        String name = file.getName().toLowerCase();
        if (name.endsWith(".mp3")) return "audio/mp3";
        if (name.endsWith(".webm")) return "audio/webm";
        if (name.endsWith(".ogg")) return "audio/ogg";
        if (name.endsWith(".m4a") || name.endsWith(".aac")) return "audio/aac";
        return "audio/wav";
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Creates the {@link AudioFormat} used for both recording and WAV-file writing.
     * Centralised here to guarantee consistency between the two operations.
     */
    private AudioFormat buildAudioFormat() {
        return new AudioFormat(
                SAMPLE_RATE,
                SAMPLE_SIZE_BITS,
                CHANNELS,
                SIGNED,
                BIG_ENDIAN   // false → little-endian (WAV standard)
        );
    }

    /**
     * Builds the Gemini multimodal request JSON body for audio transcription.
     */
    private String buildTranscriptionRequestBody(String base64Audio, String mimeType) throws Exception {
        var root      = objectMapper.createObjectNode();
        var contents  = objectMapper.createArrayNode();
        var content   = objectMapper.createObjectNode();
        var parts     = objectMapper.createArrayNode();

        // Part 1: Transcription instruction
        var textPart  = objectMapper.createObjectNode();
        textPart.put("text",
                "You are an accurate speech-to-text transcription service. " +
                "Listen to this audio recording of a student describing their study preferences " +
                "and constraints. Transcribe EXACTLY what is said into plain text. " +
                "Do not add any explanations, summaries, or commentary — return only the " +
                "verbatim transcription.");
        parts.add(textPart);

        // Part 2: Inline audio data
        var audioPart       = objectMapper.createObjectNode();
        var inlineData      = objectMapper.createObjectNode();
        inlineData.put("mime_type", (mimeType != null && !mimeType.isBlank()) ? mimeType : "audio/wav");
        inlineData.put("data", base64Audio);
        audioPart.set("inline_data", inlineData);
        parts.add(audioPart);

        content.set("parts", parts);
        contents.add(content);
        root.set("contents", contents);

        // Generation config: deterministic + short output (it's just transcription)
        var genConfig = objectMapper.createObjectNode();
        genConfig.put("temperature", 0.0);
        genConfig.put("maxOutputTokens", 1024);
        root.set("generationConfig", genConfig);

        return objectMapper.writeValueAsString(root);
    }

    /**
     * Navigates the Gemini response envelope and returns the raw text content.
     *
     * @param rawResponse full JSON string from Gemini API
     * @return the transcribed text, or empty string on parse failure
     */
    private String extractTextFromGeminiResponse(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            return root.path("candidates")
                       .get(0)
                       .path("content")
                       .path("parts")
                       .get(0)
                       .path("text")
                       .asText("")
                       .strip();
        } catch (Exception e) {
            log.error("VoiceInputService: Failed to parse Gemini transcription response: {}", e.getMessage());
            return "";
        }
    }
}
