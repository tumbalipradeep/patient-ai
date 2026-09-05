package com.patientcase.case_management;

import com.patientcase.encounter.Encounter;
import com.patientcase.encounter.EncounterCreateRequest;
import com.patientcase.encounter.EncounterService;
import com.patientcase.patient.Patient;
import com.patientcase.patient.PatientService;
import com.patientcase.user.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/cases")
public class PatientCaseController {

    private final PatientCaseService caseService;
    private final PatientService patientService;
    private final EncounterService encounterService;
    private final UserService userService;

    public PatientCaseController(PatientCaseService caseService,
                                  PatientService patientService,
                                  EncounterService encounterService,
                                  UserService userService) {
        this.caseService = caseService;
        this.patientService = patientService;
        this.encounterService = encounterService;
        this.userService = userService;
    }

    @PostMapping("/new")
    public String createCase(@Valid @ModelAttribute("caseForm") CaseCreateRequest request,
                              BindingResult result,
                              RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please correct the form errors.");
            return "redirect:/patients/" + request.getPatientId();
        }
        PatientCase patientCase = caseService.createCase(request);
        redirectAttributes.addFlashAttribute("successMessage",
                "Case " + patientCase.getCaseNumber() + " created successfully.");
        return "redirect:/cases/" + patientCase.getId();
    }
@GetMapping("/new")
public String newCaseForm(@RequestParam Long patientId, Model model) {
    CaseCreateRequest request = new CaseCreateRequest();
    request.setPatientId(patientId);

    model.addAttribute("caseForm", request);
    model.addAttribute("caseStatuses", CaseStatus.values());
    model.addAttribute("casePriorities", CasePriority.values());

    return "cases/new";
}
    @GetMapping("/{id}")
    public String viewCase(@PathVariable Long id, Model model) {
        PatientCase patientCase = caseService.findById(id);
        List<Encounter> encounters = encounterService.findByCaseId(id);

        model.addAttribute("patientCase", patientCase);
        model.addAttribute("encounters", encounters);
        model.addAttribute("statuses", CaseStatus.values());
        model.addAttribute("priorities", CasePriority.values());

        // Encounter creation form
        EncounterCreateRequest encounterRequest = new EncounterCreateRequest();
        encounterRequest.setCaseId(id);
        model.addAttribute("encounterForm", encounterRequest);
        model.addAttribute("clinicians", userService.findAllClinicians());

        return "cases/view";
    }

    @GetMapping("/{id}/edit")
    public String editCaseForm(@PathVariable Long id, Model model) {
        PatientCase patientCase = caseService.findById(id);
        CaseUpdateRequest request = new CaseUpdateRequest();
        request.setTitle(patientCase.getTitle());
        request.setChiefComplaint(patientCase.getChiefComplaint());
        request.setStatus(patientCase.getStatus());
        request.setPriority(patientCase.getPriority());

        model.addAttribute("patientCase", patientCase);
        model.addAttribute("caseForm", request);
        model.addAttribute("statuses", CaseStatus.values());
        model.addAttribute("priorities", CasePriority.values());
        return "cases/edit";
    }

    @PostMapping("/{id}/edit")
    public String updateCase(@PathVariable Long id,
                              @Valid @ModelAttribute("caseForm") CaseUpdateRequest request,
                              BindingResult result,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            PatientCase patientCase = caseService.findById(id);
            model.addAttribute("patientCase", patientCase);
            model.addAttribute("statuses", CaseStatus.values());
            model.addAttribute("priorities", CasePriority.values());
            return "cases/edit";
        }
        caseService.updateCase(id, request);
        redirectAttributes.addFlashAttribute("successMessage", "Case updated successfully.");
        return "redirect:/cases/" + id;
    }
}
