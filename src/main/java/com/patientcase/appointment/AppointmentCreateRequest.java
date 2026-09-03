package com.patientcase.appointment;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

public class AppointmentCreateRequest {

    @NotNull(message = "Patient is required")
    private Long patientId;

    @NotNull(message = "Clinician is required")
    private Long clinicianId;

    @NotNull(message = "Appointment date and time is required")
    @Future(message = "Appointment must be in the future")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime appointmentDatetime;

    @Size(max = 500, message = "Reason must be at most 500 characters")
    private String reason;

    @Size(max = 2000, message = "Notes must be at most 2000 characters")
    private String notes;

    // Getters and Setters
    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }

    public Long getClinicianId() { return clinicianId; }
    public void setClinicianId(Long clinicianId) { this.clinicianId = clinicianId; }

    public LocalDateTime getAppointmentDatetime() { return appointmentDatetime; }
    public void setAppointmentDatetime(LocalDateTime appointmentDatetime) { this.appointmentDatetime = appointmentDatetime; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
