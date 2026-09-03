package com.patientcase.document;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;

@Controller
@RequestMapping("/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public String listDocuments(Model model) {
        List<Document> documents = documentService.findAll();
        model.addAttribute("documents", documents);
        return "documents/list";
    }

    @PostMapping("/upload")
    public String uploadDocument(@RequestParam("file") MultipartFile file,
                                  @RequestParam(value = "patientId", required = false) Long patientId,
                                  @RequestParam(value = "caseId", required = false) Long caseId,
                                  @RequestParam(value = "encounterId", required = false) Long encounterId,
                                  @RequestParam(value = "description", required = false) String description,
                                  Authentication authentication,
                                  RedirectAttributes redirectAttributes) {
        try {
            documentService.uploadDocument(file, patientId, caseId, encounterId, description,
                    authentication.getName());
            redirectAttributes.addFlashAttribute("successMessage", "Document uploaded successfully.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to upload document. Please try again.");
        }

        // Redirect back to appropriate page
        if (patientId != null) return "redirect:/patients/" + patientId;
        if (caseId != null) return "redirect:/cases/" + caseId;
        if (encounterId != null) return "redirect:/encounters/" + encounterId;
        return "redirect:/documents";
    }
}
