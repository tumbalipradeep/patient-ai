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
 * Batch 4 upgrades:
 * - Every turn now returns a structured JSON object (not free text).
 * - The AI performs adaptive questioning: asks only missing information.
 * - Red-flag detection: patient-safety signals flagged for clinician awareness (NOT diagnoses).
 * - Fact / inference / unknown separation maintained via DraftFieldConfidence.
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
You are an AI-assisted clinical history-taking assistant. You help collect patient-reported \
information for review by a qualified clinician. You are NOT a doctor.

ABSOLUTE PROHIBITIONS — never, under any circumstances:
- Diagnose any condition
- Prescribe or recommend medication or treatment
- Perform or describe clinical examinations
- Invent, assume, or fabricate symptoms, vitals, history, or any clinical information
- Give medical advice
- Claim certainty about any clinical matter

YOUR ROLE — adaptive history-taking:
- Ask EXACTLY ONE focused question per turn.
- Choose the single most important missing piece of information based on what the patient \
has already told you. Do not repeat questions already answered.
- Begin with the chief complaint. Then explore relevant areas such as:
  onset and duration, location, character, severity (0–10), timing and pattern, \
  aggravating and relieving factors, associated symptoms, relevant past medical history, \
  current medications, known allergies — but only when clinically relevant to this complaint.
- Do NOT mechanically go through every category. Be context-sensitive.
- If the patient has already mentioned information, acknowledge it and ask about what is missing.
- Stop collecting when you have sufficient information for a useful preliminary draft.
- If the patient reports something that sounds potentially urgent (e.g. severe chest pain, \
  difficulty breathing, sudden severe headache, signs of serious injury), add it as a \
  red flag AND advise them to seek immediate professional medical care.

RED FLAGS — patient-safety observations only:
- Report only information the patient explicitly stated.
- Red flags are safety SIGNALS for the clinician — they are NOT diagnoses.
- Never use diagnostic language, prescribe, or imply treatment in a red flag.
- Example of allowed red flag: "Patient reports sudden severe headache described as worst of their life."
- Example of PROHIBITED red flag: "Possible subarachnoid haemorrhage — needs CT scan."

RESPONSE FORMAT — you MUST respond with ONLY a JSON object on every turn. No other text. \
No markdown. No explanation outside the JSON. The JSON must be one of:

1. CONVERSATIONAL TURN (still collecting information):
{
  "complete": false,
  "nextQuestion": "The single question to ask the patient next.",
  "patientReportedFacts": ["fact1", "fact2"],
  "inferredInformation": ["inference1"],
  "missingInformation": ["area1", "area2"],
  "redFlags": [],
  "urgentFlag": false
}

2. FINAL TURN (enough information collected):
{
  "complete": true,
  "nextQuestion": null,
  "patientReportedFacts": ["fact1", "fact2"],
  "inferredInformation": [],
  "missingInformation": [],
  "redFlags": [],
  "urgentFlag": false,
  "completionMessage": "Summary sentence shown to the patient.",
  "draft": {
    "chiefComplaint": "",
    "historyOfPresentIllness": "",
    "relevantHistory": "",
    "symptoms": [
      {
        "name": "",
        "duration": "",
        "severity": "MILD|MODERATE|SEVERE|null",
        "onset": "SUDDEN|GRADUAL|UNKNOWN",
        "notes": "",
        "confidence": "PATIENT_REPORTED|AI_INFERRED|MISSING"
      }
    ]
  }
}

STRICT RULES FOR FINAL DRAFT:
- severity must be MILD, MODERATE, or SEVERE — or null if the patient did not state one.
- onset must be SUDDEN, GRADUAL, or UNKNOWN.
- confidence must be PATIENT_REPORTED, AI_INFERRED, or MISSING.
- Leave fields as "" or [] if not reported — never invent information.
- Never include diagnoses, treatments, examinations, or vitals in the draft.
- assessmentNotes and clinicalImpression are PROHIBITED in the draft.
- Entire draft is a preliminary patient-reported summary — clinician must review and verify.
""";

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
    @Override
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
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);

        List<Map<String, String>> messages = buildMessages(history, userMessage);

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", messages,
                "max_tokens", 1024,
                "temperature", 0.2
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
     * Parse the structured JSON turn response from the AI.
     *
     * Expected format: a JSON object with "complete", "nextQuestion", "redFlags",
     * "urgentFlag", and optionally "draft" when complete=true.
     *
     * Falls back gracefully to a plain reply if the response is not valid JSON
     * or does not conform to the expected structure.
     */
    AiChatResponse parseReply(String rawReply) {
        if (rawReply == null || rawReply.isBlank()) {
            return AiChatResponse.error("The AI returned an empty response. Please try again.");
        }

        // Strip markdown fences if the model wrapped the JSON anyway
        String candidate = rawReply.strip();
        if (candidate.startsWith("```json")) {
            int start = candidate.indexOf('\n') + 1;
            int end = candidate.lastIndexOf("```");
            if (start > 0 && end > start) {
                candidate = candidate.substring(start, end).strip();
            }
        } else if (candidate.startsWith("```")) {
            int start = candidate.indexOf('\n') + 1;
            int end = candidate.lastIndexOf("```");
            if (start > 0 && end > start) {
                candidate = candidate.substring(start, end).strip();
            }
        }

        try {
            TurnResponse turn = objectMapper.readValue(candidate, TurnResponse.class);
            return buildResponse(turn);
        } catch (Exception e) {
            // Model did not return valid structured JSON — fall back to plain reply
            log.debug("AI response was not structured JSON ({}), treating as plain reply",
                    e.getClass().getSimpleName());
            return AiChatResponse.reply(rawReply.strip());
        }
    }

    private AiChatResponse buildResponse(TurnResponse turn) {
        if (turn == null) {
            return AiChatResponse.error("AI returned an unrecognised response structure.");
        }

        // Red flags — attach to all responses (present in both conversational and final turns)
        List<String> redFlags = turn.redFlags != null ? turn.redFlags : new ArrayList<>();
        boolean urgent = Boolean.TRUE.equals(turn.urgentFlag);

        if (Boolean.TRUE.equals(turn.complete) && turn.draft != null) {
            // Embed red flags into the draft payload so they persist alongside the draft
            turn.draft.redFlags = redFlags;
            // Final turn: build structured data JSON from the nested draft
            try {
                String draftJson = objectMapper.writeValueAsString(turn.draft);
                String humanMsg = (turn.completionMessage != null && !turn.completionMessage.isBlank())
                        ? turn.completionMessage
                        : "I have gathered enough information to prepare a preliminary draft for clinician review.";
                return AiChatResponse.complete(humanMsg, draftJson)
                        .withRedFlags(redFlags, urgent);
            } catch (Exception e) {
                log.warn("Failed to serialise AI draft: {}", e.getClass().getSimpleName());
                return AiChatResponse.error(
                        "AI response could not be processed. Please proceed with manual case-taking.");
            }
        }

        // Conversational turn
        String question = (turn.nextQuestion != null && !turn.nextQuestion.isBlank())
                ? turn.nextQuestion.strip()
                : null;

        if (question == null) {
            // nextQuestion is required for non-complete turns
            log.debug("AI returned complete=false but nextQuestion is missing");
            return AiChatResponse.error(
                    "AI response was incomplete. Please try again or proceed manually.");
        }

        return AiChatResponse.reply(question).withRedFlags(redFlags, urgent);
    }

    // ---- Package-private test hook ----

    /** Exposes parseReply for unit testing without a Spring context. */
    AiChatResponse testableParseReply(String rawReply) {
        return parseReply(rawReply);
    }

    // ---- Inner DTOs for provider response ----

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

    /**
     * Maps the structured per-turn JSON response from the AI.
     * Jackson ignores unknown fields for forward compatibility.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class TurnResponse {
        public Boolean complete;
        public String nextQuestion;
        public List<String> patientReportedFacts;
        public List<String> inferredInformation;
        public List<String> missingInformation;
        public List<String> redFlags;
        public Boolean urgentFlag;
        public String completionMessage;
        public DraftPayload draft;
    }

    /**
     * The nested draft object only present when complete=true.
     * Maps directly to the fields AiDraftDto accepts — validator enforces safety.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class DraftPayload {
        public String chiefComplaint;
        public String historyOfPresentIllness;
        public String relevantHistory;
        public List<SymptomPayload> symptoms;
        // redFlags are promoted from the TurnResponse level into this payload
        // before serialisation so they persist alongside the draft.
        public List<String> redFlags;
        // Prohibited fields (diagnoses, treatments, examinations, assessmentNotes,
        // clinicalImpression) are NOT declared here — Jackson ignores them via
        // @JsonIgnoreProperties(ignoreUnknown = true), so they simply don't reach
        // the validator as data. The validator enforces this independently.
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SymptomPayload {
        public String name;
        public String duration;
        public String severity;
        public String onset;
        public String notes;
        public String confidence;
    }
}
