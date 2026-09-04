package com.patientcase.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Pure unit tests for AiService parsing and disabled-mode behavior.
 * No Spring context — fast, no network calls.
 *
 * Batch 4: The AI now uses a structured per-turn JSON format.
 * Tests updated to use the new TurnResponse format.
 */
class AiServiceUnitTest {

    // ---- Disabled / unconfigured mode ----

    @Test
    void chat_whenDisabled_returnsDisabledResponse() {
        AiService service = makeService(/*enabled=*/false, /*apiKey=*/"somekey");
        AiChatResponse response = service.chat(List.of(), "Hello");

        assertThat(response.isDisabled()).isTrue();
        assertThat(response.getReply()).contains("not configured");
        assertThat(response.getError()).isNull();
    }

    @Test
    void chat_whenApiKeyBlank_returnsDisabledResponse() {
        AiService service = makeService(/*enabled=*/true, /*apiKey=*/"");
        AiChatResponse response = service.chat(List.of(), "Hello");

        assertThat(response.isDisabled()).isTrue();
        assertThat(response.getReply()).contains("not configured");
    }

    @Test
    void chat_whenApiKeyNull_returnsDisabledResponse() {
        AiService service = makeService(/*enabled=*/true, /*apiKey=*/null);
        AiChatResponse response = service.chat(List.of(), "Hello");

        assertThat(response.isDisabled()).isTrue();
    }

    // ---- Structured per-turn JSON parsing (Batch 4 format) ----

    @Test
    void parseReply_validConversationalJson_returnsNextQuestion() {
        String json = """
            {
              "complete": false,
              "nextQuestion": "What is your main symptom?",
              "patientReportedFacts": [],
              "inferredInformation": [],
              "missingInformation": ["chief complaint"],
              "redFlags": [],
              "urgentFlag": false
            }
            """;
        AiService service = makeService(false, "");
        AiChatResponse response = service.testableParseReply(json);

        assertThat(response.isComplete()).isFalse();
        assertThat(response.isDisabled()).isFalse();
        assertThat(response.getReply()).isEqualTo("What is your main symptom?");
        assertThat(response.getStructuredData()).isNull();
        assertThat(response.getError()).isNull();
    }

    @Test
    void parseReply_validCompletionJson_returnsCompleteWithStructuredData() {
        String json = """
            {
              "complete": true,
              "nextQuestion": null,
              "patientReportedFacts": ["Headache for 3 days"],
              "inferredInformation": [],
              "missingInformation": [],
              "redFlags": [],
              "urgentFlag": false,
              "completionMessage": "Here is the structured summary.",
              "draft": {
                "chiefComplaint": "Headache",
                "historyOfPresentIllness": "3 days",
                "relevantHistory": "",
                "symptoms": []
              }
            }
            """;
        AiService service = makeService(false, "");
        AiChatResponse response = service.testableParseReply(json);

        assertThat(response.isComplete()).isTrue();
        assertThat(response.getReply()).contains("structured summary");
        assertThat(response.getStructuredData()).contains("chiefComplaint");
        assertThat(response.getStructuredData()).contains("Headache");
    }

    @Test
    void parseReply_freeTextFallback_treatsAsPlainReply() {
        // AI ignored the structured format instruction — graceful fallback
        AiService service = makeService(false, "");
        AiChatResponse response = service.testableParseReply("What is your main symptom?");

        assertThat(response.isComplete()).isFalse();
        assertThat(response.isDisabled()).isFalse();
        assertThat(response.getReply()).isEqualTo("What is your main symptom?");
        assertThat(response.getStructuredData()).isNull();
    }

    @Test
    void parseReply_jsonFenceWrapped_unwrappedAndParsed() {
        // Model wrapped its response in markdown fences despite instructions
        String wrapped = "```json\n" +
                "{\"complete\":false,\"nextQuestion\":\"How long have you had this?\"," +
                "\"patientReportedFacts\":[],\"inferredInformation\":[]," +
                "\"missingInformation\":[],\"redFlags\":[],\"urgentFlag\":false}\n```";
        AiService service = makeService(false, "");
        AiChatResponse response = service.testableParseReply(wrapped);

        assertThat(response.isComplete()).isFalse();
        assertThat(response.getReply()).isEqualTo("How long have you had this?");
    }

    @Test
    void parseReply_withRedFlags_surfacedOnResponse() {
        String json = """
            {
              "complete": false,
              "nextQuestion": "Are you experiencing any difficulty breathing?",
              "patientReportedFacts": ["Chest pain"],
              "inferredInformation": [],
              "missingInformation": ["respiratory symptoms"],
              "redFlags": ["Patient reports sudden onset chest pain with sweating."],
              "urgentFlag": true
            }
            """;
        AiService service = makeService(false, "");
        AiChatResponse response = service.testableParseReply(json);

        assertThat(response.getRedFlags()).isNotNull().hasSize(1);
        assertThat(response.isUrgentFlag()).isTrue();
        assertThat(response.isComplete()).isFalse();
    }

    @Test
    void parseReply_missingNextQuestion_returnsError() {
        String json = """
            {
              "complete": false,
              "patientReportedFacts": [],
              "redFlags": [],
              "urgentFlag": false
            }
            """;
        AiService service = makeService(false, "");
        AiChatResponse response = service.testableParseReply(json);

        assertThat(response.getError()).isNotBlank();
        assertThat(response.isComplete()).isFalse();
    }

    @Test
    void parseReply_invalidJson_treatsAsPlainReply() {
        AiService service = makeService(false, "");
        AiChatResponse response = service.testableParseReply("{ not valid json here }");

        // Graceful fallback — not a crash
        assertThat(response.getError()).isNull();
        assertThat(response.getReply()).isNotBlank();
    }

    @Test
    void parseReply_emptyReply_returnsError() {
        AiService service = makeService(false, "");
        AiChatResponse response = service.testableParseReply("");

        assertThat(response.getError()).isNotBlank();
        assertThat(response.isComplete()).isFalse();
    }

    @Test
    void parseReply_nullReply_returnsError() {
        AiService service = makeService(false, "");
        AiChatResponse response = service.testableParseReply(null);

        assertThat(response.getError()).isNotBlank();
    }

    @Test
    void parseReply_completionWithNoCompletionMessage_usesDefault() {
        String json = """
            {
              "complete": true,
              "nextQuestion": null,
              "patientReportedFacts": [],
              "inferredInformation": [],
              "missingInformation": [],
              "redFlags": [],
              "urgentFlag": false,
              "draft": {
                "chiefComplaint": "Cough",
                "historyOfPresentIllness": "",
                "relevantHistory": "",
                "symptoms": []
              }
            }
            """;
        AiService service = makeService(false, "");
        AiChatResponse response = service.testableParseReply(json);

        assertThat(response.isComplete()).isTrue();
        assertThat(response.getReply()).isNotBlank();
        assertThat(response.getStructuredData()).contains("Cough");
    }

    // ---- Helper ----

    private AiService makeService(boolean enabled, String apiKey) {
        com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
        org.springframework.boot.web.client.RestTemplateBuilder builder =
                new org.springframework.boot.web.client.RestTemplateBuilder();
        AiService service = new AiService(builder, mapper);
        setField(service, "enabled", enabled);
        setField(service, "apiKey", apiKey);
        setField(service, "model", "gpt-4o-mini");
        setField(service, "apiUrl", "http://localhost/fake");
        return service;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Could not set field " + fieldName, e);
        }
    }
}
