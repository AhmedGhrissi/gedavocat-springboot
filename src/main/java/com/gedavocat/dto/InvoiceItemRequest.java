package com.gedavocat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO pour la création/modification d'une ligne de facture
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceItemRequest {
    
    @NotBlank(message = "La description est obligatoire")
    private String description;
    
    @NotNull(message = "La quantité est obligatoire")
    @PositiveOrZero(message = "La quantité doit être positive ou nulle")
    private BigDecimal quantity;
    
    @NotNull(message = "Le prix unitaire HT est obligatoire")
    @PositiveOrZero(message = "Le prix unitaire HT doit être positif ou nul")
    private BigDecimal unitPriceHT;
    
    @NotNull(message = "Le taux de TVA est obligatoire")
    @PositiveOrZero(message = "Le taux de TVA doit être positif ou nul")
    private BigDecimal tvaRate;
    
    private Integer displayOrder;
}
