package com.patientcase.security;

import com.patientcase.audit.AuditAction;
import com.patientcase.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * Listens to Spring Security authentication events and records them in the audit log.
 * Passwords and credentials are never logged.
 */
@Component
public class AuthAuditListener {

    private static final Logger log = LoggerFactory.getLogger(AuthAuditListener.class);

    private final AuditService auditService;

    public AuthAuditListener(AuditService auditService) {
        this.auditService = auditService;
    }

    @EventListener
    public void onLoginSuccess(AuthenticationSuccessEvent event) {
        try {
            String username = extractUsername(event.getAuthentication().getPrincipal());
            auditService.log(AuditAction.LOGIN, "User", null, "Login successful for: " + username);
        } catch (Exception e) {
            log.warn("Failed to audit login event: {}", e.getMessage());
        }
    }

    @EventListener
    public void onLogout(LogoutSuccessEvent event) {
        try {
            if (event.getAuthentication() != null) {
                String username = extractUsername(event.getAuthentication().getPrincipal());
                auditService.log(AuditAction.LOGOUT, "User", null, "Logout for: " + username);
            }
        } catch (Exception e) {
            log.warn("Failed to audit logout event: {}", e.getMessage());
        }
    }

    private String extractUsername(Object principal) {
        if (principal instanceof UserDetails ud) {
            return ud.getUsername();
        }
        return principal != null ? principal.toString() : "unknown";
    }
}
