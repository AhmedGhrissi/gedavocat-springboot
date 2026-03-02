package com.gedavocat.repository;

import com.gedavocat.model.Firm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository pour l'entité Firm (Cabinet d'avocats)
 */
@Repository
public interface FirmRepository extends JpaRepository<Firm, String> {
    
    /**
     * Trouver un cabinet par son nom
     */
    Optional<Firm> findByName(String name);
    
    /**
     * Vérifier si un cabinet existe par son SIREN
     */
    boolean existsBySiren(String siren);
    
    /**
     * Trouver un cabinet par son identifiant Stripe
     */
    Optional<Firm> findByStripeCustomerId(String stripeCustomerId);
}
