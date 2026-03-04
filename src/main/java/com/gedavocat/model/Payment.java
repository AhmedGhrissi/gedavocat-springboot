package com.gedavocat.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import com.gedavocat.listener.LABFTListener;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entité représentant un paiement (historique des transactions)
 * LAB-FT: Contrôles automatiques ACPR pour montants > 1000€
 */
@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_payment_user", columnList = "user_id"),
    @Index(name = "idx_payment_status", columnList = "status"),
    @Index(name = "idx_payment_payplug", columnList = "payplug_payment_id")
})
@EntityListeners(LABFTListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"user"})
@EqualsAndHashCode(exclude = {"user"})
public class Payment {
    
    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();
    
    @Column(name = "payplug_payment_id", length = 255, unique = true)
    private String paypluGPaymentId;
    
    @NotNull(message = "Le montant est obligatoire")
    @Positive(message = "Le montant doit être positif")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;
    
    @Column(length = 3)
    private String currency = "EUR";
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_plan", nullable = false, length = 20)
    private User.SubscriptionPlan subscriptionPlan;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "billing_period", nullable = false, length = 20)
    private BillingPeriod billingPeriod;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "paid_at")
    private LocalDateTime paidAt;
    
    // Énumération des statuts de paiement
    public enum PaymentStatus {
        PENDING("En attente"),
        PAID("Payé"),
        FAILED("Échec"),
        REFUNDED("Remboursé");
        
        private final String displayName;
        
        PaymentStatus(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    // Énumération des périodes de facturation
    public enum BillingPeriod {
        MONTHLY("Mensuel"),
        YEARLY("Annuel");
        
        private final String displayName;
        
        BillingPeriod(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    // Méthodes utilitaires
    public boolean isPending() {
        return status == PaymentStatus.PENDING;
    }
    
    public boolean isPaid() {
        return status == PaymentStatus.PAID;
    }
    
    public boolean hasFailed() {
        return status == PaymentStatus.FAILED;
    }
    
    public boolean isRefunded() {
        return status == PaymentStatus.REFUNDED;
    }
    
    public void markAsPaid() {
        this.status = PaymentStatus.PAID;
        this.paidAt = LocalDateTime.now();
    }
    
    public void markAsFailed() {
        this.status = PaymentStatus.FAILED;
    }
    
    public void markAsRefunded() {
        this.status = PaymentStatus.REFUNDED;
    }
    
    /**
     * Calcule le montant avec réduction annuelle
     */
    public BigDecimal getDiscountedAmount() {
        if (billingPeriod == BillingPeriod.YEARLY) {
            // Réduction de 20% sur l'abonnement annuel
            return amount.multiply(new BigDecimal("0.8"));
        }
        return amount;
    }
    
    /**
     * Retourne une description lisible du paiement
     */
    public String getDescription() {
        return String.format("Abonnement %s - %s", 
                subscriptionPlan.getDisplayName(), 
                billingPeriod.getDisplayName());
    }
    
    @PrePersist
    public void prePersist() {
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
        }
    }
}