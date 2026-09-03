package com.patientcase.clinical;

import com.patientcase.encounter.Encounter;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "symptoms")
public class Symptom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "encounter_id", nullable = false)
    private Encounter encounter;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 100)
    private String duration;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Severity severity = Severity.MILD;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Onset onset = Onset.UNKNOWN;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Symptom() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Encounter getEncounter() { return encounter; }
    public void setEncounter(Encounter encounter) { this.encounter = encounter; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }

    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }

    public Onset getOnset() { return onset; }
    public void setOnset(Onset onset) { this.onset = onset; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
