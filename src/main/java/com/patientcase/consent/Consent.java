package com.patientcase.consent;

import com.patientcase.patient.Patient;
import com.patientcase.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Explicit, purpose-oriented patient consent record.
 *
 * Captures what data was collected, why, in what context (including AI
 * assistance and document processing), and how it may be shared. Status
 * transitions from GRANTED to REVOKED when the patient withdraws consent.
 *
 * Every grant and revocation is audited via AuditService.
 */
@Entity
@Table(name = "consents")
public class Consent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /** e.g. "patient_intake", "document_digitization", "ai_assistance", "share_his" */
    @Column(nullable = false, length = 150)
    private String purpose;

    /** Consent form/document version the patient accepted. */
    @Column(length = 50)
    private String version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConsentStatus status = ConsentStatus.GRANTED;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private LocalDateTime grantedAt = LocalDateTime.now();

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Consent() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Patient getPatient() { return patient; }
    public void setPatient(Patient patient) { this.patient = patient; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public ConsentStatus getStatus() { return status; }
    public void setStatus(ConsentStatus status) { this.status = status; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public LocalDateTime getGrantedAt() { return grantedAt; }
    public void setGrantedAt(LocalDateTime grantedAt) { this.grantedAt = grantedAt; }

    public LocalDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(LocalDateTime revokedAt) { this.revokedAt = revokedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}