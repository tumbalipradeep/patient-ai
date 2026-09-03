package com.patientcase.encounter;

import com.patientcase.clinical.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class CaseTakingForm {

    private Long encounterId;

    // Chief Complaint & History
    @Size(max = 2000)
    private String chiefComplaint;

    @Size(max = 5000)
    private String historyOfPresentIllness;

    @Size(max = 5000)
    private String relevantHistory;

    // Vitals
    @DecimalMin(value = "30.0", message = "Temperature must be at least 30.0°C")
    @DecimalMax(value = "45.0", message = "Temperature must be at most 45.0°C")
    private BigDecimal temperature;

    @DecimalMin(value = "20", message = "Heart rate must be at least 20")
    @DecimalMax(value = "300", message = "Heart rate must be at most 300")
    private Integer heartRate;

    @DecimalMin(value = "50", message = "Systolic BP must be at least 50")
    @DecimalMax(value = "300", message = "Systolic BP must be at most 300")
    private Integer systolicBp;

    @DecimalMin(value = "20", message = "Diastolic BP must be at least 20")
    @DecimalMax(value = "200", message = "Diastolic BP must be at most 200")
    private Integer diastolicBp;

    @DecimalMin(value = "4", message = "Respiratory rate must be at least 4")
    @DecimalMax(value = "60", message = "Respiratory rate must be at most 60")
    private Integer respiratoryRate;

    @DecimalMin(value = "50.0", message = "Oxygen saturation must be at least 50%")
    @DecimalMax(value = "100.0", message = "Oxygen saturation must be at most 100%")
    private BigDecimal oxygenSaturation;

    @DecimalMin(value = "30.0", message = "Height must be at least 30 cm")
    @DecimalMax(value = "300.0", message = "Height must be at most 300 cm")
    private BigDecimal height;

    @DecimalMin(value = "1.0", message = "Weight must be at least 1 kg")
    @DecimalMax(value = "500.0", message = "Weight must be at most 500 kg")
    private BigDecimal weight;

    private String vitalsNotes;

    // Symptoms
    @Valid
    private List<SymptomForm> symptoms = new ArrayList<>();

    // Examinations
    @Valid
    private List<ExaminationForm> examinations = new ArrayList<>();

    // Assessment
    @Size(max = 5000)
    private String assessmentNotes;

    @Size(max = 5000)
    private String clinicalImpression;

    // Diagnoses
    @Valid
    private List<DiagnosisForm> diagnoses = new ArrayList<>();

    // Treatments
    @Valid
    private List<TreatmentForm> treatments = new ArrayList<>();

    // Follow-up
    private String followUpDate;

    @Size(max = 2000)
    private String followUpInstructions;

    @Size(max = 2000)
    private String followUpNotes;

    // Finalize flag
    private boolean finalize;

    // Inner classes
    public static class SymptomForm {
        @Size(max = 255)
        private String name;
        @Size(max = 100)
        private String duration;
        private Severity severity = Severity.MILD;
        private Onset onset = Onset.UNKNOWN;
        @Size(max = 1000)
        private String notes;

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
    }

    public static class ExaminationForm {
        @Size(max = 255)
        private String examinationArea;
        @Size(max = 2000)
        private String findings;
        @Size(max = 1000)
        private String notes;

        public String getExaminationArea() { return examinationArea; }
        public void setExaminationArea(String examinationArea) { this.examinationArea = examinationArea; }
        public String getFindings() { return findings; }
        public void setFindings(String findings) { this.findings = findings; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }

    public static class DiagnosisForm {
        @Size(max = 500)
        private String diagnosis;
        @Size(max = 2000)
        private String notes;
        private DiagnosisStatus status = DiagnosisStatus.SUSPECTED;

        public String getDiagnosis() { return diagnosis; }
        public void setDiagnosis(String diagnosis) { this.diagnosis = diagnosis; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
        public DiagnosisStatus getStatus() { return status; }
        public void setStatus(DiagnosisStatus status) { this.status = status; }
    }

    public static class TreatmentForm {
        @Size(max = 500)
        private String treatment;
        @Size(max = 2000)
        private String instructions;
        @Size(max = 1000)
        private String notes;

        public String getTreatment() { return treatment; }
        public void setTreatment(String treatment) { this.treatment = treatment; }
        public String getInstructions() { return instructions; }
        public void setInstructions(String instructions) { this.instructions = instructions; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }

    // Main Getters/Setters
    public Long getEncounterId() { return encounterId; }
    public void setEncounterId(Long encounterId) { this.encounterId = encounterId; }

    public String getChiefComplaint() { return chiefComplaint; }
    public void setChiefComplaint(String chiefComplaint) { this.chiefComplaint = chiefComplaint; }

    public String getHistoryOfPresentIllness() { return historyOfPresentIllness; }
    public void setHistoryOfPresentIllness(String historyOfPresentIllness) { this.historyOfPresentIllness = historyOfPresentIllness; }

    public String getRelevantHistory() { return relevantHistory; }
    public void setRelevantHistory(String relevantHistory) { this.relevantHistory = relevantHistory; }

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

    public String getVitalsNotes() { return vitalsNotes; }
    public void setVitalsNotes(String vitalsNotes) { this.vitalsNotes = vitalsNotes; }

    public List<SymptomForm> getSymptoms() { return symptoms; }
    public void setSymptoms(List<SymptomForm> symptoms) { this.symptoms = symptoms; }

    public List<ExaminationForm> getExaminations() { return examinations; }
    public void setExaminations(List<ExaminationForm> examinations) { this.examinations = examinations; }

    public String getAssessmentNotes() { return assessmentNotes; }
    public void setAssessmentNotes(String assessmentNotes) { this.assessmentNotes = assessmentNotes; }

    public String getClinicalImpression() { return clinicalImpression; }
    public void setClinicalImpression(String clinicalImpression) { this.clinicalImpression = clinicalImpression; }

    public List<DiagnosisForm> getDiagnoses() { return diagnoses; }
    public void setDiagnoses(List<DiagnosisForm> diagnoses) { this.diagnoses = diagnoses; }

    public List<TreatmentForm> getTreatments() { return treatments; }
    public void setTreatments(List<TreatmentForm> treatments) { this.treatments = treatments; }

    public String getFollowUpDate() { return followUpDate; }
    public void setFollowUpDate(String followUpDate) { this.followUpDate = followUpDate; }

    public String getFollowUpInstructions() { return followUpInstructions; }
    public void setFollowUpInstructions(String followUpInstructions) { this.followUpInstructions = followUpInstructions; }

    public String getFollowUpNotes() { return followUpNotes; }
    public void setFollowUpNotes(String followUpNotes) { this.followUpNotes = followUpNotes; }

    public boolean isFinalize() { return finalize; }
    public void setFinalize(boolean finalize) { this.finalize = finalize; }
}
