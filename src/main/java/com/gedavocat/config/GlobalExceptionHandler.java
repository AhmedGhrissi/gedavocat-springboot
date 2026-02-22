package com.gedavocat.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.HashMap;
import java.util.Map;

/**
 * Gestionnaire global d'exceptions — OWASP
 * Empêche la fuite de stack traces et informations internes.
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public Object handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Accès refusé: {} - IP: {}", request.getRequestURI(), getClientIp(request));
        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Accès refusé", "status", 403));
        }
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("status", "403 - Accès refusé");
        mav.addObject("message", "Vous n'avez pas les permissions nécessaires pour accéder à cette ressource.");
        mav.setStatus(HttpStatus.FORBIDDEN);
        return mav;
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public Object handleNotFound(NoHandlerFoundException ex, HttpServletRequest request) {
        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Ressource non trouvée", "status", 404));
        }
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("status", "404 - Page non trouvée");
        mav.addObject("message", "La page demandée n'existe pas.");
        mav.setStatus(HttpStatus.NOT_FOUND);
        return mav;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleValidationErrors(MethodArgumentNotValidException ex, HttpServletRequest request) {
        log.warn("Erreur de validation sur {} : {}", request.getRequestURI(), ex.getMessage());
        if (isApiRequest(request)) {
            Map<String, Object> body = new HashMap<>();
            body.put("status", 400);
            body.put("error", "Erreur de validation");
            Map<String, String> fieldErrors = new HashMap<>();
            for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
                fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
            }
            body.put("errors", fieldErrors);
            return ResponseEntity.badRequest().body(body);
        }
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("status", "400 - Données invalides");
        mav.addObject("message", "Les données soumises sont invalides. Veuillez vérifier votre saisie.");
        mav.setStatus(HttpStatus.BAD_REQUEST);
        return mav;
    }

    @ExceptionHandler(Exception.class)
    public Object handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Erreur non gérée sur {} : {}", request.getRequestURI(), ex.getMessage(), ex);
        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Erreur interne du serveur", "status", 500));
        }
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("status", "500 - Erreur interne");
        mav.addObject("message", "Une erreur inattendue est survenue. Veuillez réessayer ultérieurement.");
        mav.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        return mav;
    }

    private boolean isApiRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String accept = request.getHeader("Accept");
        String xRequestedWith = request.getHeader("X-Requested-With");
        return uri.startsWith("/api/") || uri.contains("/api/")
                || (accept != null && accept.contains("application/json"))
                || "XMLHttpRequest".equals(xRequestedWith);
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
