package com.patientcase.kiosk;

import com.patientcase.document.Document;
import com.patientcase.document.DocumentExtraction;
import com.patientcase.document.DocumentService;
import com.patientcase.document.OcrExtractionService;
import com.patientcase.encounter.Encounter;
import org.hibernate.Hibernate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Physician review queue for patient self-service intakes.
 * Accessible to ADMIN, DOCTOR, NURSE (SecurityConfig /intakes/**).
 *
 * GET  /intakes                 — pending submissions (triage queue)
 * GET  /intakes/{id}            — full review page (draft, red flags, AYUSH, documents)
 * POST /intakes/{id}/accept     — accept into a clinical case + encounter
 * POST /intakes/{id}/reject     — reject with optional note
 */
@Controller
@RequestMapping("/intakes")
public class IntakeReviewController {

    private final KioskIntakeService intakeService;
    private final DocumentService documentService;
    private final OcrExtractionService extractionService;

    public IntakeReviewController(KioskIntakeService intakeService,
                                  DocumentService documentService,
                                  OcrExtractionService extractionService) {
        this.intakeService = intakeService;
        this.documentService = documentService;
        this.extractionService = extractionService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public String reviewQueue(Model model) {
        List<KioskIntake> intakes = intakeService.findSubmissionsForReview();
        List<ReviewQueueItem> items = intakes.stream().map(intake -> {
            List<RedFlag> flags = intakeService.getRedFlagsForIntake(intake.getId());
            ReviewQueueItem item = new ReviewQueueItem();
            item.setIntake(intake);
            item.setFlagCount(flags.size());
            item.setUrgent(flags.stream().anyMatch(RedFlag::isUrgent));
            item.setChiefComplaint(intakeService.getDraftForReview(intake.getId()) != null
                    ? intakeService.getDraftForReview(intake.getId()).getChiefComplaint() : null);
            return item;
        }).toList();
        items.forEach(item -> Hibernate.initialize(item.getIntake().getPatient()));
        model.addAttribute("queueItems", items);
        return "kiosk/intakes/review-queue";
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public String reviewPage(@PathVariable Long id, Model model) {
        KioskIntake intake = intakeService.requireIntakeForReview(id);
        Hibernate.initialize(intake.getPatient());
        List<Document> documents = intakeService.getDocumentsForIntake(id);
        Map<Long, DocumentExtraction> extractionsByDocument = new HashMap<>();
        for (Document doc : documents) {
            extractionService.findByDocumentId(doc.getId())
                    .ifPresent(ex -> extractionsByDocument.put(doc.getId(), ex));
        }
        model.addAttribute("intake", intake);
        model.addAttribute("patient", intake.getPatient());
        model.addAttribute("draft", intakeService.getDraftForReview(id));
        model.addAttribute("redFlags", intakeService.getRedFlagsForIntake(id));
        model.addAttribute("ayush", intakeService.getAyush(id));
        model.addAttribute("documents", documents);
        model.addAttribute("extractionsByDocument", extractionsByDocument);
        return "kiosk/intakes/review";
    }

    @PostMapping("/{id}/accept")
    public String accept(@PathVariable Long id,
                         @RequestParam(value = "notes", required = false) String notes,
                         Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        try {
            Encounter encounter = intakeService.acceptIntake(id, authentication.getName(), notes);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Intake accepted. Encounter " + encounter.getId()
                            + " created and assigned to you.");
            return "redirect:/encounters/" + encounter.getId();
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/intakes";
        }
    }

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable Long id,
                         @RequestParam(value = "notes", required = false) String notes,
                         Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        try {
            intakeService.rejectIntake(id, authentication.getName(), notes);
            redirectAttributes.addFlashAttribute("infoMessage", "Intake rejected.");
            return "redirect:/intakes";
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/intakes";
        }
    }

    /** Lightweight view model for the queue rows (avoids service calls in the template). */
    public static class ReviewQueueItem {
        private KioskIntake intake;
        private int flagCount;
        private boolean urgent;
        private String chiefComplaint;

        public KioskIntake getIntake() { return intake; }
        public void setIntake(KioskIntake intake) { this.intake = intake; }
        public int getFlagCount() { return flagCount; }
        public void setFlagCount(int flagCount) { this.flagCount = flagCount; }
        public boolean isUrgent() { return urgent; }
        public void setUrgent(boolean urgent) { this.urgent = urgent; }
        public String getChiefComplaint() { return chiefComplaint; }
        public void setChiefComplaint(String chiefComplaint) { this.chiefComplaint = chiefComplaint; }
    }
}