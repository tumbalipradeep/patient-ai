package com.patientcase.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Response returned to the browser for an AI chat turn.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiChatResponse {

    private String reply;

    /** True when the AI has gathered enough information and structuredData is populated. */
    private boolean complete;

    /**
     * Populated only when complete=true.
     * A JSON string (or null) representing the structured clinical extraction.
     */
    private String structuredData;

    /** True if the AI feature is not configured/enabled. */
    private boolean disabled;

    /** A user-facing error message when processing failed. Never contains secrets. */
    private String error;

    /** True when the AI has gathered enough information AND the draft has been persisted server-side. */
    private boolean draftReady;

    /**
     * Patient-safety signals identified from patient-reported information only.
     * These are NOT diagnoses. Each entry is a plain-language observation for clinician awareness.
     * Always requires clinician assessment before any action is taken.
     */
    private List<String> redFlags;

    /**
     * True if the AI detected information that may require urgent clinical attention.
     * Does NOT constitute a medical diagnosis or recommendation.
     */
    private boolean urgentFlag;

    private AiChatResponse() {}

    public static AiChatResponse reply(String reply) {
        AiChatResponse r = new AiChatResponse();
        r.reply = reply;
        return r;
    }

    public static AiChatResponse complete(String reply, String structuredData) {
        AiChatResponse r = new AiChatResponse();
        r.reply = reply;
        r.complete = true;
        r.structuredData = structuredData;
        return r;
    }

    /**
     * Wraps any response to additionally signal that the server-side draft is persisted
     * and ready for clinician review at /encounters/{id}/ai-intake/draft.
     */
    public AiChatResponse withDraftReady() {
        this.draftReady = true;
        return this;
    }

    /** Attach red-flag observations (patient-safety signals only — NOT diagnoses). */
    public AiChatResponse withRedFlags(List<String> flags, boolean urgent) {
        this.redFlags = (flags != null && !flags.isEmpty()) ? flags : null;
        if (urgent) this.urgentFlag = true;
        return this;
    }

    public static AiChatResponse disabled(String message) {
        AiChatResponse r = new AiChatResponse();
        r.reply = message;
        r.disabled = true;
        return r;
    }

    public static AiChatResponse error(String message) {
        AiChatResponse r = new AiChatResponse();
        r.error = message;
        return r;
    }

    public String getReply() { return reply; }
    public boolean isComplete() { return complete; }
    public String getStructuredData() { return structuredData; }
    public boolean isDisabled() { return disabled; }
    public String getError() { return error; }
    public boolean isDraftReady() { return draftReady; }
    public List<String> getRedFlags() { return redFlags; }
    public boolean isUrgentFlag() { return urgentFlag; }
}
