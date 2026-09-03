package com.patientcase.encounter;

import com.patientcase.case_management.PatientCase;
import com.patientcase.clinical.*;
import com.patientcase.document.Document;
import com.patientcase.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "encounters")
public class Encounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private PatientCase patientCase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clinician_id", nullable = false)
    private User clinician;

    @Column(name = "encounter_date", nullable = false)
    private LocalDateTime encounterDate = LocalDateTime.now();

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "chief_complaint", columnDefinition = "TEXT")
    private String chiefComplaint;

    @Column(name = "history_of_present_illness", columnDefinition = "TEXT")
    private String historyOfPresentIllness;

    @Column(name = "relevant_history", columnDefinition = "TEXT")
    private String relevantHistory;

    @Column(name = "assessment_notes", columnDefinition = "TEXT")
    private String assessmentNotes;

    @Column(name = "clinical_impression", columnDefinition = "TEXT")
    private String clinicalImpression;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EncounterStatus status = EncounterStatus.DRAFT;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "encounter", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<Symptom> symptoms = new ArrayList<>();

    @OneToOne(mappedBy = "encounter", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private Vitals vitals;

    @OneToMany(mappedBy = "encounter", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<ClinicalExamination> examinations = new ArrayList<>();

    @OneToMany(mappedBy = "encounter", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<Diagnosis> diagnoses = new ArrayList<>();

    @OneToMany(mappedBy = "encounter", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<Treatment> treatments = new ArrayList<>();

    @OneToMany(mappedBy = "encounter", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<FollowUp> followUps = new ArrayList<>();

    @OneToMany(mappedBy = "encounter", fetch = FetchType.LAZY)
    private List<Document> documents = new ArrayList<>();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Encounter() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public PatientCase getPatientCase() { return patientCase; }
    public void setPatientCase(PatientCase patientCase) { this.patientCase = patientCase; }

    public User getClinician() { return clinician; }
    public void setClinician(User clinician) { this.clinician = clinician; }

    public LocalDateTime getEncounterDate() { return encounterDate; }
    public void setEncounterDate(LocalDateTime encounterDate) { this.encounterDate = encounterDate; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getChiefComplaint() { return chiefComplaint; }
    public void setChiefComplaint(String chiefComplaint) { this.chiefComplaint = chiefComplaint; }

    public String getHistoryOfPresentIllness() { return historyOfPresentIllness; }
    public void setHistoryOfPresentIllness(String historyOfPresentIllness) { this.historyOfPresentIllness = historyOfPresentIllness; }

    public String getRelevantHistory() { return relevantHistory; }
    public void setRelevantHistory(String relevantHistory) { this.relevantHistory = relevantHistory; }

    public String getAssessmentNotes() { return assessmentNotes; }
    public void setAssessmentNotes(String assessmentNotes) { this.assessmentNotes = assessmentNotes; }

    public String getClinicalImpression() { return clinicalImpression; }
    public void setClinicalImpression(String clinicalImpression) { this.clinicalImpression = clinicalImpression; }

    public EncounterStatus getStatus() { return status; }
    public void setStatus(EncounterStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<Symptom> getSymptoms() { return symptoms; }
    public void setSymptoms(List<Symptom> symptoms) { this.symptoms = symptoms; }

    public Vitals getVitals() { return vitals; }
    public void setVitals(Vitals vitals) { this.vitals = vitals; }

    public List<ClinicalExamination> getExaminations() { return examinations; }
    public void setExaminations(List<ClinicalExamination> examinations) { this.examinations = examinations; }

    public List<Diagnosis> getDiagnoses() { return diagnoses; }
    public void setDiagnoses(List<Diagnosis> diagnoses) { this.diagnoses = diagnoses; }

    public List<Treatment> getTreatments() { return treatments; }
    public void setTreatments(List<Treatment> treatments) { this.treatments = treatments; }

    public List<FollowUp> getFollowUps() { return followUps; }
    public void setFollowUps(List<FollowUp> followUps) { this.followUps = followUps; }

    public List<Document> getDocuments() { return documents; }
    public void setDocuments(List<Document> documents) { this.documents = documents; }
}
