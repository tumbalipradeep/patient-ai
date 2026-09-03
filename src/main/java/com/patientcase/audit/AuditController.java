package com.patientcase.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@Controller
@RequestMapping("/admin/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    private static final int PAGE_SIZE = 25;

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public String auditLog(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Model model) {

        Page<AuditLog> auditPage = auditService.findFiltered(
                username, action, from, to,
                PageRequest.of(page, PAGE_SIZE, Sort.by("createdAt").descending()));

        model.addAttribute("auditPage", auditPage);
        model.addAttribute("actions", AuditAction.values());
        model.addAttribute("username", username);
        model.addAttribute("selectedAction", action);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("currentPage", page);
        return "admin/audit";
    }
}
