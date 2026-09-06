package com.patientcase.kiosk;

import com.patientcase.encounter.Encounter;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Structured Dashavidha Pariksha (ten-fold examination) capture plus
 * Ahara-Vihara (diet and regimen) details, integrated into the intake and
 * encounter workflow. This is patient/observer-reported lifestyle and
 * constitution information for clinician review — not a diagnosis.
 */
@Entity
@Table(name = "ayush_assessments")
public class AyushAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Intake this assessment belongs to (patient kiosk flow); nullable. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "intake_id")
    private KioskIntake intake;

    /** Encounter this assessment belongs to (clinician flow); nullable. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "encounter_id")
    private Encounter encounter;

    // ---- Dashavidha Pariksha ----
    private String prakriti;
    private String vikriti;
    private String sara;
    private String samhanana;
    private String pramana;
    private String satmya;
    private String satva;
    private String aharaShakti;
    private String vyayamaShakti;
    private String vaya;

    // ---- Ahara-Vihara (diet & lifestyle) ----
    @Column(name = "ahara_details", columnDefinition = "TEXT")
    private String aharaDetails;

    @Column(name = "vihara_details", columnDefinition = "TEXT")
    private String viharaDetails;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public AyushAssessment() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public KioskIntake getIntake() { return intake; }
    public void setIntake(KioskIntake intake) { this.intake = intake; }

    public Encounter getEncounter() { return encounter; }
    public void setEncounter(Encounter encounter) { this.encounter = encounter; }

    public String getPrakriti() { return prakriti; }
    public void setPrakriti(String prakriti) { this.prakriti = prakriti; }

    public String getVikriti() { return vikriti; }
    public void setVikriti(String vikriti) { this.vikriti = vikriti; }

    public String getSara() { return sara; }
    public void setSara(String sara) { this.sara = sara; }

    public String getSamhanana() { return samhanana; }
    public void setSamhanana(String samhanana) { this.samhanana = samhanana; }

    public String getPramana() { return pramana; }
    public void setPramana(String pramana) { this.pramana = pramana; }

    public String getSatmya() { return satmya; }
    public void setSatmya(String satmya) { this.satmya = satmya; }

    public String getSatva() { return satva; }
    public void setSatva(String satva) { this.satva = satva; }

    public String getAharaShakti() { return aharaShakti; }
    public void setAharaShakti(String aharaShakti) { this.aharaShakti = aharaShakti; }

    public String getVyayamaShakti() { return vyayamaShakti; }
    public void setVyayamaShakti(String vyayamaShakti) { this.vyayamaShakti = vyayamaShakti; }

    public String getVaya() { return vaya; }
    public void setVaya(String vaya) { this.vaya = vaya; }

    public String getAharaDetails() { return aharaDetails; }
    public void setAharaDetails(String aharaDetails) { this.aharaDetails = aharaDetails; }

    public String getViharaDetails() { return viharaDetails; }
    public void setViharaDetails(String viharaDetails) { this.viharaDetails = viharaDetails; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}