package com.patientcase.kiosk;

import com.patientcase.consent.Consent;
import com.patientcase.patient.Patient;
import com.patientcase.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A patient-driven, self-service clinical intake submission via the MediKiosk.
 *
 * The patient answers an adaptive AI conversation, reviews a structured summary,
 * and submits for clinician review. A clinician then accepts (creating the
 * linked PatientCase/Encounter through the existing clinical workflow) or
 * rejects the intake.
 *
 * Security model (mirrors ai_intake_sessions):
 *   - messagesJson holds conversation turns ({role, content} list).
 *   - draftJson holds a validated AiDraftDto (patient-reported fields only).
 *   - No patient conversation content is ever logged.
 *   - Red flags are persisted separately for triage visibility.
 */
@Entity
@Table(name = "kiosk_intakes")
public class KioskIntake {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /** Language the patient selected at the kiosk (e.g. "en"). */
    @Column(nullable = false, length = 10)
    private String language = "en";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private KioskIntakeStatus status = KioskIntakeStatus.IN_PROGRESS;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consent_id")
    private Consent consent;

    /** JSON array: [{role: "user"|"assistant", content: "..."}] */
    @Column(name = "messages_json", columnDefinition = "TEXT")
    private String messagesJson;

    /** Validated AiDraftDto as JSON (patient-reported fields only). */
    @Column(name = "draft_json", columnDefinition = "TEXT")
    private String draftJson;

    /** Red-flag observations as JSON (persisted here and as red_flags rows). */
    @Column(name = "red_flags_json", columnDefinition = "TEXT")
    private String redFlagsJson;

    /** AyushAssessment as JSON (also persisted as ayush_assessments row). */
    @Column(name = "ayush_json", columnDefinition = "TEXT")
    private String ayushJson;

    /** Physician-ready summary generated from conversation + documents. */
    @Column(name = "summary_json", columnDefinition = "TEXT")
    private String summaryJson;

    /** Username of the clinician who reviewed this intake. */
    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "clinician_notes", columnDefinition = "TEXT")
    private String clinicianNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public KioskIntake() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Patient getPatient() { return patient; }
    public void setPatient(Patient patient) { this.patient = patient; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public KioskIntakeStatus getStatus() { return status; }
    public void setStatus(KioskIntakeStatus status) { this.status = status; }

    public Consent getConsent() { return consent; }
    public void setConsent(Consent consent) { this.consent = consent; }

    public String getMessagesJson() { return messagesJson; }
    public void setMessagesJson(String messagesJson) { this.messagesJson = messagesJson; }

    public String getDraftJson() { return draftJson; }
    public void setDraftJson(String draftJson) { this.draftJson = draftJson; }

    public String getRedFlagsJson() { return redFlagsJson; }
    public void setRedFlagsJson(String redFlagsJson) { this.redFlagsJson = redFlagsJson; }

    public String getAyushJson() { return ayushJson; }
    public void setAyushJson(String ayushJson) { this.ayushJson = ayushJson; }

    public String getSummaryJson() { return summaryJson; }
    public void setSummaryJson(String summaryJson) { this.summaryJson = summaryJson; }

    public boolean isConsentRequired() {
        return consent == null;
    }

    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }

    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }

    public String getClinicianNotes() { return clinicianNotes; }
    public void setClinicianNotes(String clinicianNotes) { this.clinicianNotes = clinicianNotes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}