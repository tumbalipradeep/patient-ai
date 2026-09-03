package com.patientcase.document;

import com.patientcase.common.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@Controller
@RequestMapping("/documents")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

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

    /** Patient-scoped document list (linked from patient profile) */
    @GetMapping("/patient/{patientId}")
    public String listByPatient(@PathVariable Long patientId, Model model) {
        List<Document> documents = documentService.findByPatientId(patientId);
        model.addAttribute("documents", documents);
        model.addAttribute("contextLabel", "Patient");
        model.addAttribute("uploadPatientId", patientId);
        return "documents/list";
    }

    /** Case-scoped document list (linked from case view) */
    @GetMapping("/case/{caseId}")
    public String listByCase(@PathVariable Long caseId, Model model) {
        List<Document> documents = documentService.findByCaseId(caseId);
        model.addAttribute("documents", documents);
        model.addAttribute("contextLabel", "Case");
        model.addAttribute("uploadCaseId", caseId);
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

        if (encounterId != null) return "redirect:/encounters/" + encounterId;
        if (caseId != null) return "redirect:/documents/case/" + caseId;
        if (patientId != null) return "redirect:/documents/patient/" + patientId;
        return "redirect:/documents";
    }

    /**
     * Secure document download.
     * Validates the document exists, resolves a safe path, and streams the file.
     * Access is implicitly controlled by Spring Security (authenticated users only).
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadDocument(@PathVariable Long id) {
        Document document = documentService.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + id));

        Path filePath;
        try {
            filePath = documentService.resolveDownloadPath(document);
        } catch (IllegalArgumentException e) {
            log.warn("Blocked attempt to access document with invalid storage reference, id={}", id);
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
}
