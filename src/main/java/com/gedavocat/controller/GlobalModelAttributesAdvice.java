package com.gedavocat.controller;

import com.gedavocat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Expose des attributs communs à tous les templates (layout.html inclus).
 * Utilisation : th:if="${userPlan == 'CABINET_PLUS'}" etc.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAttributesAdvice {

    private final UserRepository userRepository;

    @ModelAttribute("userPlan")
    public String userPlan(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        return userRepository.findByEmail(authentication.getName())
                .map(u -> u.getSubscriptionPlan() != null ? u.getSubscriptionPlan().name() : null)
                .orElse(null);
    }
}
