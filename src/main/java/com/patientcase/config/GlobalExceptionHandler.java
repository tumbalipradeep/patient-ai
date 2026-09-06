package com.patientcase.config;

import com.patientcase.common.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(ResourceNotFoundException ex, Model model) {
        model.addAttribute("errorMessage", "The requested resource was not found.");
        return "errors/404";
    }

    /** Unmatched URLs must be 404 (not 500) for authenticated users. */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNoRoute(Exception ex, Model model) {
        return handleNotFound(null, model);
    }

    /** Wrong HTTP method on a known URL must be 405 (not 500). */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public String handleMethodNotAllowed(Exception ex, Model model) {
        model.addAttribute("errorMessage", "This URL does not support the requested HTTP method.");
        return "errors/405";
    }

    /** Missing file part or oversized uploads must be 400 (not 500). */
    @ExceptionHandler(MissingServletRequestPartException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleMissingFile(Exception ex, Model model) {
        model.addAttribute("errorMessage", "A file is required to upload a document.");
        return "errors/400";
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleOversizeUpload(Exception ex, Model model) {
        model.addAttribute("errorMessage", "File exceeds the maximum allowed size of 10MB.");
        return "errors/400";
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleAccessDenied(AccessDeniedException ex, Model model) {
        model.addAttribute("errorMessage", "You do not have permission to access this resource.");
        return "errors/403";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGenericError(Exception ex, Model model) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        model.addAttribute("errorMessage", "An unexpected error occurred. Please try again later.");
        return "errors/500";
    }
}
