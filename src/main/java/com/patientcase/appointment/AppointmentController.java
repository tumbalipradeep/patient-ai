package com.patientcase.appointment;

import com.patientcase.patient.PatientService;
import com.patientcase.user.UserService;
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
@RequestMapping("/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final PatientService patientService;
    private final UserService userService;

    public AppointmentController(AppointmentService appointmentService,
                                  PatientService patientService,
                                  UserService userService) {
        this.appointmentService = appointmentService;
        this.patientService = patientService;
        this.userService = userService;
    }

    @GetMapping
    public String listAppointments(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "15") int size,
                                    Model model) {
        Page<Appointment> appointments = appointmentService.findAll(
                PageRequest.of(page, size, Sort.by("appointmentDatetime").descending()));
        model.addAttribute("appointments", appointments);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", appointments.getTotalPages());
        model.addAttribute("statuses", AppointmentStatus.values());
        return "appointments/list";
    }

    @GetMapping("/new")
    public String newAppointmentForm(Model model) {
        model.addAttribute("appointmentForm", new AppointmentCreateRequest());
        populateFormModel(model);
        return "appointments/new";
    }

    @PostMapping("/new")
    public String createAppointment(@Valid @ModelAttribute("appointmentForm") AppointmentCreateRequest request,
                                     BindingResult result,
                                     Model model,
                                     RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            populateFormModel(model);
            return "appointments/new";
        }
        try {
            appointmentService.createAppointment(request);
            redirectAttributes.addFlashAttribute("successMessage", "Appointment scheduled successfully.");
            return "redirect:/appointments";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            populateFormModel(model);
            return "appointments/new";
        }
    }

    @GetMapping("/{id}/edit")
    public String editAppointmentForm(@PathVariable Long id, Model model) {
        Appointment appointment = appointmentService.findById(id);
        AppointmentCreateRequest request = new AppointmentCreateRequest();
        request.setPatientId(appointment.getPatient().getId());
        request.setClinicianId(appointment.getClinician().getId());
        request.setAppointmentDatetime(appointment.getAppointmentDatetime());
        request.setReason(appointment.getReason());
        request.setNotes(appointment.getNotes());

        model.addAttribute("appointment", appointment);
        model.addAttribute("appointmentForm", request);
        model.addAttribute("statuses", AppointmentStatus.values());
        populateFormModel(model);
        return "appointments/edit";
    }

    @PostMapping("/{id}/edit")
    public String updateAppointment(@PathVariable Long id,
                                     @Valid @ModelAttribute("appointmentForm") AppointmentCreateRequest request,
                                     BindingResult result,
                                     Model model,
                                     RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            Appointment appointment = appointmentService.findById(id);
            model.addAttribute("appointment", appointment);
            model.addAttribute("statuses", AppointmentStatus.values());
            populateFormModel(model);
            return "appointments/edit";
        }
        try {
            appointmentService.updateAppointment(id, request);
            redirectAttributes.addFlashAttribute("successMessage", "Appointment updated successfully.");
            return "redirect:/appointments";
        } catch (IllegalArgumentException e) {
            model.addAttribute("appointment", appointmentService.findById(id));
            model.addAttribute("statuses", AppointmentStatus.values());
            model.addAttribute("errorMessage", e.getMessage());
            populateFormModel(model);
            return "appointments/edit";
        }
    }

    @PostMapping("/{id}/status")
    public String updateStatus(@PathVariable Long id,
                                @RequestParam String status,
                                RedirectAttributes redirectAttributes) {
        try {
            appointmentService.updateStatus(id, AppointmentStatus.valueOf(status));
            redirectAttributes.addFlashAttribute("successMessage", "Appointment status updated.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Invalid appointment status.");
        }
        return "redirect:/appointments";
    }

    private void populateFormModel(Model model) {
        model.addAttribute("patients", patientService.searchPatients("", PageRequest.of(0, 1000)).getContent());
        model.addAttribute("clinicians", userService.findAllClinicians());
    }
}
