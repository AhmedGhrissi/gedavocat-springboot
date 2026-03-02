package com.gedavocat.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.ToString;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Entité représentant une ligne de facture
 */
@Entity
@Table(name = "invoice_items", indexes = {
    @Index(name = "idx_invoice_item_invoice_id", columnList = "invoice_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"invoice"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class InvoiceItem {
    
    @Id
    @Column(length = 36)
    @EqualsAndHashCode.Include
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    @NotNull(message = "La facture est obligatoire")
    @JsonIgnore
    private Invoice invoice;
    
    @NotBlank(message = "La description est obligatoire")
    @Column(nullable = false, length = 500)
    private String description;
    
    @NotNull(message = "La quantité est obligatoire")
    @PositiveOrZero(message = "La quantité doit être positive ou nulle")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;
    
    @NotNull(message = "Le prix unitaire HT est obligatoire")
    @PositiveOrZero(message = "Le prix unitaire HT doit être positif ou nul")
    @Column(name = "unit_price_ht", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPriceHT;
    
    @NotNull(message = "Le taux de TVA est obligatoire")
    @PositiveOrZero(message = "Le taux de TVA doit être positif ou nul")
    @Column(name = "tva_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal tvaRate = BigDecimal.valueOf(20.0); // 20% par défaut
    
    @Column(name = "total_ht", precision = 10, scale = 2)
    private BigDecimal totalHT;
    
    @Column(name = "total_tva", precision = 10, scale = 2)
    private BigDecimal totalTVA;
    
    @Column(name = "total_ttc", precision = 10, scale = 2)
    private BigDecimal totalTTC;
    
    @Column(name = "display_order")
    private Integer displayOrder;
    
    // Méthodes utilitaires
    @PrePersist
    public void prePersist() {
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
        }
        calculateTotals();
    }
    
    @PreUpdate
    public void preUpdate() {
        calculateTotals();
    }
    
    // Calcul automatique des totaux
    public void calculateTotals() {
        if (quantity != null && unitPriceHT != null && tvaRate != null) {
            this.totalHT = unitPriceHT.multiply(quantity)
                .setScale(2, RoundingMode.HALF_UP);
            
            this.totalTVA = totalHT.multiply(tvaRate.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP))
                .setScale(2, RoundingMode.HALF_UP);
            
            this.totalTTC = totalHT.add(totalTVA)
                .setScale(2, RoundingMode.HALF_UP);
        }
    }
}
