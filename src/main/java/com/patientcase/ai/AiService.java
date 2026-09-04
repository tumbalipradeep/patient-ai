package com.patientcase.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Calls an OpenAI-compatible chat-completions API to power the AI case-taking assistant.
 *
 * Security guarantees:
 * - API key is never logged, never returned to the browser.
 * - Patient messages are not logged (only structural metadata).
 * - Disabled gracefully when AI_ENABLED=false or AI_API_KEY is blank.
 */
@Service
public class AiService implements AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    // ---- Configuration (all via environment variables) ----
    @Value("${app.ai.api-url:https://api.openai.com/v1/chat/completions}")
    private String apiUrl;

    @Value("${app.ai.api-key:}")
    private String apiKey;

    @Value("${app.ai.model:gpt-4o-mini}")
    private String model;

    @Value("${app.ai.enabled:false}")
    private boolean enabled;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // ---- System prompt ----
    private static final String SYSTEM_PROMPT = """
You are an AI-assisted clinical case-taking assistant helping to collect patient-reported information \
for review by a qualified clinician. You are NOT a doctor and must never diagnose, prescribe, or give \
medical advice.

Your role:
- Ask ONE clear, focused question at a time.
- Begin with the patient's chief complaint / presenting problem.
- Progress logically through: onset, duration, location, character, severity (1-10), \
aggravating and relieving factors, associated symptoms, relevant past medical history, \
current medications, known allergies.
- Do NOT invent or assume symptoms, vitals, diagnoses, examinations, or treatments.
- Clearly distinguish what the patient reports from any interpretation.
- Stop when you have gathered sufficient information for a useful preliminary draft.
- If the situation sounds urgent or dangerous, advise the user to seek immediate professional \
medical care and end the conversation.

When you have collected enough information, respond with EXACTLY this JSON block (and nothing else \
on the line containing ```json):

```json
{
  "chiefComplaint": "",
  "historyOfPresentIllness": "",
  "relevantHistory": "",
  "assessmentNotes": "AI-collected patient-reported information — draft only, clinician review required",
  "clinicalImpression": "",
  "symptoms": [
    {"name": "", "duration": "", "severity": "MILD|MODERATE|SEVERE", "onset": "SUDDEN|GRADUAL|UNKNOWN", "notes": ""}
  ],
  "examinations": [],
  "diagnoses": [],
  "treatments": [],
  "followUpInstructions": "",
  "followUpNotes": ""
}
```

Rules for the JSON:
- Leave any field empty ("" or []) if the information was not reported.
- severity must be one of: MILD, MODERATE, SEVERE.
- onset must be one of: SUDDEN, GRADUAL, UNKNOWN.
- examinations, diagnoses, and treatments must remain [] — do not populate these from patient-reported information.
- The entire output is a DRAFT. Never claim certainty.
""";

    /** Marker we look for in the model's reply to detect structured extraction. */
    private static final String JSON_FENCE_OPEN  = "```json";
    private static final String JSON_FENCE_CLOSE = "```";

    public AiService(RestTemplateBuilder restTemplateBuilder, ObjectMapper objectMapper) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * Process one chat turn.
     * Returns an {@link AiChatResponse} — never throws.
     */
    public AiChatResponse chat(List<AiChatRequest.Message> history, String userMessage) {
        if (!enabled || apiKey == null || apiKey.isBlank()) {
            return AiChatResponse.disabled(
                    "AI assistance is not configured. " +
                    "Set AI_ENABLED=true and provide AI_API_KEY to enable it.");
        }

        try {
            String rawReply = callProvider(history, userMessage);
            return parseReply(rawReply);
        } catch (RestClientException e) {
            // Log safe technical info only — no key, no patient data
            log.warn("AI provider request failed: {}", e.getClass().getSimpleName());
            return AiChatResponse.error(
                    "AI service is temporarily unavailable. Please try again or proceed with manual case-taking.");
        } catch (Exception e) {
            log.warn("Unexpected error during AI chat: {}", e.getClass().getSimpleName());
            return AiChatResponse.error(
                    "An unexpected error occurred. Please proceed with manual case-taking.");
        }
    }

    // ---- Private helpers ----

    private String callProvider(List<AiChatRequest.Message> history, String userMessage) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // API key is only ever placed in this header — never in logs, never in responses
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);

        List<Map<String, String>> messages = buildMessages(history, userMessage);

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", messages,
                "max_tokens", 1024,
                "temperature", 0.2   // Lower temperature → more consistent structured output
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<ProviderResponse> response =
                restTemplate.postForEntity(apiUrl, entity, ProviderResponse.class);

        if (response.getStatusCode().is2xxSuccessful()
                && response.getBody() != null
                && response.getBody().choices != null
                && !response.getBody().choices.isEmpty()) {
            return response.getBody().choices.get(0).message.content;
        }

        log.warn("AI provider returned unexpected response status: {}", response.getStatusCode());
        throw new RestClientException("Unexpected provider response");
    }

    private List<Map<String, String>> buildMessages(List<AiChatRequest.Message> history, String userMessage) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));

        // Replay conversation history (capped to last 20 turns to limit token usage)
        int start = Math.max(0, history.size() - 20);
        for (int i = start; i < history.size(); i++) {
            AiChatRequest.Message m = history.get(i);
            String role = "assistant".equals(m.getRole()) ? "assistant" : "user";
            messages.add(Map.of("role", role, "content", m.getContent()));
        }

        if (userMessage != null && !userMessage.isBlank()) {
            messages.add(Map.of("role", "user", "content", userMessage));
        }
        return messages;
    }

    /**
     * Parses the raw LLM reply.
     * If it contains a JSON fence block, extracts and validates the structured data.
     * Falls back to a plain reply if parsing fails.
     */
    private AiChatResponse parseReply(String rawReply) {
        if (rawReply == null || rawReply.isBlank()) {
            return AiChatResponse.error("The AI returned an empty response. Please try again.");
        }

        int jsonStart = rawReply.indexOf(JSON_FENCE_OPEN);
        if (jsonStart == -1) {
            // Normal conversational reply
            return AiChatResponse.reply(rawReply.strip());
        }

        // Extract the JSON block between ``` fences
        int contentStart = rawReply.indexOf('\n', jsonStart) + 1;
        int jsonEnd = rawReply.indexOf(JSON_FENCE_CLOSE, contentStart);

        if (contentStart <= 0 || jsonEnd <= contentStart) {
            // Malformed fence — treat as conversational
            log.debug("AI reply contained malformed JSON fence — treating as plain reply");
            return AiChatResponse.reply(rawReply.strip());
        }

        String jsonCandidate = rawReply.substring(contentStart, jsonEnd).strip();

        try {
            // Validate it parses as a JSON object — don't trust arbitrary structure
            objectMapper.readTree(jsonCandidate);
            // Any human-readable message before the fence becomes the reply
            String humanReply = rawReply.substring(0, jsonStart).strip();
            if (humanReply.isBlank()) {
                humanReply = "I have gathered enough information to prepare a preliminary draft for clinician review.";
            }
            return AiChatResponse.complete(humanReply, jsonCandidate);
        } catch (Exception e) {
            log.debug("AI returned JSON fence but content was not valid JSON — treating as plain reply");
            return AiChatResponse.reply(rawReply.strip());
        }
    }

    // ---- Package-private test hook ----

    /** Exposes parseReply for unit testing without a Spring context. */
    AiChatResponse testableParseReply(String rawReply) {
        return parseReply(rawReply);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ProviderResponse {
        public List<Choice> choices;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Choice {
        public ChoiceMessage message;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ChoiceMessage {
        public String content;
    }
}
