package com.patientcase.case_management;

import com.patientcase.audit.AuditAction;
import com.patientcase.audit.AuditService;
import com.patientcase.common.ResourceNotFoundException;
import com.patientcase.patient.Patient;
import com.patientcase.patient.PatientRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PatientCaseService {

    private final PatientCaseRepository caseRepository;
    private final PatientRepository patientRepository;
    private final AuditService auditService;
    private final JdbcTemplate jdbcTemplate;

    public PatientCaseService(PatientCaseRepository caseRepository,
                               PatientRepository patientRepository,
                               AuditService auditService,
                               JdbcTemplate jdbcTemplate) {
        this.caseRepository = caseRepository;
        this.patientRepository = patientRepository;
        this.auditService = auditService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public PatientCase findById(Long id) {
        return caseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Case not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<PatientCase> findByPatientId(Long patientId) {
        return caseRepository.findByPatientIdOrderByCreatedAtDesc(patientId);
    }

    @Transactional
    public PatientCase createCase(CaseCreateRequest request) {
        Patient patient = patientRepository.findById(request.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found: " + request.getPatientId()));

        PatientCase patientCase = new PatientCase();
        patientCase.setCaseNumber(generateCaseNumber());
        patientCase.setPatient(patient);
        patientCase.setTitle(request.getTitle());
        patientCase.setChiefComplaint(request.getChiefComplaint());
        patientCase.setStatus(request.getStatus());
        patientCase.setPriority(request.getPriority());

        PatientCase saved = caseRepository.save(patientCase);
        auditService.log(AuditAction.CASE_CREATED, "PatientCase", saved.getId(),
                "Case " + saved.getCaseNumber() + " created for patient " + patient.getPatientNumber());
        return saved;
    }

    @Transactional
    public PatientCase updateCase(Long id, CaseUpdateRequest request) {
        PatientCase patientCase = findById(id);
        patientCase.setTitle(request.getTitle());
        patientCase.setChiefComplaint(request.getChiefComplaint());
        patientCase.setStatus(request.getStatus());
        patientCase.setPriority(request.getPriority());
        PatientCase saved = caseRepository.save(patientCase);
        auditService.log(AuditAction.CASE_UPDATED, "PatientCase", saved.getId(),
                "Case " + saved.getCaseNumber() + " updated");
        return saved;
    }

    @Transactional(readOnly = true)
    public long countActiveCases() {
        return caseRepository.countActiveCases();
    }

    @Transactional(readOnly = true)
    public List<PatientCase> findRecentCases(int limit) {
        return caseRepository.findRecentCases(PageRequest.of(0, limit));
    }

    private String generateCaseNumber() {
        Long nextVal = jdbcTemplate.queryForObject("SELECT NEXTVAL('case_number_seq')", Long.class);
        int year = java.time.LocalDate.now().getYear();
        return "C-" + year + "-" + String.format("%03d", nextVal);
    }
}
