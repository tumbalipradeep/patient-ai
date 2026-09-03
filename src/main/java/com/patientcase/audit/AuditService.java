package com.patientcase.audit;

import com.patientcase.user.User;
import com.patientcase.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public AuditService(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditAction action, String entityType, Long entityId, String metadata) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setAction(action);
            auditLog.setEntityType(entityType);
            auditLog.setEntityId(entityId);
            auditLog.setMetadata(metadata);

            String username = getCurrentUsername();
            auditLog.setUsername(username);

            if (username != null) {
                userRepository.findByUsername(username).ifPresent(auditLog::setUser);
            }

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.warn("Failed to write audit log for action {}: {}", action, e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditAction action) {
        log(action, null, null, null);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> findFiltered(String username, AuditAction action,
                                        LocalDate from, LocalDate to,
                                        Pageable pageable) {
        LocalDateTime fromDt = (from != null) ? from.atStartOfDay() : null;
        LocalDateTime toDt   = (to   != null) ? to.atTime(23, 59, 59) : null;
        String usernameFilter = (username != null && username.isBlank()) ? null : username;
        return auditLogRepository.findFiltered(usernameFilter, action, fromDt, toDt, pageable);
    }

    private String getCurrentUsername() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                return auth.getName();
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }
}
