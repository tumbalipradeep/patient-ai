package com.patientcase.audit;

import com.patientcase.user.User;
import com.patientcase.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
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
        log(action, entityType, entityId, metadata, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditAction action, String entityType, Long entityId, String metadata, String ipAddress) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setAction(action);
            auditLog.setEntityType(entityType);
            auditLog.setEntityId(entityId);
            auditLog.setMetadata(metadata);
            auditLog.setIpAddress(ipAddress);

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
        log(action, null, null, null, null);
    }

    /**
     * Extracts the client IP address from an HttpServletRequest.
     *
     * Handles the X-Forwarded-For header for reverse-proxy deployments, but only
     * trusts the FIRST IP in the chain (the value appended by the outermost trusted proxy).
     * The raw header is NOT used verbatim — spoofed client-supplied headers are mitigated
     * by taking only the leftmost entry, which is the original client address as seen by
     * the first trusted proxy.
     *
     * In deployments where the app is NOT behind a trusted reverse proxy, consider
     * ignoring X-Forwarded-For entirely and returning only remoteAddr.
     */
    public static String extractIp(HttpServletRequest request) {
        if (request == null) return null;
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // Take only the first address — leftmost in the chain
            String first = xff.split(",")[0].strip();
            if (!first.isEmpty()) {
                return truncateIp(first);
            }
        }
        return truncateIp(request.getRemoteAddr());
    }

    private static String truncateIp(String ip) {
        if (ip == null) return null;
        // Column is VARCHAR(50) — guard against absurdly long values
        return ip.length() > 50 ? ip.substring(0, 50) : ip;
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
