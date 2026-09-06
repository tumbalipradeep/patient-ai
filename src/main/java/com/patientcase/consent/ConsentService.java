package com.patientcase.consent;

import com.patientcase.audit.AuditAction;
import com.patientcase.audit.AuditService;
import com.patientcase.patient.Patient;
import com.patientcase.patient.PatientRepository;
import com.patientcase.user.User;
import com.patientcase.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages explicit, purpose-oriented patient consent records.
 *
 * Consent is granted by the patient before clinical intake begins and may be
 * revoked at any time. Every grant and revocation is written to the audit log.
 */
@Service
public class ConsentService {

    private final ConsentRepository consentRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public ConsentService(ConsentRepository consentRepository,
                          PatientRepository patientRepository,
                          UserRepository userRepository,
                          AuditService auditService) {
        this.consentRepository = consentRepository;
        this.patientRepository = patientRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public Consent grant(Long patientId, Long userId, String purpose, String version, String ipAddress) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found: " + patientId));
        User user = userId == null ? null : userRepository.findById(userId).orElse(null);

        Consent consent = new Consent();
        consent.setPatient(patient);
        consent.setUser(user);
        consent.setPurpose(purpose);
        consent.setVersion(version);
        consent.setStatus(ConsentStatus.GRANTED);
        consent.setIpAddress(ipAddress);

        Consent saved = consentRepository.save(consent);
        auditService.log(AuditAction.CONSENT_GRANTED, "Consent", saved.getId(),
                "Consent granted for purpose: " + purpose, ipAddress);
        return saved;
    }

    @Transactional
    public void revoke(Long consentId, String ipAddress) {
        Consent consent = consentRepository.findById(consentId)
                .orElseThrow(() -> new IllegalArgumentException("Consent record not found: " + consentId));
        if (consent.getStatus() == ConsentStatus.REVOKED) {
            throw new IllegalStateException("Consent has already been revoked.");
        }
        consent.setStatus(ConsentStatus.REVOKED);
        consent.setRevokedAt(java.time.LocalDateTime.now());
        consentRepository.save(consent);
        auditService.log(AuditAction.CONSENT_REVOKED, "Consent", consent.getId(),
                "Consent revoked for purpose: " + consent.getPurpose(), ipAddress);
    }

    @Transactional(readOnly = true)
    public List<Consent> findByPatientId(Long patientId) {
        return consentRepository.findByPatientIdOrderByGrantedAtDesc(patientId);
    }

    @Transactional(readOnly = true)
    public boolean hasActiveConsent(Long patientId, String purpose) {
        return consentRepository.findTop3ByPatientIdOrderByGrantedAtDesc(patientId)
                .stream()
                .anyMatch(c -> c.getStatus() == ConsentStatus.GRANTED
                        && (purpose == null || purpose.equals(c.getPurpose())));
    }

    @Transactional(readOnly = true)
    public List<Consent> recentForPatient(Long patientId, int limit) {
        int safeLimit = Math.min(limit, 20);
        List<Consent> all = consentRepository.findByPatientIdOrderByGrantedAtDesc(patientId);
        return all.size() > safeLimit ? new ArrayList<>(all.subList(0, safeLimit)) : all;
    }
}