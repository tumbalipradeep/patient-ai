package com.patientcase.encounter;

import com.patientcase.case_management.PatientCase;
import com.patientcase.case_management.PatientCaseService;
import com.patientcase.clinical.*;
import com.patientcase.user.UserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

@Controller
public class EncounterController {

    private final EncounterService encounterService;
    private final PatientCaseService caseService;
    private final UserService userService;
    private final SymptomRepository symptomRepository;
    private final VitalsRepository vitalsRepository;
    private final ClinicalExaminationRepository examinationRepository;
    private final DiagnosisRepository diagnosisRepository;
    private final TreatmentRepository treatmentRepository;
    private final FollowUpRepository followUpRepository;

    public EncounterController(EncounterService encounterService,
                                PatientCaseService caseService,
                                UserService userService,
                                SymptomRepository symptomRepository,
                                VitalsRepository vitalsRepository,
                                ClinicalExaminationRepository examinationRepository,
                                DiagnosisRepository diagnosisRepository,
                                TreatmentRepository treatmentRepository,
                                FollowUpRepository followUpRepository) {
        this.encounterService = encounterService;
        this.caseService = caseService;
        this.userService = userService;
        this.symptomRepository = symptomRepository;
        this.vitalsRepository = vitalsRepository;
        this.examinationRepository = examinationRepository;
        this.diagnosisRepository = diagnosisRepository;
        this.treatmentRepository = treatmentRepository;
        this.followUpRepository = followUpRepository;
    }

    @PostMapping("/cases/{caseId}/encounters/new")
    public String createEncounter(@PathVariable Long caseId,
                                   @Valid @ModelAttribute("encounterForm") EncounterCreateRequest request,
                                   BindingResult result,
                                   RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please correct the form errors.");
            return "redirect:/cases/" + caseId;
        }
        Encounter encounter = encounterService.createEncounter(request);
        redirectAttributes.addFlashAttribute("successMessage", "Encounter created. Begin case-taking.");
        return "redirect:/encounters/" + encounter.getId() + "/case-taking";
    }

    @GetMapping("/encounters/{id}")
    public String viewEncounter(@PathVariable Long id, Model model) {
        Encounter encounter = encounterService.findById(id);
        List<Symptom> symptoms = symptomRepository.findByEncounterId(id);
        Vitals vitals = vitalsRepository.findByEncounterId(id).orElse(null);
        List<ClinicalExamination> examinations = examinationRepository.findByEncounterId(id);
        List<Diagnosis> diagnoses = diagnosisRepository.findByEncounterId(id);
        List<Treatment> treatments = treatmentRepository.findByEncounterId(id);
        List<FollowUp> followUps = followUpRepository.findByEncounterId(id);

        model.addAttribute("encounter", encounter);
        model.addAttribute("symptoms", symptoms);
        model.addAttribute("vitals", vitals);
        model.addAttribute("examinations", examinations);
        model.addAttribute("diagnoses", diagnoses);
        model.addAttribute("treatments", treatments);
        model.addAttribute("followUps", followUps);

        return "encounters/view";
    }

    @GetMapping("/encounters/{id}/case-taking")
    public String caseTakingForm(@PathVariable Long id, Model model) {
        Encounter encounter = encounterService.findById(id);

        // Build form from existing data
        CaseTakingForm form = buildCaseTakingForm(encounter, id);
        model.addAttribute("form", form);
        model.addAttribute("encounter", encounter);
        model.addAttribute("severities", Severity.values());
        model.addAttribute("onsets", Onset.values());
        model.addAttribute("diagnosisStatuses", DiagnosisStatus.values());

        return "encounters/case-taking";
    }

    @PostMapping("/encounters/{id}/case-taking")
    public String saveCaseTaking(@PathVariable Long id,
                                  @Valid @ModelAttribute("form") CaseTakingForm form,
                                  BindingResult result,
                                  Authentication authentication,
                                  Model model,
                                  RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            Encounter encounter = encounterService.findById(id);
            model.addAttribute("encounter", encounter);
            model.addAttribute("severities", Severity.values());
            model.addAttribute("onsets", Onset.values());
            model.addAttribute("diagnosisStatuses", DiagnosisStatus.values());
            model.addAttribute("errorMessage", "Please correct the form errors.");
            return "encounters/case-taking";
        }

        form.setEncounterId(id);
        Encounter saved = encounterService.saveCaseTaking(id, form, authentication.getName());

        if (form.isFinalize()) {
            redirectAttributes.addFlashAttribute("successMessage", "Encounter finalized successfully.");
            return "redirect:/encounters/" + id;
        } else {
            redirectAttributes.addFlashAttribute("successMessage", "Draft saved successfully.");
            return "redirect:/encounters/" + id + "/case-taking";
        }
    }

    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    @PostMapping("/encounters/{id}/cancel")
    public String cancelEncounter(@PathVariable Long id,
                                   Authentication authentication,
                                   RedirectAttributes redirectAttributes) {
        Encounter encounter = encounterService.findById(id);
        Long caseId = encounter.getPatientCase().getId();
        try {
            encounterService.cancelEncounter(id);
            redirectAttributes.addFlashAttribute("successMessage", "Encounter cancelled.");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/cases/" + caseId;
    }

    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE')")
    @PostMapping("/encounters/{encounterId}/followups/{followUpId}/status")
    public String updateFollowUpStatus(@PathVariable Long encounterId,
                                        @PathVariable Long followUpId,
                                        @RequestParam String status,
                                        RedirectAttributes redirectAttributes) {
        try {
            com.patientcase.clinical.FollowUpStatus newStatus =
                    com.patientcase.clinical.FollowUpStatus.valueOf(status);
            encounterService.updateFollowUpStatus(followUpId, newStatus);
            redirectAttributes.addFlashAttribute("successMessage", "Follow-up status updated.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Invalid status value.");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/encounters/" + encounterId;
    }

    private CaseTakingForm buildCaseTakingForm(Encounter encounter, Long encounterId) {
        CaseTakingForm form = new CaseTakingForm();
        form.setEncounterId(encounterId);
        form.setChiefComplaint(encounter.getChiefComplaint());
        form.setHistoryOfPresentIllness(encounter.getHistoryOfPresentIllness());
        form.setRelevantHistory(encounter.getRelevantHistory());
        form.setAssessmentNotes(encounter.getAssessmentNotes());
        form.setClinicalImpression(encounter.getClinicalImpression());

        // Load vitals
        vitalsRepository.findByEncounterId(encounterId).ifPresent(v -> {
            form.setTemperature(v.getTemperature());
            form.setHeartRate(v.getHeartRate());
            form.setSystolicBp(v.getSystolicBp());
            form.setDiastolicBp(v.getDiastolicBp());
            form.setRespiratoryRate(v.getRespiratoryRate());
            form.setOxygenSaturation(v.getOxygenSaturation());
            form.setHeight(v.getHeight());
            form.setWeight(v.getWeight());
            form.setVitalsNotes(v.getNotes());
        });

        // Load symptoms
        List<CaseTakingForm.SymptomForm> symptomForms = new ArrayList<>();
        symptomRepository.findByEncounterId(encounterId).forEach(s -> {
            CaseTakingForm.SymptomForm sf = new CaseTakingForm.SymptomForm();
            sf.setName(s.getName());
            sf.setDuration(s.getDuration());
            sf.setSeverity(s.getSeverity());
            sf.setOnset(s.getOnset());
            sf.setNotes(s.getNotes());
            symptomForms.add(sf);
        });
        form.setSymptoms(symptomForms);

        // Load examinations
        List<CaseTakingForm.ExaminationForm> examForms = new ArrayList<>();
        examinationRepository.findByEncounterId(encounterId).forEach(e -> {
            CaseTakingForm.ExaminationForm ef = new CaseTakingForm.ExaminationForm();
            ef.setExaminationArea(e.getExaminationArea());
            ef.setFindings(e.getFindings());
            ef.setNotes(e.getNotes());
            examForms.add(ef);
        });
        form.setExaminations(examForms);

        // Load diagnoses
        List<CaseTakingForm.DiagnosisForm> diagForms = new ArrayList<>();
        diagnosisRepository.findByEncounterId(encounterId).forEach(d -> {
            CaseTakingForm.DiagnosisForm df = new CaseTakingForm.DiagnosisForm();
            df.setDiagnosis(d.getDiagnosis());
            df.setNotes(d.getNotes());
            df.setStatus(d.getStatus());
            diagForms.add(df);
        });
        form.setDiagnoses(diagForms);

        // Load treatments
        List<CaseTakingForm.TreatmentForm> treatForms = new ArrayList<>();
        treatmentRepository.findByEncounterId(encounterId).forEach(t -> {
            CaseTakingForm.TreatmentForm tf = new CaseTakingForm.TreatmentForm();
            tf.setTreatment(t.getTreatment());
            tf.setInstructions(t.getInstructions());
            tf.setNotes(t.getNotes());
            treatForms.add(tf);
        });
        form.setTreatments(treatForms);

        // Load follow-up
        followUpRepository.findByEncounterId(encounterId).stream().findFirst().ifPresent(f -> {
            if (f.getFollowUpDate() != null) {
                form.setFollowUpDate(f.getFollowUpDate().toString());
            }
            form.setFollowUpInstructions(f.getInstructions());
            form.setFollowUpNotes(f.getNotes());
        });

        return form;
    }
}
