package com.patientcase.kiosk;

import com.patientcase.encounter.Encounter;
import com.patientcase.patient.Patient;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A patient-safety observation used to flag an intake/encounter for triage
 * awareness. It is NOT a diagnosis and never contains diagnostic language.
 *
 * Examples (patient-reported only):
 *   - "Patient reports sudden severe headache described as worst of life"
 *   - "Patient reports difficulty breathing at rest"
 *
 * Red flags surface in the clinician review and mark the intake as priority.
 */
@Entity
@Table(name = "red_flags")
public class RedFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "intake_id")
    private KioskIntake intake;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "encounter_id")
    private Encounter encounter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @Column(nullable = false, length = 500)
    private String description;

    /** Signals potentially urgent attention is required (still not a diagnosis). */
    @Column(nullable = false)
    private boolean urgent = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RedFlagSource source = RedFlagSource.AI_INTELLIGENCE;

    @Column(nullable = false)
    private boolean resolved = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public RedFlag() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public KioskIntake getIntake() { return intake; }
    public void setIntake(KioskIntake intake) { this.intake = intake; }

    public Encounter getEncounter() { return encounter; }
    public void setEncounter(Encounter encounter) { this.encounter = encounter; }

    public Patient getPatient() { return patient; }
    public void setPatient(Patient patient) { this.patient = patient; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isUrgent() { return urgent; }
    public void setUrgent(boolean urgent) { this.urgent = urgent; }

    public RedFlagSource getSource() { return source; }
    public void setSource(RedFlagSource source) { this.source = source; }

    public boolean isResolved() { return resolved; }
    public void setResolved(boolean resolved) { this.resolved = resolved; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}