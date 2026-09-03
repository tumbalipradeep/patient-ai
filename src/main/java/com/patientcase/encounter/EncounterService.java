package com.patientcase.encounter;

import com.patientcase.audit.AuditAction;
import com.patientcase.audit.AuditService;
import com.patientcase.case_management.PatientCase;
import com.patientcase.case_management.PatientCaseRepository;
import com.patientcase.case_management.CaseStatus;
import com.patientcase.clinical.*;
import com.patientcase.common.ResourceNotFoundException;
import com.patientcase.user.User;
import com.patientcase.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class EncounterService {

    private final EncounterRepository encounterRepository;
    private final PatientCaseRepository caseRepository;
    private final UserRepository userRepository;
    private final SymptomRepository symptomRepository;
    private final VitalsRepository vitalsRepository;
    private final ClinicalExaminationRepository examinationRepository;
    private final DiagnosisRepository diagnosisRepository;
    private final TreatmentRepository treatmentRepository;
    private final FollowUpRepository followUpRepository;
    private final AuditService auditService;

    public EncounterService(EncounterRepository encounterRepository,
                             PatientCaseRepository caseRepository,
                             UserRepository userRepository,
                             SymptomRepository symptomRepository,
                             VitalsRepository vitalsRepository,
                             ClinicalExaminationRepository examinationRepository,
                             DiagnosisRepository diagnosisRepository,
                             TreatmentRepository treatmentRepository,
                             FollowUpRepository followUpRepository,
                             AuditService auditService) {
        this.encounterRepository = encounterRepository;
        this.caseRepository = caseRepository;
        this.userRepository = userRepository;
        this.symptomRepository = symptomRepository;
        this.vitalsRepository = vitalsRepository;
        this.examinationRepository = examinationRepository;
        this.diagnosisRepository = diagnosisRepository;
        this.treatmentRepository = treatmentRepository;
        this.followUpRepository = followUpRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Encounter findById(Long id) {
        return encounterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Encounter not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Encounter> findByCaseId(Long caseId) {
        return encounterRepository.findByPatientCaseIdOrderByEncounterDateDesc(caseId);
    }

    @Transactional
    public Encounter createEncounter(EncounterCreateRequest request) {
        PatientCase patientCase = caseRepository.findById(request.getCaseId())
                .orElseThrow(() -> new ResourceNotFoundException("Case not found: " + request.getCaseId()));

        User clinician = userRepository.findById(request.getClinicianId())
                .orElseThrow(() -> new ResourceNotFoundException("Clinician not found: " + request.getClinicianId()));

        Encounter encounter = new Encounter();
        encounter.setPatientCase(patientCase);
        encounter.setClinician(clinician);
        encounter.setStatus(EncounterStatus.DRAFT);

        // Update case status to IN_PROGRESS if OPEN
        if (patientCase.getStatus() == CaseStatus.OPEN) {
            patientCase.setStatus(CaseStatus.IN_PROGRESS);
            caseRepository.save(patientCase);
        }

        Encounter saved = encounterRepository.save(encounter);
        auditService.log(AuditAction.ENCOUNTER_CREATED, "Encounter", saved.getId(),
                "Encounter created for case " + patientCase.getCaseNumber());
        return saved;
    }

    @Transactional
    public Encounter saveCaseTaking(Long encounterId, CaseTakingForm form, String currentUsername) {
        Encounter encounter = findById(encounterId);

        // Update encounter fields
        encounter.setChiefComplaint(form.getChiefComplaint());
        encounter.setHistoryOfPresentIllness(form.getHistoryOfPresentIllness());
        encounter.setRelevantHistory(form.getRelevantHistory());
        encounter.setAssessmentNotes(form.getAssessmentNotes());
        encounter.setClinicalImpression(form.getClinicalImpression());

        // Save vitals
        saveVitals(encounter, form);

        // Save symptoms - replace all
        symptomRepository.deleteByEncounterId(encounterId);
        if (form.getSymptoms() != null) {
            for (CaseTakingForm.SymptomForm sf : form.getSymptoms()) {
                if (sf.getName() != null && !sf.getName().isBlank()) {
                    Symptom symptom = new Symptom();
                    symptom.setEncounter(encounter);
                    symptom.setName(sf.getName());
                    symptom.setDuration(sf.getDuration());
                    symptom.setSeverity(sf.getSeverity() != null ? sf.getSeverity() : Severity.MILD);
                    symptom.setOnset(sf.getOnset() != null ? sf.getOnset() : Onset.UNKNOWN);
                    symptom.setNotes(sf.getNotes());
                    symptomRepository.save(symptom);
                }
            }
        }

        // Save examinations - replace all
        examinationRepository.deleteByEncounterId(encounterId);
        if (form.getExaminations() != null) {
            for (CaseTakingForm.ExaminationForm ef : form.getExaminations()) {
                if (ef.getExaminationArea() != null && !ef.getExaminationArea().isBlank()
                        && ef.getFindings() != null && !ef.getFindings().isBlank()) {
                    ClinicalExamination exam = new ClinicalExamination();
                    exam.setEncounter(encounter);
                    exam.setExaminationArea(ef.getExaminationArea());
                    exam.setFindings(ef.getFindings());
                    exam.setNotes(ef.getNotes());
                    examinationRepository.save(exam);
                }
            }
        }

        // Save diagnoses - append (don't overwrite previously saved)
        User clinician = userRepository.findByUsername(currentUsername).orElse(null);
        if (form.getDiagnoses() != null) {
            // Clear existing and re-save
            diagnosisRepository.findByEncounterId(encounterId)
                    .forEach(d -> diagnosisRepository.delete(d));
            for (CaseTakingForm.DiagnosisForm df : form.getDiagnoses()) {
                if (df.getDiagnosis() != null && !df.getDiagnosis().isBlank()) {
                    Diagnosis diagnosis = new Diagnosis();
                    diagnosis.setEncounter(encounter);
                    diagnosis.setDiagnosis(df.getDiagnosis());
                    diagnosis.setNotes(df.getNotes());
                    diagnosis.setStatus(df.getStatus() != null ? df.getStatus() : DiagnosisStatus.SUSPECTED);
                    diagnosis.setCreatedBy(clinician);
                    diagnosisRepository.save(diagnosis);
                    auditService.log(AuditAction.DIAGNOSIS_CREATED, "Diagnosis", diagnosis.getId(), null);
                }
            }
        }

        // Save treatments
        if (form.getTreatments() != null) {
            treatmentRepository.findByEncounterId(encounterId)
                    .forEach(t -> treatmentRepository.delete(t));
            for (CaseTakingForm.TreatmentForm tf : form.getTreatments()) {
                if (tf.getTreatment() != null && !tf.getTreatment().isBlank()) {
                    Treatment treatment = new Treatment();
                    treatment.setEncounter(encounter);
                    treatment.setTreatment(tf.getTreatment());
                    treatment.setInstructions(tf.getInstructions());
                    treatment.setNotes(tf.getNotes());
                    treatment.setCreatedBy(clinician);
                    treatmentRepository.save(treatment);
                    auditService.log(AuditAction.TREATMENT_CREATED, "Treatment", treatment.getId(), null);
                }
            }
        }

        // Save follow-up
        if (form.getFollowUpDate() != null && !form.getFollowUpDate().isBlank()) {
            followUpRepository.findByEncounterId(encounterId)
                    .forEach(f -> followUpRepository.delete(f));
            FollowUp followUp = new FollowUp();
            followUp.setEncounter(encounter);
            followUp.setPatientCase(encounter.getPatientCase());
            try {
                followUp.setFollowUpDate(LocalDate.parse(form.getFollowUpDate()));
            } catch (Exception e) {
                // ignore parse error
            }
            followUp.setInstructions(form.getFollowUpInstructions());
            followUp.setNotes(form.getFollowUpNotes());
            followUp.setStatus(FollowUpStatus.PENDING);
            followUpRepository.save(followUp);
        }

        // Finalize if requested
        if (form.isFinalize()) {
            encounter.setStatus(EncounterStatus.COMPLETED);
        }

        Encounter saved = encounterRepository.save(encounter);
        auditService.log(AuditAction.ENCOUNTER_UPDATED, "Encounter", saved.getId(),
                form.isFinalize() ? "Encounter finalized" : "Encounter draft saved");
        return saved;
    }

    private void saveVitals(Encounter encounter, CaseTakingForm form) {
        Vitals vitals = vitalsRepository.findByEncounterId(encounter.getId()).orElse(new Vitals());
        vitals.setEncounter(encounter);
        vitals.setTemperature(form.getTemperature());
        vitals.setHeartRate(form.getHeartRate());
        vitals.setSystolicBp(form.getSystolicBp());
        vitals.setDiastolicBp(form.getDiastolicBp());
        vitals.setRespiratoryRate(form.getRespiratoryRate());
        vitals.setOxygenSaturation(form.getOxygenSaturation());
        vitals.setHeight(form.getHeight());
        vitals.setWeight(form.getWeight());
        vitals.setNotes(form.getVitalsNotes());
        vitalsRepository.save(vitals);
    }

    @Transactional(readOnly = true)
    public List<Encounter> findRecentEncounters(int limit) {
        return encounterRepository.findRecentEncounters(PageRequest.of(0, limit));
    }
}
