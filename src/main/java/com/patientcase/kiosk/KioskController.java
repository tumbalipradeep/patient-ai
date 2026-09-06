package com.patientcase.kiosk;

import com.patientcase.audit.AuditService;
import com.patientcase.consent.Consent;
import com.patientcase.consent.ConsentService;
import com.patientcase.document.Document;
import com.patientcase.document.DocumentService;
import com.patientcase.document.OcrExtractionService;
import com.patientcase.patient.Patient;
import com.patientcase.user.User;
import com.patientcase.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Patient-facing MediKiosk portal. All /api/kiosk/** and authenticated routes
 * under /kiosk/** are restricted to the PATIENT role by SecurityConfig, with
 * ownership enforced per-method (an intake is only accessible to the patient
 * it belongs to).
 *
 * Public entry:
 *   GET  /kiosk                       — language selection
 *   GET  /kiosk/login                 — patient sign-in access point (uses the shared /login form)
 *   GET/POST /kiosk/register          — patient self-registration
 *
 * Authenticated patient flow:
 *   GET  /kiosk/home                  — welcome portal / progress
 *   GET/POST /kiosk/consent           — explicit consent for the intake
 *   GET  /kiosk/intake/{id}           — conversational intake (chat UI)
 *   POST /kiosk/intake/{id}/ayush     — AYUSH Dashavidha Pariksha capture
 *   GET  /kiosk/intake/{id}/documents — document upload for prior records
 *   POST /kiosk/intake/{id}/documents — upload + digitization request
 *   GET  /kiosk/intake/{id}/summary   — patient review of their submitted info
 *   POST /kiosk/intake/{id}/submit    — submit for physician review
 */
@Controller
@RequestMapping("/kiosk")
public class KioskController {

    private static final Logger log = LoggerFactory.getLogger(KioskController.class);

    public static final String CONSENT_PURPOSE = "patient_intake";
    public static final String CONSENT_VERSION = "medikiosk-1.0";

    private final PatientRegistrationService registrationService;
    private final KioskIntakeService intakeService;
    private final ConsentService consentService;
    private final DocumentService documentService;
    private final OcrExtractionService extractionService;
    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    public KioskController(PatientRegistrationService registrationService,
                           KioskIntakeService intakeService,
                           ConsentService consentService,
                           DocumentService documentService,
                           OcrExtractionService extractionService,
                           UserRepository userRepository,
                           AuthenticationManager authenticationManager,
                           SecurityContextRepository securityContextRepository) {
        this.registrationService = registrationService;
        this.intakeService = intakeService;
        this.consentService = consentService;
        this.documentService = documentService;
        this.extractionService = extractionService;
        this.userRepository = userRepository;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    // ---- Public entry ----

    @GetMapping
    public String languageSelection() {
        return "kiosk/index";
    }

    @GetMapping("/login")
    public String patientLogin(@RequestParam(name = "lang", required = false) String lang,
                               Model model) {
        model.addAttribute("lang", lang != null ? lang : "en");
        return "kiosk/login";
    }

    @GetMapping("/register")
    public String registerPage(@RequestParam(name = "lang", required = false) String lang,
                               Model model) {
        model.addAttribute("lang", lang != null ? lang : "en");
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new PatientRegistrationService.RegistrationForm());
        }
        return "kiosk/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") PatientRegistrationService.RegistrationForm form,
                           BindingResult bindingResult,
                           HttpServletRequest request,
                           HttpServletResponse response,
                           RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "kiosk/register";
        }
        try {
            registrationService.register(form, AuditService.extractIp(request));
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            redirectAttributes.addFlashAttribute("form", form);
            return "redirect:/kiosk/register";
        } catch (Exception e) {
            log.warn("Kiosk registration failed for '{}': {}", form.getUsername(), e.getClass().getSimpleName());
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Registration could not be completed. Please try again or ask reception for help.");
            redirectAttributes.addFlashAttribute("form", form);
            return "redirect:/kiosk/register";
        }

        // Auto-sign-in for a smooth kiosk experience; failure falls back to the login page.
        if (authenticate(form.getUsername(), form.getPassword(), request, response)) {
            return "redirect:/kiosk/consent";
        }
        redirectAttributes.addFlashAttribute("infoMessage",
                "Account created. Please sign in to continue.");
        return "redirect:/kiosk/login";
    }

    // ---- Authenticated patient portal ----

    @GetMapping("/home")
    public String home(Authentication authentication, Model model) {
        Patient patient = currentPatient(authentication);
        model.addAttribute("patient", patient);
        model.addAttribute("activeIntake",
                intakeService.getOrCreateActiveIntake(patient.getId(), currentUserId(authentication)));
        model.addAttribute("intakes", intakeService.findIntakesForPatient(patient.getId()));
        return "kiosk/home";
    }

    @GetMapping("/consent")
    public String consentPage(Authentication authentication, Model model) {
        Patient patient = currentPatient(authentication);
        KioskIntake intake = intakeService.getOrCreateActiveIntake(patient.getId(), currentUserId(authentication));
        model.addAttribute("patient", patient);
        model.addAttribute("intake", intake);
        model.addAttribute("consentGranted", intake.getConsent() != null);
        model.addAttribute("purpose", CONSENT_PURPOSE);
        model.addAttribute("version", CONSENT_VERSION);
        return "kiosk/consent";
    }

    @PostMapping("/consent")
    public String grantConsent(Authentication authentication,
                               HttpServletRequest request,
                               RedirectAttributes redirectAttributes) {
        Patient patient = currentPatient(authentication);
        KioskIntake intake = intakeService.getOrCreateActiveIntake(patient.getId(), currentUserId(authentication));
        if (intake.getConsent() == null) {
            Consent consent = consentService.grant(patient.getId(), currentUserId(authentication),
                    CONSENT_PURPOSE, CONSENT_VERSION, AuditService.extractIp(request));
            intakeService.bindConsent(intake.getId(), patient.getId(), consent);
        }
        redirectAttributes.addFlashAttribute("successMessage",
                "Thank you. Your consent has been recorded.");
        return "redirect:/kiosk/intake/" + intake.getId();
    }

    @GetMapping("/intake/{id}")
    public String intakePage(@PathVariable Long id,
                             Authentication authentication,
                             Model model) {
        Patient patient = currentPatient(authentication);
        KioskIntake intake = intakeService.requireOwnedIntake(id, patient.getId());
        model.addAttribute("patient", patient);
        model.addAttribute("intake", intake);
        model.addAttribute("sessionStatus", intake.getStatus().name());
        model.addAttribute("hasDraft",
                intake.getStatus() == KioskIntakeStatus.DRAFT_READY
                || intake.getStatus() == KioskIntakeStatus.SUBMITTED
                || intake.getStatus() == KioskIntakeStatus.ACCEPTED);
        model.addAttribute("consentGranted", intake.getConsent() != null);
        return "kiosk/intake";
    }

    @GetMapping("/intake/{id}/summary")
    public String summaryPage(@PathVariable Long id,
                              Authentication authentication,
                              Model model) {
        Patient patient = currentPatient(authentication);
        KioskIntake intake = intakeService.requireOwnedIntake(id, patient.getId());
        model.addAttribute("patient", patient);
        model.addAttribute("intake", intake);
        model.addAttribute("draft", intakeService.getDraft(id, patient.getId()));
        model.addAttribute("redFlags", intakeService.getRedFlagsForIntake(id));
        model.addAttribute("ayush", intakeService.getAyush(id));
        model.addAttribute("documents", documentService.findByPatientId(patient.getId()));
        model.addAttribute("submittable",
                intake.getStatus() == KioskIntakeStatus.DRAFT_READY
                || intake.getStatus() == KioskIntakeStatus.IN_PROGRESS);
        return "kiosk/summary";
    }

    @PostMapping("/intake/{id}/submit")
    public String submit(@PathVariable Long id,
                         Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        Patient patient = currentPatient(authentication);
        try {
            intakeService.submit(id, patient.getId());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Thank you. Your intake has been submitted for physician review.");
            return "redirect:/kiosk/home";
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/kiosk/intake/" + id + "/summary";
        }
    }

    @GetMapping("/intake/{id}/ayush")
    public String ayushPage(@PathVariable Long id,
                            Authentication authentication,
                            Model model) {
        Patient patient = currentPatient(authentication);
        KioskIntake intake = intakeService.requireOwnedIntake(id, patient.getId());
        model.addAttribute("patient", patient);
        model.addAttribute("intake", intake);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new KioskIntakeService.AyushForm());
        }
        return "kiosk/ayush";
    }

    @PostMapping("/intake/{id}/ayush")
    public String saveAyush(@PathVariable Long id,
                            @Valid @ModelAttribute("form") KioskIntakeService.AyushForm form,
                            BindingResult bindingResult,
                            Authentication authentication,
                            RedirectAttributes redirectAttributes) {
        Patient patient = currentPatient(authentication);
        if (bindingResult.hasErrors()) {
            return "kiosk/ayush";
        }
        intakeService.saveAyush(id, patient.getId(), form);
        redirectAttributes.addFlashAttribute("successMessage",
                "Lifestyle information saved.");
        return "redirect:/kiosk/intake/" + id + "/summary";
    }

    @GetMapping("/intake/{id}/documents")
    public String documentsPage(@PathVariable Long id,
                                Authentication authentication,
                                Model model) {
        Patient patient = currentPatient(authentication);
        KioskIntake intake = intakeService.requireOwnedIntake(id, patient.getId());
        model.addAttribute("patient", patient);
        model.addAttribute("intake", intake);
        model.addAttribute("documents", documentService.findByPatientId(patient.getId()));
        model.addAttribute("digitizationAvailable", extractionService.isDigitizationAvailable());
        model.addAttribute("digitizationProvider", extractionService.providerId());

        Map<Long, com.patientcase.document.DocumentExtraction> extractionsByDocument = new java.util.HashMap<>();
        for (com.patientcase.document.Document doc
                : documentService.findByPatientId(patient.getId())) {
            extractionService.findByDocumentId(doc.getId())
                    .ifPresent(ex -> extractionsByDocument.put(doc.getId(), ex));
        }
        model.addAttribute("extractionsByDocument", extractionsByDocument);
        return "kiosk/documents";
    }

    @PostMapping("/intake/{id}/documents")
    public String uploadDocument(@PathVariable Long id,
                                 @RequestParam("file") MultipartFile file,
                                 @RequestParam(value = "description", required = false) String description,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        Patient patient = currentPatient(authentication);
        KioskIntake intake = intakeService.requireOwnedIntake(id, patient.getId());
        try {
            Document doc = documentService.uploadDocument(file, patient.getId(), null, null,
                    description, authentication.getName());
            try {
                extractionService.requestExtraction(doc.getId(), intake.getId());
            } catch (Exception e) {
                log.warn("Digitization request failed for document {}: {}",
                        doc.getId(), e.getClass().getSimpleName());
            }
            redirectAttributes.addFlashAttribute("successMessage",
                    "Document uploaded. Digitization status is shown on the review summary.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (IOException e) {
            log.warn("Document upload IO error: {}", e.getClass().getSimpleName());
            redirectAttributes.addFlashAttribute("errorMessage",
                    "The file could not be saved. Please try again.");
        }
        return "redirect:/kiosk/intake/" + id + "/documents";
    }

    // ---- Helpers ----

    private Patient currentPatient(Authentication authentication) {
        return intakeService.requirePatientForUser(currentUserId(authentication));
    }

    private Long currentUserId(Authentication authentication) {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AccessDeniedException("Account not found."));
        return user.getId();
    }

    /**
     * Patient-scoped document download. Verifies the document belongs to the
     * current patient before streaming the file (staff downloads use
     * /documents/{id}/download, which is role-gated separately).
     */
    @GetMapping("/document/{documentId}/download")
    public ResponseEntity<Resource> downloadOwnDocument(@PathVariable Long documentId,
                                                        Authentication authentication) {
        Patient patient = currentPatient(authentication);
        Document document = documentService.findByPatientId(patient.getId()).stream()
                .filter(doc -> doc.getId().equals(documentId))
                .findFirst()
                .orElseThrow(() -> new com.patientcase.common.ResourceNotFoundException(
                        "Document not found: " + documentId));

        Path filePath;
        try {
            filePath = documentService.resolveDownloadPath(document);
        } catch (IllegalArgumentException e) {
            log.warn("Blocked attempt to access document with invalid storage reference, id={}", documentId);
            return ResponseEntity.badRequest().build();
        }

        Resource resource = new PathResource(filePath);
        String contentType = document.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + document.getOriginalFilename() + "\"")
                .body(resource);
    }

    private boolean authenticate(String username, String password, HttpServletRequest request,
                                 HttpServletResponse response) {
        try {
            UsernamePasswordAuthenticationToken token =
                    new UsernamePasswordAuthenticationToken(username, password);
            token.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            Authentication authenticated = authenticationManager.authenticate(token);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authenticated);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);
            return true;
        } catch (AuthenticationException e) {
            log.warn("Post-registration auto-login failed for user '{}': {}",
                    username, e.getClass().getSimpleName());
            return false;
        }
    }
}