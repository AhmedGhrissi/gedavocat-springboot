package com.gedavocat.config;

import com.gedavocat.exception.BusinessValidationException;
import com.gedavocat.exception.ForbiddenAccessException;
import com.gedavocat.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
    public Object handleAccessDenied(AccessDeniedException ex, HttpServletRequest request, HttpServletResponse response) {
        log.warn("Accès refusé: {} - IP: {}", request.getRequestURI(), getClientIp(request));
        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Accès refusé", "status", 403));
        }
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("status", "403 - Accès refusé");
        mav.addObject("message", "Vous n'avez pas les permissions nécessaires pour accéder à cette ressource.");
        mav.setStatus(HttpStatus.FORBIDDEN);
        try { response.setStatus(HttpStatus.FORBIDDEN.value()); } catch (Exception ignored) {}
        return mav;
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public Object handleNotFound(NoHandlerFoundException ex, HttpServletRequest request, HttpServletResponse response) {
        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Ressource non trouvée", "status", 404));
        }
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("status", "404 - Page non trouvée");
        mav.addObject("message", "La page demandée n'existe pas.");
        mav.setStatus(HttpStatus.NOT_FOUND);
        try { response.setStatus(HttpStatus.NOT_FOUND.value()); } catch (Exception ignored) {}
        return mav;
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Object handleNoResource(NoResourceFoundException ex, HttpServletRequest request, HttpServletResponse response) {
        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Ressource non trouvée", "status", 404));
        }
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("status", "404 - Page non trouvée");
        mav.addObject("message", "La page demandée n'existe pas.");
        mav.setStatus(HttpStatus.NOT_FOUND);
        try { response.setStatus(HttpStatus.NOT_FOUND.value()); } catch (Exception ignored) {}
        return mav;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleValidationErrors(MethodArgumentNotValidException ex, HttpServletRequest request, HttpServletResponse response) {
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
        try { response.setStatus(HttpStatus.BAD_REQUEST.value()); } catch (Exception ignored) {}
        return mav;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public Object handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request, HttpServletResponse response) {
        log.warn("Ressource non trouvée: {} - {}", request.getRequestURI(), ex.getMessage());
        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", ex.getMessage(), "status", 404));
        }
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("status", "404 - Non trouvé");
        mav.addObject("message", ex.getMessage());
        mav.setStatus(HttpStatus.NOT_FOUND);
        try { response.setStatus(HttpStatus.NOT_FOUND.value()); } catch (Exception ignored) {}
        return mav;
    }

    @ExceptionHandler(ForbiddenAccessException.class)
    public Object handleForbiddenAccess(ForbiddenAccessException ex, HttpServletRequest request, HttpServletResponse response) {
        log.warn("Accès interdit: {} - {} - IP: {}", request.getRequestURI(), ex.getMessage(), getClientIp(request));
        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", ex.getMessage(), "status", 403));
        }
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("status", "403 - Accès interdit");
        mav.addObject("message", ex.getMessage());
        mav.setStatus(HttpStatus.FORBIDDEN);
        try { response.setStatus(HttpStatus.FORBIDDEN.value()); } catch (Exception ignored) {}
        return mav;
    }

    @ExceptionHandler(BusinessValidationException.class)
    public Object handleBusinessValidation(BusinessValidationException ex, HttpServletRequest request, HttpServletResponse response) {
        log.warn("Erreur métier: {} - {}", request.getRequestURI(), ex.getMessage());
        if (isApiRequest(request)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", ex.getMessage(), "status", 400));
        }
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("status", "400 - Erreur de validation");
        mav.addObject("message", ex.getMessage());
        mav.setStatus(HttpStatus.BAD_REQUEST);
        try { response.setStatus(HttpStatus.BAD_REQUEST.value()); } catch (Exception ignored) {}
        return mav;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Object handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest request, HttpServletResponse response) {
        log.warn("Paramètre manquant sur {} : {}", request.getRequestURI(), ex.getMessage());
        if (isApiRequest(request)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Paramètre requis manquant: " + ex.getParameterName(), "status", 400));
        }
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("status", "400 - Paramètre manquant");
        mav.addObject("message", "Un paramètre requis est manquant.");
        mav.setStatus(HttpStatus.BAD_REQUEST);
        try { response.setStatus(HttpStatus.BAD_REQUEST.value()); } catch (Exception ignored) {}
        return mav;
    }

    @ExceptionHandler(Exception.class)
    public Object handleGenericException(Exception ex, HttpServletRequest request, HttpServletResponse response) {
        log.error("Erreur non gérée sur {} : {}", request.getRequestURI(), ex.getMessage(), ex);
        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Erreur interne du serveur", "status", 500));
        }
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("status", "500 - Erreur interne");
        mav.addObject("message", "Une erreur inattendue est survenue. Veuillez réessayer ultérieurement.");
        mav.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        try { response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value()); } catch (Exception ignored) {}
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

    /**
     * SEC-HEADER FIX : ne pas utiliser X-Forwarded-For directement car il peut être forgé.
     * En production, le reverse proxy (nginx) gère les headers de forwarding
     * et Spring Boot utilise server.forward-headers-strategy=NATIVE.
     * Ici on se contente de request.getRemoteAddr() qui est l'IP vue par le serveur.
     */
    private String getClientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}