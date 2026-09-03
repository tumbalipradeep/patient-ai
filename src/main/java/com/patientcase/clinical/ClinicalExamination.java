package com.patientcase.clinical;

import com.patientcase.encounter.Encounter;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "clinical_examinations")
public class ClinicalExamination {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "encounter_id", nullable = false)
    private Encounter encounter;

    @Column(name = "examination_area", nullable = false, length = 255)
    private String examinationArea;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String findings;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public ClinicalExamination() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Encounter getEncounter() { return encounter; }
    public void setEncounter(Encounter encounter) { this.encounter = encounter; }

    public String getExaminationArea() { return examinationArea; }
    public void setExaminationArea(String examinationArea) { this.examinationArea = examinationArea; }

    public String getFindings() { return findings; }
    public void setFindings(String findings) { this.findings = findings; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
