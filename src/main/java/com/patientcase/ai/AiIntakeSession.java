package com.patientcase.ai;

import com.patientcase.encounter.Encounter;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Persists a single AI intake session for one encounter.
 *
 * One active session at a time per encounter (enforced by UNIQUE constraint
 * on encounter_id). When a session is DISCARDED a new one can replace it.
 *
 * messagesJson — JSON array of {role, content} turns (conversation history).
 * draftJson    — Validated AiDraftDto serialised as JSON; null until DRAFT_READY.
 *
 * Security: no patient message content or raw AI JSON is ever logged.
 * The draft is stored for clinician review only.
 */
@Entity
@Table(name = "ai_intake_sessions",
       uniqueConstraints = @UniqueConstraint(
           name = "uq_ai_intake_session_encounter",
           columnNames = "encounter_id"))
public class AiIntakeSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "encounter_id", nullable = false)
    private Encounter encounter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiIntakeSessionStatus status = AiIntakeSessionStatus.IN_PROGRESS;

    /** JSON array: [{role: "user"|"assistant", content: "..."}] */
    @Column(name = "messages_json", columnDefinition = "TEXT")
    private String messagesJson;

    /** Validated AiDraftDto as JSON; null until status = DRAFT_READY. */
    @Column(name = "draft_json", columnDefinition = "TEXT")
    private String draftJson;

    /** Username of the clinician who started this session. */
    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public AiIntakeSession() {}

    // ---- Getters / Setters ----

    public Long getId() { return id; }

    public Encounter getEncounter() { return encounter; }
    public void setEncounter(Encounter encounter) { this.encounter = encounter; }

    public AiIntakeSessionStatus getStatus() { return status; }
    public void setStatus(AiIntakeSessionStatus status) { this.status = status; }

    public String getMessagesJson() { return messagesJson; }
    public void setMessagesJson(String messagesJson) { this.messagesJson = messagesJson; }

    public String getDraftJson() { return draftJson; }
    public void setDraftJson(String draftJson) { this.draftJson = draftJson; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
