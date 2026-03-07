package com.gedavocat.service;

import com.gedavocat.model.Firm;
import com.gedavocat.model.User;
import com.gedavocat.repository.FirmRepository;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Service de gestion des cabinets d'avocats (Firms)
 * 
 * Fonctionnalités:
 * - Création de cabinets
 * - Gestion des abonnements
 * - Vérification des quotas (avocats, clients)
 * - Isolation multi-tenant
 * 
 * Référence: docs/RAPPORT_AUDIT_SECURITE_Phase1.md §6.1 VULN-01
 */
@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class FirmService {

    private final FirmRepository firmRepository;
    private final UserRepository userRepository;
    private final ClientRepository clientRepository;

    /**
     * Créer un nouveau cabinet d'avocats
     */
    @Transactional
    public Firm createFirm(Firm firm) {
        if (firm.getId() == null || firm.getId().isEmpty()) {
            firm.setId(UUID.randomUUID().toString());
        }
        
        // Vérifier l'unicité du SIREN
        if (firm.getSiren() != null && firmRepository.existsBySiren(firm.getSiren())) {
            throw new IllegalArgumentException("Un cabinet avec ce SIREN existe déjà");
        }
        
        log.info("Creating new firm: {} (plan: {})", firm.getName(), firm.getSubscriptionPlan());
        return firmRepository.save(firm);
    }

    /**
     * Trouver un cabinet par son ID
     */
    public Optional<Firm> findById(String firmId) {
        return firmRepository.findById(firmId);
    }

    /**
     * Mettre à jour un cabinet
     */
    @Transactional
    public Firm updateFirm(Firm firm) {
        log.info("Updating firm: {}", firm.getId());
        return firmRepository.save(firm);
    }

    /**
     * Activer l'abonnement d'un cabinet
     */
    @Transactional
    public void activateSubscription(String firmId, Firm.SubscriptionPlan plan, LocalDateTime endsAt) {
        Firm firm = firmRepository.findById(firmId)
            .orElseThrow(() -> new IllegalArgumentException("Cabinet non trouvé: " + firmId));
        
        firm.setSubscriptionPlan(plan);
        firm.setSubscriptionStatus(Firm.SubscriptionStatus.ACTIVE);
        firm.setSubscriptionStartsAt(LocalDateTime.now());
        firm.setSubscriptionEndsAt(endsAt);
        
        // Mettre à jour les quotas selon le plan
        firm.setMaxLawyers(plan.getMaxLawyers());
        firm.setMaxClients(plan.getMaxClients());
        
        firmRepository.save(firm);
        log.info("Activated subscription for firm {} - Plan: {}", firmId, plan);
    }

    /**
     * Annuler l'abonnement d'un cabinet
     */
    @Transactional
    public void cancelSubscription(String firmId) {
        Firm firm = firmRepository.findById(firmId)
            .orElseThrow(() -> new IllegalArgumentException("Cabinet non trouvé: " + firmId));
        
        firm.setSubscriptionStatus(Firm.SubscriptionStatus.CANCELLED);
        firmRepository.save(firm);
        log.info("Cancelled subscription for firm {}", firmId);
    }

    /**
     * Vérifier si un cabinet peut ajouter un nouvel avocat
     */
    public boolean canAddMoreLawyers(String firmId) {
        Firm firm = firmRepository.findById(firmId)
            .orElseThrow(() -> new IllegalArgumentException("Cabinet non trouvé: " + firmId));
        
        // Compter le nombre d'avocats actuels dans ce cabinet
        long lawyersCount = userRepository.countByFirmIdAndRoleIn(
            firmId, 
            java.util.Arrays.asList(User.UserRole.LAWYER, User.UserRole.LAWYER_SECONDARY, User.UserRole.AVOCAT_ADMIN)
        );
        
        boolean canAdd = firm.canAddMoreLawyers((int) lawyersCount);
        log.debug("Cabinet {} - Avocats: {}/{} (can add: {})", 
                 firmId, lawyersCount, firm.getMaxLawyers(), canAdd);
        return canAdd;
    }

    /**
     * Vérifier si un cabinet peut ajouter un nouveau client
     */
    public boolean canAddMoreClients(String firmId) {
        Firm firm = firmRepository.findById(firmId)
            .orElseThrow(() -> new IllegalArgumentException("Cabinet non trouvé: " + firmId));
        
        // Compter le nombre de clients actuels dans ce cabinet
        long clientsCount = clientRepository.countByFirmId(firmId);
        
        boolean canAdd = firm.canAddMoreClients((int) clientsCount);
        log.debug("Cabinet {} - Clients: {}/{} (can add: {})", 
                 firmId, clientsCount, firm.getMaxClients(), canAdd);
        return canAdd;
    }

    /**
     * Vérifier si l'abonnement d'un cabinet est actif
     */
    public boolean hasActiveSubscription(String firmId) {
        return firmRepository.findById(firmId)
            .map(Firm::hasActiveSubscription)
            .orElse(false);
    }

    /**
     * Vérifier et désactiver les abonnements expirés
     * (À exécuter par un cron job quotidien)
     */
    @Transactional
    public void checkExpiredSubscriptions() {
        firmRepository.findAll().forEach(firm -> {
            if (firm.isSubscriptionExpired() 
                    && firm.getSubscriptionStatus() == Firm.SubscriptionStatus.ACTIVE) {
                firm.setSubscriptionStatus(Firm.SubscriptionStatus.INACTIVE);
                firmRepository.save(firm);
                log.warn("Subscription expired for firm: {} ({})", firm.getId(), firm.getName());
            }
        });
    }
}
