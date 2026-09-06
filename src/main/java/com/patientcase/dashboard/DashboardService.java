package com.patientcase.dashboard;

import com.patientcase.appointment.Appointment;
import com.patientcase.appointment.AppointmentService;
import com.patientcase.case_management.PatientCase;
import com.patientcase.case_management.PatientCaseService;
import com.patientcase.encounter.Encounter;
import com.patientcase.encounter.EncounterService;
import com.patientcase.kiosk.KioskIntakeService;
import com.patientcase.patient.PatientService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DashboardService {

    private final PatientService patientService;
    private final PatientCaseService caseService;
    private final EncounterService encounterService;
    private final AppointmentService appointmentService;
    private final KioskIntakeService intakeService;

    public DashboardService(PatientService patientService,
                             PatientCaseService caseService,
                             EncounterService encounterService,
                             AppointmentService appointmentService,
                             KioskIntakeService intakeService) {
        this.patientService = patientService;
        this.caseService = caseService;
        this.encounterService = encounterService;
        this.appointmentService = appointmentService;
        this.intakeService = intakeService;
    }

    @Transactional(readOnly = true)
    public DashboardStats getStats() {
        DashboardStats stats = new DashboardStats();
        stats.setTotalPatients(patientService.countAll());
        stats.setActiveCases(caseService.countActiveCases());
        stats.setTodayAppointments(appointmentService.countTodayAppointments());
        stats.setPendingIntakes(intakeService.countPendingReview());

        List<Encounter> recentEncounters = encounterService.findRecentEncounters(5);
        stats.setRecentEncounters(recentEncounters);

        List<Appointment> upcomingAppointments = appointmentService.findUpcomingAppointments(5);
        stats.setUpcomingAppointments(upcomingAppointments);

        List<PatientCase> recentCases = caseService.findRecentCases(5);
        stats.setRecentCases(recentCases);

        return stats;
    }

    public static class DashboardStats {
        private long totalPatients;
        private long activeCases;
        private long todayAppointments;
        private long pendingIntakes;
        private List<Encounter> recentEncounters;
        private List<Appointment> upcomingAppointments;
        private List<PatientCase> recentCases;

        public long getTotalPatients() { return totalPatients; }
        public void setTotalPatients(long totalPatients) { this.totalPatients = totalPatients; }

        public long getActiveCases() { return activeCases; }
        public void setActiveCases(long activeCases) { this.activeCases = activeCases; }

        public long getTodayAppointments() { return todayAppointments; }
        public void setTodayAppointments(long todayAppointments) { this.todayAppointments = todayAppointments; }

        public long getPendingIntakes() { return pendingIntakes; }
        public void setPendingIntakes(long pendingIntakes) { this.pendingIntakes = pendingIntakes; }

        public List<Encounter> getRecentEncounters() { return recentEncounters; }
        public void setRecentEncounters(List<Encounter> recentEncounters) { this.recentEncounters = recentEncounters; }

        public List<Appointment> getUpcomingAppointments() { return upcomingAppointments; }
        public void setUpcomingAppointments(List<Appointment> upcomingAppointments) { this.upcomingAppointments = upcomingAppointments; }

        public List<PatientCase> getRecentCases() { return recentCases; }
        public void setRecentCases(List<PatientCase> recentCases) { this.recentCases = recentCases; }
    }
}
