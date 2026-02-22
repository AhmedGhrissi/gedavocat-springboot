package com.gedavocat.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.ToString;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
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
    
    @NotNull(message = "Le numéro de facture est obligatoire")
    @Column(name = "invoice_number", nullable = false, unique = true, length = 50)
    private String invoiceNumber;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    @NotNull(message = "Le client est obligatoire")
    private Client client;
    
    @Column(name = "invoice_date", nullable = false)
    @NotNull(message = "La date de facture est obligatoire")
    private LocalDate invoiceDate;
    
    @Column(name = "due_date")
    private LocalDate dueDate;
    
    @Column(name = "paid_date")
    private LocalDate paidDate;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status = InvoiceStatus.DRAFT;
    
    @NotNull(message = "Le montant total HT est obligatoire")
    @PositiveOrZero(message = "Le montant total HT doit être positif ou nul")
    @Column(name = "total_ht", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalHT;
    
    @NotNull(message = "Le montant de la TVA est obligatoire")
    @Column(name = "total_tva", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalTVA = BigDecimal.ZERO;
    
    @NotNull(message = "Le montant total TTC est obligatoire")
    @PositiveOrZero(message = "Le montant total TTC doit être positif ou nul")
    @Column(name = "total_ttc", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalTTC;
    
    @Column(length = 3)
    private String currency = "EUR";
    
    @Column(columnDefinition = "TEXT")
    private String notes;
    
    @Column(name = "payment_method", length = 50)
    private String paymentMethod;
    
    @Column(name = "document_url", length = 500)
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
    
    // Méthodes utilitaires
    @PrePersist
    public void prePersist() {
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
        }
        if (invoiceDate == null) {
            invoiceDate = LocalDate.now();
        }
        if (status == InvoiceStatus.SENT && dueDate == null) {
            dueDate = invoiceDate.plusDays(30); // Échéance par défaut : 30 jours
        }
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
        this.totalHT = items.stream()
            .map(InvoiceItem::getTotalHT)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        this.totalTVA = items.stream()
            .map(InvoiceItem::getTotalTVA)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        this.totalTTC = totalHT.add(totalTVA);
    }
}
