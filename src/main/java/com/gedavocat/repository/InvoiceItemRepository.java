package com.gedavocat.repository;

import com.gedavocat.model.InvoiceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository pour la gestion des lignes de facture
 */
@Repository
public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, String> {
    
    /**
     * Trouve toutes les lignes d'une facture
     */
    List<InvoiceItem> findByInvoiceIdOrderByDisplayOrderAsc(String invoiceId);
    
    /**
     * Supprime toutes les lignes d'une facture
     */
    void deleteByInvoiceId(String invoiceId);
}
