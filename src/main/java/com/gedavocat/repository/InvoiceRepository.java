package com.gedavocat.repository;

import com.gedavocat.model.Invoice;
import com.gedavocat.model.Invoice.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository pour la gestion des factures
 */
@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, String> {

    /**
     * Trouve toutes les factures d'un client
     */
    List<Invoice> findByClientId(String clientId);

    /**
     * Trouve toutes les factures d'un client par statut
     */
    List<Invoice> findByClientIdAndStatus(String clientId, InvoiceStatus status);

    /**
     * Trouve une facture par son numéro
     */
    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    /**
     * Trouve toutes les factures d'un avocat (via les clients)
     */
    @Query("SELECT i FROM Invoice i WHERE i.client.lawyer.id = :lawyerId")
    List<Invoice> findByLawyerId(@Param("lawyerId") String lawyerId);

    /**
     * Trouve toutes les factures d'un avocat avec un statut spécifique
     */
    @Query("SELECT i FROM Invoice i WHERE i.client.lawyer.id = :lawyerId AND i.status = :status")
    List<Invoice> findByLawyerIdAndStatus(@Param("lawyerId") String lawyerId, @Param("status") InvoiceStatus status);

    /**
     * Trouve toutes les factures en retard
     */
    @Query("SELECT i FROM Invoice i WHERE i.status = 'SENT' AND i.dueDate < :currentDate")
    List<Invoice> findOverdueInvoices(@Param("currentDate") LocalDate currentDate);

    /**
     * Trouve toutes les factures en retard d'un avocat
     */
    @Query("SELECT i FROM Invoice i WHERE i.client.lawyer.id = :lawyerId AND i.status = 'SENT' AND i.dueDate < :currentDate")
    List<Invoice> findOverdueInvoicesByLawyer(@Param("lawyerId") String lawyerId, @Param("currentDate") LocalDate currentDate);

    /**
     * Trouve toutes les factures entre deux dates
     */
    @Query("SELECT i FROM Invoice i WHERE i.invoiceDate BETWEEN :startDate AND :endDate")
    List<Invoice> findByInvoiceDateBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * Trouve toutes les factures d'un avocat entre deux dates
     */
    @Query("SELECT i FROM Invoice i WHERE i.client.lawyer.id = :lawyerId AND i.invoiceDate BETWEEN :startDate AND :endDate")
    List<Invoice> findByLawyerIdAndInvoiceDateBetween(@Param("lawyerId") String lawyerId,
                                                       @Param("startDate") LocalDate startDate,
                                                       @Param("endDate") LocalDate endDate);

    /**
     * Compte le nombre de factures d'un client
     */
    long countByClientId(String clientId);

    /**
     * Vérifie si un numéro de facture existe déjà
     */
    boolean existsByInvoiceNumber(String invoiceNumber);

    @Query("SELECT i FROM Invoice i " +
            "LEFT JOIN FETCH i.items " +
            "LEFT JOIN FETCH i.client " +
            "WHERE i.id = :id")
     Optional<Invoice> findByIdWithDetails(@Param("id") String id);
}
