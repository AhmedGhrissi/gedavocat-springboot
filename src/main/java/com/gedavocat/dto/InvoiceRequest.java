package com.gedavocat.dto;

import com.gedavocat.model.Invoice.InvoiceStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO pour la création/modification d'une facture
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceRequest {
    
    @NotBlank(message = "Le numéro de facture est obligatoire")
    private String invoiceNumber;
    
    @NotBlank(message = "L'ID du client est obligatoire")
    private String clientId;
    
    @NotNull(message = "La date de facture est obligatoire")
    private LocalDate invoiceDate;
    
    private LocalDate dueDate;
    
    private LocalDate paidDate;
    
    private InvoiceStatus status;
    
    private String notes;
    
    private String paymentMethod;
    
    private String documentUrl;
    
    @Valid
    private List<InvoiceItemRequest> items = new ArrayList<>();
}
