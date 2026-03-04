package com.gedavocat.listener;

import com.gedavocat.model.Client;
import com.gedavocat.model.LABFTCheck;
import com.gedavocat.model.Payment;
import com.gedavocat.repository.LABFTCheckRepository;
import com.gedavocat.service.LABFTService;
import com.gedavocat.service.LABFTService.RiskLevel;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Listener JPA pour déclencher automatiquement les contrôles LAB-FT
 * 
 * Conformité ACPR:
 * - Contrôle automatique lors de la création d'un client
 * - Contrôle automatique pour les paiements > 1000€
 * - Traçabilité complète dans labft_checks
 * 
 * @author DPO Marie DUBOIS
 */
@Slf4j
@Component
public class LABFTListener {

    private static LABFTService labftService;
    private static LABFTCheckRepository labftCheckRepository;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public void setLABFTService(LABFTService service) {
        LABFTListener.labftService = service;
    }

    @Autowired
    public void setLABFTCheckRepository(LABFTCheckRepository repository) {
        LABFTListener.labftCheckRepository = repository;
    }

    /**
     * Déclenché après la création d'un nouveau client
     * Effectue automatiquement le contrôle de vigilance client initial
     */
    @PostPersist
    public void onClientCreated(Object entity) {
        if (!(entity instanceof Client)) {
            return;
        }

        Client client = (Client) entity;
        
        try {
            log.info("LAB-FT: Contrôle automatique nouveau client: {}", client.getId());
            
            // 1. Scoring de risque
            RiskLevel riskLevel = labftService.calculateClientRiskScore(client);
            
            // 2. Contrôle PEP
            boolean isPEP = labftService.isPoliticallyExposedPerson(client);
            if (isPEP) {
                LABFTCheck pepCheck = new LABFTCheck();
                pepCheck.setClient(client);
                pepCheck.setFirm(client.getFirm());
                pepCheck.setCheckType(LABFTCheck.CheckType.PEP_CHECK);
                pepCheck.setCheckResult(LABFTCheck.CheckResult.ALERTE);
                pepCheck.setPepDetected(true);
                pepCheck.setAutomaticCheck(true);
                pepCheck.setCheckedBy("SYSTEM_AUTO_PEP");
                pepCheck.setComments("Personne Politiquement Exposée détectée - Vigilance renforcée requise");
                labftCheckRepository.save(pepCheck);
                
                log.warn("LAB-FT ALERTE: Client PEP détecté: {}", client.getId());
            }
            
            // 3. Contrôle sanctions
            boolean isSanctioned = labftService.isOnSanctionsList(client);
            if (isSanctioned) {
                LABFTCheck sanctionsCheck = new LABFTCheck();
                sanctionsCheck.setClient(client);
                sanctionsCheck.setFirm(client.getFirm());
                sanctionsCheck.setCheckType(LABFTCheck.CheckType.SANCTIONS_CHECK);
                sanctionsCheck.setCheckResult(LABFTCheck.CheckResult.BLOQUE);
                sanctionsCheck.setSanctionsDetected(true);
                sanctionsCheck.setAutomaticCheck(true);
                sanctionsCheck.setCheckedBy("SYSTEM_AUTO_SANCTIONS");
                sanctionsCheck.setComments("Client présent sur liste de sanctions - BLOQUÉ");
                labftCheckRepository.save(sanctionsCheck);
                
                log.error("LAB-FT CRITIQUE: Client sanctionné détecté: {} - BLOQUÉ", client.getId());
            }
            
            // 4. Vigilance initiale
            LABFTCheck vigilanceCheck = new LABFTCheck();
            vigilanceCheck.setClient(client);
            vigilanceCheck.setFirm(client.getFirm());
            vigilanceCheck.setCheckType(LABFTCheck.CheckType.VIGILANCE_CLIENT);
            vigilanceCheck.setRiskLevel(convertRiskLevel(riskLevel));
            vigilanceCheck.setCheckResult(determineCheckResult(riskLevel, isPEP, isSanctioned));
            vigilanceCheck.setPepDetected(isPEP);
            vigilanceCheck.setSanctionsDetected(isSanctioned);
            vigilanceCheck.setAutomaticCheck(true);
            vigilanceCheck.setCheckedBy("SYSTEM_AUTO_VIGILANCE");
            vigilanceCheck.setComments("Contrôle de vigilance initiale automatique");
            labftCheckRepository.save(vigilanceCheck);
            
            log.info("LAB-FT: Contrôles automatiques terminés pour client: {}, Risque: {}", 
                    client.getId(), riskLevel);
            
        } catch (Exception e) {
            log.error("LAB-FT: Erreur lors du contrôle automatique client {}: {}", 
                    client.getId(), e.getMessage(), e);
        }
    }

    /**
     * Déclenché après la création d'un paiement
     * Effectue automatiquement le contrôle si montant > 1000€
     */
    @PostPersist
    public void onPaymentCreated(Object entity) {
        if (!(entity instanceof Payment)) {
            return;
        }

        Payment payment = (Payment) entity;
        
        try {
            BigDecimal amount = payment.getAmount();
            
            // Seuil de vigilance ACPR: 1000€
            if (amount.compareTo(new BigDecimal("1000.00")) <= 0) {
                return; // Pas de contrôle nécessaire
            }
            
            log.info("LAB-FT: Contrôle automatique paiement >1000€: {}, Montant: {}€", 
                    payment.getId(), amount);
            
            // Récupérer le client associé via l'utilisateur
            // Note: En production, récupérer via payment.getInvoice().getClient()
            
            List<String> alertReasons = new ArrayList<>();
            boolean isSuspicious = false;
            
            // 1. Vérification montant
            if (amount.compareTo(new BigDecimal("10000.00")) > 0) {
                alertReasons.add("Montant > 10000€ - Déclaration TRACFIN requise");
                isSuspicious = true;
            }
            
            // 2. Vérification fractionnement (simulation)
            if (amount.compareTo(new BigDecimal("8000.00")) > 0 && 
                amount.compareTo(new BigDecimal("9900.00")) < 0) {
                alertReasons.add("Montant proche du seuil 10000€ - Possible fractionnement");
                isSuspicious = true;
            }
            
            // Enregistrement du contrôle
            LABFTCheck transactionCheck = new LABFTCheck();
            transactionCheck.setPayment(payment);
            transactionCheck.setFirm(payment.getUser().getFirm());
            transactionCheck.setCheckType(LABFTCheck.CheckType.TRANSACTION_ANALYSIS);
            transactionCheck.setAmount(amount);
            transactionCheck.setTransactionType(payment.getSubscriptionPlan().toString());
            transactionCheck.setCheckResult(isSuspicious ? 
                LABFTCheck.CheckResult.SUSPECT : LABFTCheck.CheckResult.CONFORME);
            
            if (isSuspicious) {
                try {
                    transactionCheck.setAlertReasons(objectMapper.writeValueAsString(alertReasons));
                } catch (Exception e) {
                    transactionCheck.setAlertReasons(String.join(", ", alertReasons));
                }
                transactionCheck.setTracfinDeclared(amount.compareTo(new BigDecimal("10000.00")) > 0);
                if (transactionCheck.isTracfinDeclared()) {
                    transactionCheck.setTracfinReference("TRACFIN-" + System.currentTimeMillis());
                }
            }
            
            transactionCheck.setAutomaticCheck(true);
            transactionCheck.setCheckedBy("SYSTEM_AUTO_TRANSACTION");
            transactionCheck.setComments("Contrôle automatique paiement > 1000€");
            
            labftCheckRepository.save(transactionCheck);
            
            if (isSuspicious) {
                log.warn("LAB-FT ALERTE: Transaction suspecte: {}, Montant: {}€, Raisons: {}", 
                        payment.getId(), amount, alertReasons);
            } else {
                log.info("LAB-FT: Transaction conforme: {}, Montant: {}€", 
                        payment.getId(), amount);
            }
            
        } catch (Exception e) {
            log.error("LAB-FT: Erreur lors du contrôle automatique paiement {}: {}", 
                    payment.getId(), e.getMessage(), e);
        }
    }

    // =============================================================================
    // Méthodes utilitaires
    // =============================================================================

    private LABFTCheck.RiskLevel convertRiskLevel(RiskLevel riskLevel) {
        return switch (riskLevel) {
            case FAIBLE -> LABFTCheck.RiskLevel.FAIBLE;
            case MODERE -> LABFTCheck.RiskLevel.MODERE;
            case ELEVE -> LABFTCheck.RiskLevel.ELEVE;
            case CRITIQUE -> LABFTCheck.RiskLevel.CRITIQUE;
        };
    }

    private LABFTCheck.CheckResult determineCheckResult(RiskLevel riskLevel, boolean isPEP, boolean isSanctioned) {
        if (isSanctioned) {
            return LABFTCheck.CheckResult.BLOQUE;
        }
        if (riskLevel == RiskLevel.CRITIQUE || isPEP) {
            return LABFTCheck.CheckResult.SUSPECT;
        }
        if (riskLevel == RiskLevel.ELEVE) {
            return LABFTCheck.CheckResult.ALERTE;
        }
        return LABFTCheck.CheckResult.CONFORME;
    }
}
