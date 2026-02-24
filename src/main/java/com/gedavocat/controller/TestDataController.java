package com.gedavocat.controller;

import com.gedavocat.model.Client;
import com.gedavocat.model.Invoice;
import com.gedavocat.model.InvoiceItem;
import com.gedavocat.model.User;
import com.gedavocat.repository.ClientRepository;
import com.gedavocat.repository.InvoiceItemRepository;
import com.gedavocat.repository.InvoiceRepository;
import com.gedavocat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Endpoints de test uniquement activés en profile dev/local :
 * - POST /test/seed : crée un avocat, un client lié et une facture pour tests
 * - POST /test/login : authentifie un user (email/password) et crée une session
 */
@RestController
@RequestMapping("/test")
@Profile({"dev", "local"})
@RequiredArgsConstructor
public class TestDataController {

    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    @PostMapping("/seed")
    public ResponseEntity<Map<String, String>> seed(@RequestBody(required = false) Map<String, String> body) {
        // Create a lawyer (if not exists)
        String lawyerEmail = body != null && body.containsKey("lawyerEmail") ? body.get("lawyerEmail") : "test.lawyer@example.com";
        String clientEmail = body != null && body.containsKey("clientEmail") ? body.get("clientEmail") : "test.client@example.com";
        String clientName = body != null && body.containsKey("clientName") ? body.get("clientName") : "Client de Test";
        String password = body != null && body.containsKey("password") ? body.get("password") : "Password1!"; // must meet rules for register endpoints

        User lawyer = userRepository.findByEmail(lawyerEmail).orElseGet(() -> {
            User u = new User();
            u.setId(UUID.randomUUID().toString());
            u.setEmail(lawyerEmail);
            u.setName("Test Lawyer");
            u.setPassword(passwordEncoder.encode(password));
            u.setRole(User.UserRole.LAWYER);
            return userRepository.save(u);
        });

        // Create a client and link to lawyer
        Client client = clientRepository.findByEmail(clientEmail).orElseGet(() -> {
            Client c = new Client();
            c.setId(UUID.randomUUID().toString());
            c.setName(clientName);
            c.setEmail(clientEmail);
            c.setLawyer(lawyer);
            return clientRepository.save(c);
        });

        // Create an invoice for that client
        Invoice invoice = new Invoice();
        invoice.setId(UUID.randomUUID().toString());
        invoice.setInvoiceNumber("TEST-" + System.currentTimeMillis());
        invoice.setClient(client);
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setDueDate(LocalDate.now().plusDays(30));
        invoice.setStatus(Invoice.InvoiceStatus.SENT);
        invoice.setTotalHT(BigDecimal.valueOf(100));
        invoice.setTotalTVA(BigDecimal.valueOf(20.00));
        invoice.setTotalTTC(BigDecimal.valueOf(120.00));
        invoiceRepository.save(invoice);

        // Add a sample invoice item
        InvoiceItem item = new InvoiceItem();
        item.setId(UUID.randomUUID().toString());
        item.setInvoice(invoice);
        item.setDescription("Honoraires test");
        item.setQuantity(BigDecimal.ONE);
        item.setUnitPriceHT(BigDecimal.valueOf(100));
        item.setTvaRate(BigDecimal.valueOf(20));
        invoiceItemRepository.save(item);

        // return useful ids and credentials
        Map<String, String> res = new HashMap<>();
        res.put("lawyerEmail", lawyer.getEmail());
        res.put("clientEmail", client.getEmail());
        res.put("clientId", client.getId());
        res.put("invoiceId", invoice.getId());
        res.put("password", password);
        return ResponseEntity.ok(res);
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> testLogin(@RequestBody Map<String, String> body,
                                                         HttpServletRequest request,
                                                         HttpServletResponse response) {
        String email = body.get("email");
        String password = body.get("password");
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(email, password);
        Authentication auth = authenticationManager.authenticate(token);
        SecurityContextHolder.getContext().setAuthentication(auth);
        HttpSession session = request.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, SecurityContextHolder.getContext());
        Map<String,String> r = new HashMap<>();
        r.put("message", "ok");
        r.put("email", email);
        return ResponseEntity.ok(r);
    }
}
