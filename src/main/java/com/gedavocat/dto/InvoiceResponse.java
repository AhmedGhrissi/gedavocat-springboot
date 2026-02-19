package com.gedavocat.dto;

import com.gedavocat.model.Invoice.InvoiceStatus;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO pour la réponse d'une facture
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceResponse {
    
    private String id;
    private String invoiceNumber;
    private String clientId;
    private String clientName;
    private String clientEmail;
    private LocalDate invoiceDate;
    private LocalDate dueDate;
    private LocalDate paidDate;
    private InvoiceStatus status;
    private BigDecimal totalHT;
    private BigDecimal totalTVA;
    private BigDecimal totalTTC;
    private String currency;
    private String notes;
    private String paymentMethod;
    private String documentUrl;
    private List<InvoiceItemResponse> items = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean overdue;
}
