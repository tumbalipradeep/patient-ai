package com.patientcase.clinical;

import com.patientcase.encounter.Encounter;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "vitals")
public class Vitals {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "encounter_id", nullable = false)
    private Encounter encounter;

    @Column(precision = 4, scale = 1)
    private BigDecimal temperature;

    @Column(name = "heart_rate")
    private Integer heartRate;

    @Column(name = "systolic_bp")
    private Integer systolicBp;

    @Column(name = "diastolic_bp")
    private Integer diastolicBp;

    @Column(name = "respiratory_rate")
    private Integer respiratoryRate;

    @Column(name = "oxygen_saturation", precision = 4, scale = 1)
    private BigDecimal oxygenSaturation;

    @Column(precision = 5, scale = 1)
    private BigDecimal height;

    @Column(precision = 5, scale = 1)
    private BigDecimal weight;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt = LocalDateTime.now();

    @Column(columnDefinition = "TEXT")
    private String notes;

    public Vitals() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Encounter getEncounter() { return encounter; }
    public void setEncounter(Encounter encounter) { this.encounter = encounter; }

    public BigDecimal getTemperature() { return temperature; }
    public void setTemperature(BigDecimal temperature) { this.temperature = temperature; }

    public Integer getHeartRate() { return heartRate; }
    public void setHeartRate(Integer heartRate) { this.heartRate = heartRate; }

    public Integer getSystolicBp() { return systolicBp; }
    public void setSystolicBp(Integer systolicBp) { this.systolicBp = systolicBp; }

    public Integer getDiastolicBp() { return diastolicBp; }
    public void setDiastolicBp(Integer diastolicBp) { this.diastolicBp = diastolicBp; }

    public Integer getRespiratoryRate() { return respiratoryRate; }
    public void setRespiratoryRate(Integer respiratoryRate) { this.respiratoryRate = respiratoryRate; }

    public BigDecimal getOxygenSaturation() { return oxygenSaturation; }
    public void setOxygenSaturation(BigDecimal oxygenSaturation) { this.oxygenSaturation = oxygenSaturation; }

    public BigDecimal getHeight() { return height; }
    public void setHeight(BigDecimal height) { this.height = height; }

    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }

    public LocalDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(LocalDateTime recordedAt) { this.recordedAt = recordedAt; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public BigDecimal getBmi() {
        if (height != null && weight != null && height.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal heightInMeters = height.divide(new BigDecimal("100"), 4, java.math.RoundingMode.HALF_UP);
            return weight.divide(heightInMeters.multiply(heightInMeters), 1, java.math.RoundingMode.HALF_UP);
        }
        return null;
    }
}
