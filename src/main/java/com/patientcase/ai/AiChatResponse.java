package com.patientcase.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Response returned to the browser for an AI chat turn.
 *
 * The conversational UX fields below are OPTIONAL and backward-compatible:
 * older clients simply ignore them, and they are null whenever the provider
 * did not (or could not) supply them.
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

    /**
     * True when the failure is transient (provider/timeout) and the client
     * should offer a "Retry" action that re-sends the same turn.
     * False for permanent failures (validation, session state) where retrying
     * would not help.
     */
    private boolean retryable;

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

    /**
     * Facts the patient explicitly reported on this turn (or that were already
     * recorded earlier). Used to show the patient what the assistant believes
     * it heard — patient-reported information is kept visibly distinct from
     * anything inferred.
     */
    private List<String> patientReportedFacts;

    /**
     * Information the assistant inferred rather than the patient stating it.
     * Must never be clinical facts: anything here is shown to the patient with
     * an explicit "please verify" treatment and normally stays empty.
     */
    private List<String> inferredInformation;

    /**
     * Patient-friendly quick answers for the current question, e.g.
     * ["1 day", "2–3 days", "More than a week", "Not sure"].
     * When present the UI renders them as tappable chips. Keyboards/touch.
     */
    private List<String> suggestedAnswers;

    /**
     * True when the UI should also offer an "Other" free-text path for the
     * current question. Defaults to true when null.
     */
    private Boolean allowOtherText;

    /**
     * Normalised clinical category the current question belongs to, e.g.
     * "CHIEF_COMPLAINT", "HPI", "PAST_HISTORY". The server maps these to
     * friendly patient-facing labels; unrecognised values map to a generic
     * "Intake" section.
     */
    private String section;

    /**
     * The assistant's estimate of how much of the clinical history has been
     * covered so far, as an integer percentage 0–100 (server-clamped). Used
     * to render the intake progress bar in the conversational view.
     */
    private Integer sectionProgress;

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

    /** Enrich a response with conversational UX metadata (facts, inference, chips, section, progress). */
    public AiChatResponse withConversation(
            List<String> patientReportedFacts,
            List<String> inferredInformation,
            List<String> suggestedAnswers,
            boolean allowOtherText,
            String section,
            Integer sectionProgress) {
        this.patientReportedFacts =
                (patientReportedFacts != null && !patientReportedFacts.isEmpty()) ? patientReportedFacts : null;
        this.inferredInformation =
                (inferredInformation != null && !inferredInformation.isEmpty()) ? inferredInformation : null;
        this.suggestedAnswers =
                (suggestedAnswers != null && !suggestedAnswers.isEmpty()) ? suggestedAnswers : null;
        this.allowOtherText = allowOtherText;
        this.section = section;
        this.sectionProgress = sectionProgress;
        return this;
    }

    /** Mark a response as retryable (transient provider/timeout failure). */
    public AiChatResponse retryable() {
        this.retryable = true;
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
    public boolean isRetryable() { return retryable; }
    public boolean isDraftReady() { return draftReady; }
    public List<String> getRedFlags() { return redFlags; }
    public boolean isUrgentFlag() { return urgentFlag; }
    public List<String> getPatientReportedFacts() { return patientReportedFacts; }
    public List<String> getInferredInformation() { return inferredInformation; }
    public List<String> getSuggestedAnswers() { return suggestedAnswers; }
    public Boolean getAllowOtherText() { return allowOtherText != null ? allowOtherText : Boolean.TRUE; }
    public String getSection() { return section; }
    public Integer getSectionProgress() { return sectionProgress; }
}
