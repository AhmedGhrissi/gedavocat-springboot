package com.gedavocat.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.ToString;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entité représentant une facture liée à un client
 */
@Entity
@Table(name = "invoices", indexes = {
    @Index(name = "idx_invoice_client_id", columnList = "client_id"),
    @Index(name = "idx_invoice_number", columnList = "invoice_number", unique = true),
    @Index(name = "idx_invoice_status", columnList = "status"),
    @Index(name = "idx_invoice_date", columnList = "invoice_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"client", "items"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Invoice {
    
    @Id
    @Column(length = 36)
    @EqualsAndHashCode.Include
    private String id;

    @Version
    @Column(name = "entity_version")
    private Long entityVersion;
    
    @NotNull(message = "Le numéro de facture est obligatoire")
    @Column(name = "invoice_number", nullable = false, unique = true, length = 50)
    private String invoiceNumber;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    @NotNull(message = "Le client est obligatoire")
    @JsonIgnore
    private Client client;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id")
    @JsonIgnore
    private Case caseEntity;
    
    // MULTI-TENANT: Lien vers le cabinet (obligatoire en base)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "firm_id", nullable = false)
    @JsonIgnore
    private Firm firm;
    
    @Column(name = "issue_date")
    private LocalDate invoiceDate = LocalDate.now();
    
    // Legacy column synchronized with issue_date for backward compatibility
    @Column(name = "invoice_date")
    private LocalDate legacyInvoiceDate;
    
    @Column(name = "due_date")
    private LocalDate dueDate;
    
    @Column(name = "paid_date")
    private LocalDate paidDate;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private InvoiceStatus status = InvoiceStatus.DRAFT;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 50)
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;
    
    @Column(name = "subtotal_amount", precision = 10, scale = 2)
    private BigDecimal subtotalAmount = BigDecimal.ZERO;
    
    @Column(name = "tax_amount", precision = 10, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;
    
    @Column(name = "total_amount", precision = 10, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;
    
    // Legacy columns - keep synchronized with new column names
    @Column(name = "total_ht", precision = 10, scale = 2)
    private BigDecimal totalHT = BigDecimal.ZERO;
    
    @Column(name = "total_tva", precision = 10, scale = 2)
    private BigDecimal totalTVA = BigDecimal.ZERO;
    
    @Column(name = "total_ttc", precision = 10, scale = 2)
    private BigDecimal totalTTC = BigDecimal.ZERO;
    
    @PositiveOrZero(message = "Le montant payé doit être positif ou nul")
    @Column(name = "paid_amount", precision = 10, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;
    
    @Column(length = 3)
    private String currency = "EUR";
    
    @Column(columnDefinition = "TEXT")
    private String notes;
    
    @Column(name = "payment_method", length = 50)
    private String paymentMethod;
    
    @Column(name = "document_url", length = 500)
    @JsonIgnore
    private String documentUrl;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // Relation avec les lignes de facture
    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InvoiceItem> items = new ArrayList<>();
    
    // Énumération des statuts de facture
    public enum InvoiceStatus {
        DRAFT("Brouillon"),
        SENT("Envoyée"),
        PAID("Payée"),
        OVERDUE("En retard"),
        CANCELLED("Annulée");
        
        private final String displayName;
        
        InvoiceStatus(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    // Énumération des statuts de paiement
    public enum PaymentStatus {
        UNPAID("Non payée"),
        PARTIAL("Partiellement payée"),
        PAID("Payée"),
        REFUNDED("Remboursée");
        
        private final String displayName;
        
        PaymentStatus(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    // Méthodes utilitaires
    @PrePersist
    public void prePersist() {
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
        }
        if (invoiceDate == null) {
            invoiceDate = LocalDate.now();
        }
        // Synchronize legacy invoice_date column with issue_date
        this.legacyInvoiceDate = this.invoiceDate;
        
        // Synchronize legacy amount columns with new columns
        syncAmountColumns();
        
        if (status == InvoiceStatus.SENT && dueDate == null) {
            dueDate = invoiceDate.plusDays(30); // Échéance par défaut : 30 jours
        }
    }
    
    @PreUpdate
    public void preUpdate() {
        // Keep legacy invoice_date synchronized on updates
        this.legacyInvoiceDate = this.invoiceDate;
        
        // Synchronize legacy amount columns with new columns
        syncAmountColumns();
    }
    
    /**
     * Synchronize legacy columns (total_ht, total_tva, total_ttc) 
     * with new columns (subtotal_amount, tax_amount, total_amount)
     */
    private void syncAmountColumns() {
        // Sync subtotal_amount <-> total_ht
        if (subtotalAmount != null && totalHT == null) {
            this.totalHT = this.subtotalAmount;
        } else if (totalHT != null && subtotalAmount == null) {
            this.subtotalAmount = this.totalHT;
        } else if (subtotalAmount != null) {
            this.totalHT = this.subtotalAmount;
        }
        
        // Sync tax_amount <-> total_tva
        if (taxAmount != null && totalTVA == null) {
            this.totalTVA = this.taxAmount;
        } else if (totalTVA != null && taxAmount == null) {
            this.taxAmount = this.totalTVA;
        } else if (taxAmount != null) {
            this.totalTVA = this.taxAmount;
        }
        
        // Sync total_amount <-> total_ttc
        if (totalAmount != null && totalTTC == null) {
            this.totalTTC = this.totalAmount;
        } else if (totalTTC != null && totalAmount == null) {
            this.totalAmount = this.totalTTC;
        } else if (totalAmount != null) {
            this.totalTTC = this.totalAmount;
        }
        
        // Ensure all amounts are initialized
        if (totalHT == null) totalHT = BigDecimal.ZERO;
        if (totalTVA == null) totalTVA = BigDecimal.ZERO;
        if (totalTTC == null) totalTTC = BigDecimal.ZERO;
        if (subtotalAmount == null) subtotalAmount = BigDecimal.ZERO;
        if (taxAmount == null) taxAmount = BigDecimal.ZERO;
        if (totalAmount == null) totalAmount = BigDecimal.ZERO;
    }
    
    public boolean isOverdue() {
        return status == InvoiceStatus.SENT 
            && dueDate != null 
            && LocalDate.now().isAfter(dueDate);
    }
    
    public boolean isPaid() {
        return status == InvoiceStatus.PAID;
    }
    
    // Méthodes pour gérer les lignes de facture
    public void addItem(InvoiceItem item) {
        items.add(item);
        item.setInvoice(this);
    }
    
    public void removeItem(InvoiceItem item) {
        items.remove(item);
        item.setInvoice(null);
    }
    
    // Calcul automatique des totaux
    public void calculateTotals() {
        // Sum line totals safely (null-safe) and ensure scale/rounding
        BigDecimal ht = items.stream()
            .map(InvoiceItem::getTotalHT)
            .map(v -> v == null ? BigDecimal.ZERO : v)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);

        BigDecimal tva = items.stream()
            .map(InvoiceItem::getTotalTVA)
            .map(v -> v == null ? BigDecimal.ZERO : v)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);

        BigDecimal ttc = ht.add(tva).setScale(2, RoundingMode.HALF_UP);
        
        // Set both old and new column values
        this.totalHT = ht;
        this.totalTVA = tva;
        this.totalTTC = ttc;
        this.subtotalAmount = ht;
        this.taxAmount = tva;
        this.totalAmount = ttc;
    }
}