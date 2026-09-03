package com.patientcase.patient;

import com.patientcase.audit.AuditAction;
import com.patientcase.audit.AuditService;
import com.patientcase.common.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class PatientService {

    private final PatientRepository patientRepository;
    private final AuditService auditService;

    public PatientService(PatientRepository patientRepository, AuditService auditService) {
        this.patientRepository = patientRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<Patient> searchPatients(String search, Pageable pageable) {
        return patientRepository.searchPatients(search, pageable);
    }

    @Transactional(readOnly = true)
    public Patient findById(Long id) {
        return patientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found: " + id));
    }

    @Transactional(readOnly = true)
    public Patient findByPatientNumber(String patientNumber) {
        return patientRepository.findByPatientNumber(patientNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found: " + patientNumber));
    }

    @Transactional
    public Patient createPatient(PatientCreateRequest request) {
        Patient patient = new Patient();
        patient.setPatientNumber(generatePatientNumber());
        mapRequestToPatient(request, patient);
        Patient saved = patientRepository.save(patient);
        auditService.log(AuditAction.PATIENT_CREATED, "Patient", saved.getId(),
                "Patient " + saved.getPatientNumber() + " created");
        return saved;
    }

    @Transactional
    public Patient updatePatient(Long id, PatientUpdateRequest request) {
        Patient patient = findById(id);
        mapUpdateRequestToPatient(request, patient);
        Patient saved = patientRepository.save(patient);
        auditService.log(AuditAction.PATIENT_UPDATED, "Patient", saved.getId(),
                "Patient " + saved.getPatientNumber() + " updated");
        return saved;
    }

    @Transactional(readOnly = true)
    public long countAll() {
        return patientRepository.count();
    }

    private void mapRequestToPatient(PatientCreateRequest request, Patient patient) {
        patient.setFirstName(request.getFirstName());
        patient.setLastName(request.getLastName());
        patient.setDateOfBirth(request.getDateOfBirth());
        patient.setGender(request.getGender());
        patient.setPhone(request.getPhone());
        patient.setEmail(request.getEmail());
        patient.setAddress(request.getAddress());
        patient.setEmergencyContactName(request.getEmergencyContactName());
        patient.setEmergencyContactPhone(request.getEmergencyContactPhone());
        patient.setBloodGroup(request.getBloodGroup());
        patient.setAllergies(request.getAllergies());
    }

    private void mapUpdateRequestToPatient(PatientUpdateRequest request, Patient patient) {
        patient.setFirstName(request.getFirstName());
        patient.setLastName(request.getLastName());
        patient.setDateOfBirth(request.getDateOfBirth());
        patient.setGender(request.getGender());
        patient.setPhone(request.getPhone());
        patient.setEmail(request.getEmail());
        patient.setAddress(request.getAddress());
        patient.setEmergencyContactName(request.getEmergencyContactName());
        patient.setEmergencyContactPhone(request.getEmergencyContactPhone());
        patient.setBloodGroup(request.getBloodGroup());
        patient.setAllergies(request.getAllergies());
    }

    private String generatePatientNumber() {
        String prefix = "P-";
        long count = patientRepository.count() + 1;
        return prefix + String.format("%06d", count + 1000);
    }
}
