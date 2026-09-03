package com.patientcase.patient;

import com.patientcase.case_management.CaseCreateRequest;
import com.patientcase.case_management.CasePriority;
import com.patientcase.case_management.CaseStatus;
import com.patientcase.case_management.PatientCase;
import com.patientcase.case_management.PatientCaseService;
import com.patientcase.encounter.Encounter;
import com.patientcase.encounter.EncounterService;
import com.patientcase.appointment.AppointmentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/patients")
public class PatientController {

    private final PatientService patientService;
    private final PatientCaseService caseService;
    private final EncounterService encounterService;

    public PatientController(PatientService patientService,
                              PatientCaseService caseService,
                              EncounterService encounterService) {
        this.patientService = patientService;
        this.caseService = caseService;
        this.encounterService = encounterService;
    }

    @GetMapping
    public String listPatients(@RequestParam(defaultValue = "") String search,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "15") int size,
                                Model model) {
        Page<Patient> patients = patientService.searchPatients(
                search,
                PageRequest.of(page, size, Sort.by("lastName").ascending())
        );
        model.addAttribute("patients", patients);
        model.addAttribute("search", search);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", patients.getTotalPages());
        return "patients/list";
    }

    @GetMapping("/new")
    public String newPatientForm(Model model) {
        model.addAttribute("patientForm", new PatientCreateRequest());
        model.addAttribute("genders", Gender.values());
        model.addAttribute("bloodGroups", new String[]{"A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"});
        return "patients/new";
    }

    @PostMapping("/new")
    public String createPatient(@Valid @ModelAttribute("patientForm") PatientCreateRequest request,
                                 BindingResult result,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("genders", Gender.values());
            model.addAttribute("bloodGroups", new String[]{"A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"});
            return "patients/new";
        }
        Patient patient = patientService.createPatient(request);
        redirectAttributes.addFlashAttribute("successMessage", "Patient " + patient.getPatientNumber() + " registered successfully.");
        return "redirect:/patients/" + patient.getId();
    }

    @GetMapping("/{id}")
    public String viewPatient(@PathVariable Long id, Model model) {
        Patient patient = patientService.findById(id);
        List<PatientCase> cases = caseService.findByPatientId(id);
        model.addAttribute("patient", patient);
        model.addAttribute("cases", cases);
        model.addAttribute("caseStatuses", CaseStatus.values());
        model.addAttribute("casePriorities", CasePriority.values());

        // Pre-fill case creation form
        CaseCreateRequest caseRequest = new CaseCreateRequest();
        caseRequest.setPatientId(id);
        model.addAttribute("caseForm", caseRequest);

        return "patients/profile";
    }

    @GetMapping("/{id}/edit")
    public String editPatientForm(@PathVariable Long id, Model model) {
        Patient patient = patientService.findById(id);
        PatientUpdateRequest request = new PatientUpdateRequest();
        request.setFirstName(patient.getFirstName());
        request.setLastName(patient.getLastName());
        request.setDateOfBirth(patient.getDateOfBirth());
        request.setGender(patient.getGender());
        request.setPhone(patient.getPhone());
        request.setEmail(patient.getEmail());
        request.setAddress(patient.getAddress());
        request.setEmergencyContactName(patient.getEmergencyContactName());
        request.setEmergencyContactPhone(patient.getEmergencyContactPhone());
        request.setBloodGroup(patient.getBloodGroup());
        request.setAllergies(patient.getAllergies());

        model.addAttribute("patient", patient);
        model.addAttribute("patientForm", request);
        model.addAttribute("genders", Gender.values());
        model.addAttribute("bloodGroups", new String[]{"A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"});
        return "patients/edit";
    }

    @PostMapping("/{id}/edit")
    public String updatePatient(@PathVariable Long id,
                                 @Valid @ModelAttribute("patientForm") PatientUpdateRequest request,
                                 BindingResult result,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            Patient patient = patientService.findById(id);
            model.addAttribute("patient", patient);
            model.addAttribute("genders", Gender.values());
            model.addAttribute("bloodGroups", new String[]{"A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"});
            return "patients/edit";
        }
        Patient patient = patientService.updatePatient(id, request);
        redirectAttributes.addFlashAttribute("successMessage", "Patient updated successfully.");
        return "redirect:/patients/" + patient.getId();
    }
}
