package com.gedavocat.repository;

import com.gedavocat.model.Payment;
import com.gedavocat.model.Payment.PaymentStatus;
import com.gedavocat.model.User.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, String> {
    
    /**
     * Trouve les paiements d'un utilisateur
     */
    List<Payment> findByUserIdOrderByCreatedAtDesc(String userId);
    
    /**
     * Trouve un paiement par son ID PayPlug
     */
    Optional<Payment> findByPaypluGPaymentId(String paypluGPaymentId);
    
    /**
     * Trouve les paiements par statut
     */
    List<Payment> findByStatus(PaymentStatus status);
    
    /**
     * Trouve les paiements d'un plan d'abonnement
     */
    List<Payment> findBySubscriptionPlan(SubscriptionPlan plan);
    
    /**
     * Calcule le chiffre d'affaires total
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.status = 'PAID'")
    BigDecimal calculateTotalRevenue();
    
    /**
     * Calcule le chiffre d'affaires d'un utilisateur
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.user.id = :userId AND p.status = 'PAID'")
    BigDecimal calculateUserRevenue(@Param("userId") String userId);
    
    /**
     * Trouve les paiements d'une période
     */
    @Query("SELECT p FROM Payment p WHERE p.createdAt BETWEEN :startDate AND :endDate")
    List<Payment> findByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
    
    /**
     * Compte les paiements réussis d'un utilisateur
     */
    @Query("SELECT COUNT(p) FROM Payment p WHERE p.user.id = :userId AND p.status = 'PAID'")
    long countSuccessfulPaymentsByUserId(@Param("userId") String userId);
    
    /**
     * Trouve le dernier paiement réussi d'un utilisateur
     */
    @Query("SELECT p FROM Payment p WHERE p.user.id = :userId AND p.status = 'PAID' ORDER BY p.paidAt DESC")
    List<Payment> findLastSuccessfulPaymentByUserId(@Param("userId") String userId);
}