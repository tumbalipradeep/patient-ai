package com.patientcase.integration.fhir;

import com.patientcase.clinical.Diagnosis;
import com.patientcase.clinical.Symptom;
import com.patientcase.clinical.Treatment;
import com.patientcase.clinical.Vitals;
import com.patientcase.document.Document;
import com.patientcase.encounter.Encounter;
import com.patientcase.patient.Patient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps internal domain entities to FHIR R4 resource projections for the
 * ABDM/HIS integration boundary. Pure mapping logic — no I/O, no secrets.
 *
 * This layer keeps the integration boundary clean: adapters consume these
 * projections without reaching into transaction-managed entities.
 */
@Component
public class FhirExportMapper {

    public FhirResources.Patient toPatient(Patient p) {
        return new FhirResources.Patient(
                String.valueOf(p.getId()),
                p.getPatientNumber(),
                p.getFirstName(),
                p.getLastName(),
                p.getDateOfBirth(),
                p.getGender() != null ? p.getGender().name() : null,
                p.getPhone(),
                p.getEmail(),
                p.getBloodGroup());
    }

    public FhirResources.Encounter toEncounter(Encounter e) {
        return new FhirResources.Encounter(
                String.valueOf(e.getId()),
                e.getPatientCase() != null ? e.getPatientCase().getCaseNumber() : null,
                e.getStatus() != null ? e.getStatus().name() : "DRAFT",
                e.getEncounterDate(),
                e.getClinician() != null ? e.getClinician().getFullName() : null,
                e.getChiefComplaint(),
                e.getHistoryOfPresentIllness(),
                e.getRelevantHistory());
    }

    public List<FhirResources.Condition> toConditions(Encounter e) {
        List<FhirResources.Condition> out = new ArrayList<>();
        if (e.getDiagnoses() == null) return out;
        for (Diagnosis d : e.getDiagnoses()) {
            out.add(new FhirResources.Condition(
                    String.valueOf(d.getId()),
                    null,
                    d.getDiagnosis(),
                    d.getStatus() != null ? d.getStatus().name() : "SUSPECTED",
                    null));
        }
        return out;
    }

    public List<FhirResources.Observation> toObservations(Encounter e) {
        List<FhirResources.Observation> out = new ArrayList<>();
        if (e.getVitals() != null) {
            Vitals v = e.getVitals();
            addObservation(out, v.getTemperature() != null ? v.getTemperature().toPlainString() : null,
                    "temperature", "C", "36.1-37.2");
            addObservation(out, v.getHeartRate() != null ? v.getHeartRate().toString() : null,
                    "heart-rate", "bpm", "60-100");
            addObservation(out, v.getSystolicBp() != null ? v.getSystolicBp().toString() : null,
                    "systolic-bp", "mmHg", "90-120");
            addObservation(out, v.getDiastolicBp() != null ? v.getDiastolicBp().toString() : null,
                    "diastolic-bp", "mmHg", "60-80");
            addObservation(out, v.getOxygenSaturation() != null ? v.getOxygenSaturation().toPlainString() : null,
                    "oxygen-saturation", "%", "95-100");
        }
        if (e.getSymptoms() != null) {
            for (Symptom s : e.getSymptoms()) {
                out.add(new FhirResources.Observation(
                        String.valueOf(s.getId()), "symptom", s.getName(),
                        s.getSeverity() != null ? s.getSeverity().name() : null, null,
                        null, false, null));
            }
        }
        return out;
    }

    private void addObservation(List<FhirResources.Observation> out, String value,
                                String code, String unit, String referenceRange) {
        if (value != null) {
            out.add(new FhirResources.Observation(
                    null, "vital-signs", code, value, unit, referenceRange, false, null));
        }
    }

    public List<FhirResources.Medication> toMedications(Encounter e) {
        List<FhirResources.Medication> out = new ArrayList<>();
        if (e.getTreatments() == null) return out;
        int i = 0;
        for (Treatment t : e.getTreatments()) {
            out.add(new FhirResources.Medication(
                    String.valueOf(t.getId()), t.getTreatment(), null, null,
                    t.getInstructions(), "active"));
            i++;
        }
        return out;
    }

    public FhirResources.DocumentReference toDocumentReference(Document d) {
        return new FhirResources.DocumentReference(
                String.valueOf(d.getId()),
                d.getContentType(),
                d.getOriginalFilename(),
                d.getUploadedAt(),
                d.getDescription());
    }
}