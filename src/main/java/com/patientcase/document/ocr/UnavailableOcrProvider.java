package com.patientcase.document.ocr;

import org.springframework.stereotype.Component;

/**
 * Default OCR provider used when no real digitization engine is configured.
 *
 * It honestly reports that document digitization is unavailable rather than
 * fabricating extraction results. A real provider (e.g. Tesseract-backed or a
 * vendor OCR service) can be added behind the same OcrProvider contract.
 */
@Component("unavailableOcrProvider")
public class UnavailableOcrProvider implements OcrProvider {

    @Override
    public String providerId() {
        return "unavailable";
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public DocumentIntelligence extract(byte[] content, String contentType, String originalFilename) {
        return DocumentIntelligence.unsupported();
    }
}