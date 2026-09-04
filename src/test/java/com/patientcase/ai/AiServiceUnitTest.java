package com.patientcase.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Pure unit tests for AiService parsing and disabled-mode behavior.
 * No Spring context — fast, no network calls.
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

    // ---- Reply parsing ----

    @Test
    void parseReply_plainTextReply_returnsNormalReply() {
        AiService service = makeService(false, "");
        AiChatResponse response = service.testableParseReply("What is your main symptom?");

        assertThat(response.isComplete()).isFalse();
        assertThat(response.isDisabled()).isFalse();
        assertThat(response.getReply()).isEqualTo("What is your main symptom?");
        assertThat(response.getStructuredData()).isNull();
    }

    @Test
    void parseReply_withValidJsonFence_returnsCompleteWithStructuredData() {
        String rawReply = "Here is the structured summary.\n" +
                "```json\n" +
                "{\"chiefComplaint\":\"Headache\",\"historyOfPresentIllness\":\"3 days\"}\n" +
                "```";

        AiService service = makeService(false, "");
        AiChatResponse response = service.testableParseReply(rawReply);

        assertThat(response.isComplete()).isTrue();
        assertThat(response.getReply()).contains("structured summary");
        assertThat(response.getStructuredData()).contains("chiefComplaint");
        assertThat(response.getStructuredData()).contains("Headache");
    }

    @Test
    void parseReply_withMalformedJsonInFence_treatsAsPlainReply() {
        String rawReply = "Some text\n```json\nnot { valid json\n```";

        AiService service = makeService(false, "");
        AiChatResponse response = service.testableParseReply(rawReply);

        assertThat(response.isComplete()).isFalse();
        assertThat(response.getStructuredData()).isNull();
        assertThat(response.getReply()).isNotBlank();
    }

    @Test
    void parseReply_withUnterminatedFence_treatsAsPlainReply() {
        String rawReply = "Some text\n```json\n{\"key\":\"value\"}";

        AiService service = makeService(false, "");
        AiChatResponse response = service.testableParseReply(rawReply);

        assertThat(response.isComplete()).isFalse();
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
    void parseReply_jsonFenceWithNoHumanText_usesDefaultCompletionMessage() {
        String rawReply = "```json\n{\"chiefComplaint\":\"Cough\"}\n```";

        AiService service = makeService(false, "");
        AiChatResponse response = service.testableParseReply(rawReply);

        assertThat(response.isComplete()).isTrue();
        assertThat(response.getReply()).isNotBlank();
        assertThat(response.getStructuredData()).contains("Cough");
    }

    // ---- Helper: expose the private parse method for unit testing ----

    private AiService makeService(boolean enabled, String apiKey) {
        // Use real ObjectMapper; stub out the RestTemplateBuilder
        com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
        org.springframework.boot.web.client.RestTemplateBuilder builder =
                new org.springframework.boot.web.client.RestTemplateBuilder();
        AiService service = new AiService(builder, mapper);
        // Inject via reflection — avoids @Value needing Spring context
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
