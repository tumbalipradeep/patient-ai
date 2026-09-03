package com.patientcase.appointment;

import com.patientcase.audit.AuditAction;
import com.patientcase.audit.AuditService;
import com.patientcase.common.ResourceNotFoundException;
import com.patientcase.patient.Patient;
import com.patientcase.patient.PatientRepository;
import com.patientcase.user.User;
import com.patientcase.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public AppointmentService(AppointmentRepository appointmentRepository,
                               PatientRepository patientRepository,
                               UserRepository userRepository,
                               AuditService auditService) {
        this.appointmentRepository = appointmentRepository;
        this.patientRepository = patientRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Appointment findById(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<Appointment> findAll(Pageable pageable) {
        return appointmentRepository.findAllWithDetails(pageable);
    }

    @Transactional
    public Appointment createAppointment(AppointmentCreateRequest request) {
        Patient patient = patientRepository.findById(request.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found: " + request.getPatientId()));
        User clinician = userRepository.findById(request.getClinicianId())
                .orElseThrow(() -> new ResourceNotFoundException("Clinician not found: " + request.getClinicianId()));

        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setClinician(clinician);
        appointment.setAppointmentDatetime(request.getAppointmentDatetime());
        appointment.setStatus(AppointmentStatus.SCHEDULED);
        appointment.setReason(request.getReason());
        appointment.setNotes(request.getNotes());

        Appointment saved = appointmentRepository.save(appointment);
        auditService.log(AuditAction.APPOINTMENT_CREATED, "Appointment", saved.getId(),
                "Appointment for patient " + patient.getPatientNumber());
        return saved;
    }

    @Transactional
    public Appointment updateStatus(Long id, AppointmentStatus status) {
        Appointment appointment = findById(id);
        appointment.setStatus(status);
        Appointment saved = appointmentRepository.save(appointment);
        auditService.log(AuditAction.APPOINTMENT_UPDATED, "Appointment", saved.getId(),
                "Status updated to " + status);
        return saved;
    }

    @Transactional
    public Appointment updateAppointment(Long id, AppointmentCreateRequest request) {
        Appointment appointment = findById(id);
        Patient patient = patientRepository.findById(request.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found: " + request.getPatientId()));
        User clinician = userRepository.findById(request.getClinicianId())
                .orElseThrow(() -> new ResourceNotFoundException("Clinician not found: " + request.getClinicianId()));

        appointment.setPatient(patient);
        appointment.setClinician(clinician);
        appointment.setAppointmentDatetime(request.getAppointmentDatetime());
        appointment.setReason(request.getReason());
        appointment.setNotes(request.getNotes());

        Appointment saved = appointmentRepository.save(appointment);
        auditService.log(AuditAction.APPOINTMENT_UPDATED, "Appointment", saved.getId(), "Appointment updated");
        return saved;
    }

    @Transactional(readOnly = true)
    public long countTodayAppointments() {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        return appointmentRepository.countTodayAppointments(start, end);
    }

    @Transactional(readOnly = true)
    public List<Appointment> findUpcomingAppointments(int limit) {
        return appointmentRepository.findUpcomingAppointments(LocalDateTime.now(), PageRequest.of(0, limit));
    }
}
