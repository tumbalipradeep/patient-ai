package com.patientcase.appointment;

import com.patientcase.patient.PatientService;
import com.patientcase.user.Role;
import com.patientcase.user.User;
import com.patientcase.user.UserRepository;
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
    private final UserRepository userRepository;

    public AppointmentController(AppointmentService appointmentService,
                                  PatientService patientService,
                                  UserRepository userRepository) {
        this.appointmentService = appointmentService;
        this.patientService = patientService;
        this.userRepository = userRepository;
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
        appointmentService.createAppointment(request);
        redirectAttributes.addFlashAttribute("successMessage", "Appointment scheduled successfully.");
        return "redirect:/appointments";
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
        appointmentService.updateAppointment(id, request);
        redirectAttributes.addFlashAttribute("successMessage", "Appointment updated successfully.");
        return "redirect:/appointments";
    }

    @PostMapping("/{id}/status")
    public String updateStatus(@PathVariable Long id,
                                @RequestParam AppointmentStatus status,
                                RedirectAttributes redirectAttributes) {
        appointmentService.updateStatus(id, status);
        redirectAttributes.addFlashAttribute("successMessage", "Appointment status updated.");
        return "redirect:/appointments";
    }

    private void populateFormModel(Model model) {
        model.addAttribute("patients", patientService.searchPatients("", PageRequest.of(0, 1000)).getContent());
        model.addAttribute("clinicians", userRepository.findByEnabledTrue());
    }
}
