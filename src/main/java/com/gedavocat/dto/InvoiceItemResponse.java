package com.gedavocat.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO pour la réponse d'une ligne de facture
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceItemResponse {
    
    private String id;
    private String description;
    private BigDecimal quantity;
    private BigDecimal unitPriceHT;
    private BigDecimal tvaRate;
    private BigDecimal totalHT;
    private BigDecimal totalTVA;
    private BigDecimal totalTTC;
    private Integer displayOrder;
}
