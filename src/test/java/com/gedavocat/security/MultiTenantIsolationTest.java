package com.gedavocat.security;

import com.gedavocat.model.*;
import com.gedavocat.repository.*;
import com.gedavocat.service.FirmService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests d'isolation multi-tenant
 *
 * Objectif : Vérifier qu'un utilisateur d'un cabinet ne peut JAMAIS voir
 * les données d'un autre cabinet (dossiers, documents, clients).
 *
 * Architecture testée :
 * - Firm entity (cabinets)
 * - firmId foreign key sur toutes les tables
 * - Hibernate @Filter automatique (MultiTenantFilter)
 *
 * Référence : docs/RAPPORT_AUDIT_SECURITE_Phase1.md §6.1 VULN-01
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class MultiTenantIsolationTest {

    @Autowired private FirmRepository firmRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CaseRepository caseRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private FirmService firmService;

    @PersistenceContext
    private EntityManager entityManager;

    // Données de test — instance fields (réinitialisées par @BeforeEach dans la même transaction)
    private Firm firmA;
    private Firm firmB;
    private User lawyerA;
    private User lawyerB;
    private Case caseA1;
    private Case caseB1;
    private Client clientA1;
    private Client clientB1;
    private Document documentA1;
    private Document documentB1;

    @BeforeEach
    void setUpTestData() {
        SecurityContextHolder.clearContext();

        // Cabinets
        firmA = new Firm();
        firmA.setId(UUID.randomUUID().toString());
        firmA.setName("Cabinet Dupont & Associés");
        firmA.setSubscriptionPlan(Firm.SubscriptionPlan.CABINET);
        firmA.setSubscriptionStatus(Firm.SubscriptionStatus.ACTIVE);
        firmA.setMaxLawyers(5);
        firmA.setMaxClients(75);
        firmA = firmRepository.save(firmA);

        firmB = new Firm();
        firmB.setId(UUID.randomUUID().toString());
        firmB.setName("Cabinet Martin & Partners");
        firmB.setSubscriptionPlan(Firm.SubscriptionPlan.CABINET);
        firmB.setSubscriptionStatus(Firm.SubscriptionStatus.ACTIVE);
        firmB.setMaxLawyers(5);
        firmB.setMaxClients(75);
        firmB = firmRepository.save(firmB);

        // Avocats
        lawyerA = new User();
        lawyerA.setId(UUID.randomUUID().toString());
        lawyerA.setEmail("avocat.a." + UUID.randomUUID() + "@cabinet-dupont.fr");
        lawyerA.setName("Maître Dupont");
        lawyerA.setFirstName("Maître");
        lawyerA.setLastName("Dupont");
        lawyerA.setPassword("password_encoded");
        lawyerA.setRole(User.UserRole.LAWYER);
        lawyerA.setFirm(firmA);
        lawyerA.setAccountEnabled(true);
        lawyerA.setEmailVerified(true);
        lawyerA = userRepository.save(lawyerA);

        lawyerB = new User();
        lawyerB.setId(UUID.randomUUID().toString());
        lawyerB.setEmail("avocat.b." + UUID.randomUUID() + "@cabinet-martin.fr");
        lawyerB.setName("Maître Martin");
        lawyerB.setFirstName("Maître");
        lawyerB.setLastName("Martin");
        lawyerB.setPassword("password_encoded");
        lawyerB.setRole(User.UserRole.LAWYER);
        lawyerB.setFirm(firmB);
        lawyerB.setAccountEnabled(true);
        lawyerB.setEmailVerified(true);
        lawyerB = userRepository.save(lawyerB);

        // Clients
        clientA1 = new Client();
        clientA1.setId(UUID.randomUUID().toString());
        clientA1.setFirm(firmA);
        clientA1.setLawyer(lawyerA);
        clientA1.setFirstName("Jean");
        clientA1.setLastName("Dupuis");
        clientA1.setName("Jean Dupuis");
        clientA1.setEmail("jean.dupuis@example.com");
        clientA1.setPhone("0601020304");
        clientA1 = clientRepository.save(clientA1);

        clientB1 = new Client();
        clientB1.setId(UUID.randomUUID().toString());
        clientB1.setFirm(firmB);
        clientB1.setLawyer(lawyerB);
        clientB1.setFirstName("Marie");
        clientB1.setLastName("Durand");
        clientB1.setName("Marie Durand");
        clientB1.setEmail("marie.durand@example.com");
        clientB1.setPhone("0607080910");
        clientB1 = clientRepository.save(clientB1);

        // Dossiers
        caseA1 = new Case();
        caseA1.setId(UUID.randomUUID().toString());
        caseA1.setFirm(firmA);
        caseA1.setLawyer(lawyerA);
        caseA1.setClient(clientA1);
        caseA1.setName("Affaire Dupuis vs Entreprise X");
        caseA1.setReference("A-" + UUID.randomUUID());
        caseA1.setCaseType(Case.CaseType.CIVIL);
        caseA1.setStatus(Case.CaseStatus.OPEN);
        caseA1 = caseRepository.save(caseA1);

        caseB1 = new Case();
        caseB1.setId(UUID.randomUUID().toString());
        caseB1.setFirm(firmB);
        caseB1.setLawyer(lawyerB);
        caseB1.setClient(clientB1);
        caseB1.setName("Affaire Durand Succession");
        caseB1.setReference("B-" + UUID.randomUUID());
        caseB1.setCaseType(Case.CaseType.FAMILLE);
        caseB1.setStatus(Case.CaseStatus.IN_PROGRESS);
        caseB1 = caseRepository.save(caseB1);

        // Documents
        documentA1 = new Document();
        documentA1.setId(UUID.randomUUID().toString());
        documentA1.setFirm(firmA);
        documentA1.setCaseEntity(caseA1);
        documentA1.setUploadedBy(lawyerA);
        documentA1.setUploaderRole("LAWYER");
        documentA1.setFilename("contrat_dupuis.pdf");
        documentA1.setOriginalName("Contrat Dupuis.pdf");
        documentA1.setMimetype("application/pdf");
        documentA1.setPath("/uploads/firmA/contrat_dupuis.pdf");
        documentA1.setFileSize(1024L);
        documentA1 = documentRepository.save(documentA1);

        documentB1 = new Document();
        documentB1.setId(UUID.randomUUID().toString());
        documentB1.setFirm(firmB);
        documentB1.setCaseEntity(caseB1);
        documentB1.setUploadedBy(lawyerB);
        documentB1.setUploaderRole("LAWYER");
        documentB1.setFilename("testament_durand.pdf");
        documentB1.setOriginalName("Testament Durand.pdf");
        documentB1.setMimetype("application/pdf");
        documentB1.setPath("/uploads/firmB/testament_durand.pdf");
        documentB1.setFileSize(2048L);
        documentB1 = documentRepository.save(documentB1);

        entityManager.flush();
    }

    // =====================================================================
    // TESTS CRÉATION — vérifient que les fixtures sont correctement créées
    // =====================================================================

    @Test
    @DisplayName("01 - Création : 2 cabinets distincts")
    void testCreateTwoFirms() {
        assertNotNull(firmA.getId());
        assertNotNull(firmB.getId());
        assertNotEquals(firmA.getId(), firmB.getId());
    }

    @Test
    @DisplayName("02 - Création : avocats dans chaque cabinet")
    void testCreateLawyersInEachFirm() {
        assertEquals(firmA.getId(), lawyerA.getFirm().getId());
        assertEquals(firmB.getId(), lawyerB.getFirm().getId());
    }

    @Test
    @DisplayName("03 - Création : clients dans chaque cabinet")
    void testCreateClientsInEachFirm() {
        assertEquals(firmA.getId(), clientA1.getFirm().getId());
        assertEquals(firmB.getId(), clientB1.getFirm().getId());
    }

    @Test
    @DisplayName("04 - Création : dossiers dans chaque cabinet")
    void testCreateCasesInEachFirm() {
        assertEquals(firmA.getId(), caseA1.getFirm().getId());
        assertEquals(firmB.getId(), caseB1.getFirm().getId());
    }

    @Test
    @DisplayName("05 - Création : documents dans chaque cabinet")
    void testCreateDocumentsInEachFirm() {
        assertEquals(firmA.getId(), documentA1.getFirm().getId());
        assertEquals(firmB.getId(), documentB1.getFirm().getId());
    }

    // =====================================================================
    // TESTS ISOLATION — vérifient le filtre multi-tenant Hibernate
    // =====================================================================

    @Test
    @DisplayName("06 - ISOLATION: Avocat A ne voit QUE ses dossiers")
    void testLawyerACanOnlySeeOwnCases() {
        authenticateAs(lawyerA);
        List<Case> cases = caseRepository.findAll();
        assertEquals(1, cases.size());
        assertEquals(caseA1.getId(), cases.get(0).getId());
        assertEquals(firmA.getId(), cases.get(0).getFirm().getId());
        assertFalse(cases.stream().anyMatch(c -> c.getId().equals(caseB1.getId())));
    }

    @Test
    @DisplayName("07 - ISOLATION: Avocat B ne voit QUE ses dossiers")
    void testLawyerBCanOnlySeeOwnCases() {
        authenticateAs(lawyerB);
        List<Case> cases = caseRepository.findAll();
        assertEquals(1, cases.size());
        assertEquals(caseB1.getId(), cases.get(0).getId());
        assertEquals(firmB.getId(), cases.get(0).getFirm().getId());
        assertFalse(cases.stream().anyMatch(c -> c.getId().equals(caseA1.getId())));
    }

    @Test
    @DisplayName("08 - ISOLATION: Avocat A ne voit QUE ses clients")
    void testLawyerACanOnlySeeOwnClients() {
        authenticateAs(lawyerA);
        List<Client> clients = clientRepository.findAll();
        assertEquals(1, clients.size());
        assertEquals(clientA1.getId(), clients.get(0).getId());
        assertFalse(clients.stream().anyMatch(c -> c.getId().equals(clientB1.getId())));
    }

    @Test
    @DisplayName("09 - ISOLATION: Avocat B ne voit QUE ses clients")
    void testLawyerBCanOnlySeeOwnClients() {
        authenticateAs(lawyerB);
        List<Client> clients = clientRepository.findAll();
        assertEquals(1, clients.size());
        assertEquals(clientB1.getId(), clients.get(0).getId());
        assertFalse(clients.stream().anyMatch(c -> c.getId().equals(clientA1.getId())));
    }

    @Test
    @DisplayName("10 - ISOLATION: Avocat A ne voit QUE ses documents")
    void testLawyerACanOnlySeeOwnDocuments() {
        authenticateAs(lawyerA);
        List<Document> documents = documentRepository.findAll();
        assertEquals(1, documents.size());
        assertEquals(documentA1.getId(), documents.get(0).getId());
        assertFalse(documents.stream().anyMatch(d -> d.getId().equals(documentB1.getId())));
    }

    @Test
    @DisplayName("11 - ISOLATION: Avocat B ne voit QUE ses documents")
    void testLawyerBCanOnlySeeOwnDocuments() {
        authenticateAs(lawyerB);
        List<Document> documents = documentRepository.findAll();
        assertEquals(1, documents.size());
        assertEquals(documentB1.getId(), documents.get(0).getId());
        assertFalse(documents.stream().anyMatch(d -> d.getId().equals(documentA1.getId())));
    }

    @Test
    @DisplayName("12 - SÉCURITÉ: Accès direct par ID refuse dossier autre cabinet")
    void testDirectAccessToCaseFromOtherFirmReturnsEmpty() {
        authenticateAs(lawyerA);
        var caseFromOtherFirm = caseRepository.findByIdWithFilter(caseB1.getId());
        assertTrue(caseFromOtherFirm.isEmpty(),
            "L'avocat ne doit PAS pouvoir accéder au dossier d'un autre cabinet même avec l'ID");
    }

    @Test
    @DisplayName("13 - QUOTAS: Limitation nombre avocats par cabinet")
    void testFirmLawyerQuota() {
        assertEquals(5, firmA.getMaxLawyers());

        boolean canAddMore = firmService.canAddMoreLawyers(firmA.getId());
        assertTrue(canAddMore, "Le cabinet A devrait pouvoir ajouter plus d'avocats (1/5)");

        for (int i = 2; i <= 5; i++) {
            User lawyer = new User();
            lawyer.setId(UUID.randomUUID().toString());
            lawyer.setEmail("avocat" + i + "." + UUID.randomUUID() + "@cabinet-dupont.fr");
            lawyer.setName("Avocat " + i);
            lawyer.setFirstName("Avocat");
            lawyer.setLastName("Test" + i);
            lawyer.setPassword("password");
            lawyer.setRole(User.UserRole.LAWYER);
            lawyer.setFirm(firmA);
            lawyer.setAccountEnabled(true);
            lawyer.setEmailVerified(true);
            userRepository.save(lawyer);
        }

        boolean canAddAfterQuota = firmService.canAddMoreLawyers(firmA.getId());
        assertFalse(canAddAfterQuota, "Le cabinet A ne devrait PLUS pouvoir ajouter d'avocats (5/5)");
    }

    @Test
    @DisplayName("14 - QUOTAS: Limitation nombre clients par cabinet")
    void testFirmClientQuota() {
        assertEquals(75, firmB.getMaxClients());
        boolean canAddMore = firmService.canAddMoreClients(firmB.getId());
        assertTrue(canAddMore, "Le cabinet B devrait pouvoir ajouter plus de clients (1/75)");
    }

    // =====================================================================
    // UTILITAIRES
    // =====================================================================

    private void authenticateAs(User user) {
        UserPrincipal principal = new UserPrincipal(user);
        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        if (user.getFirm() != null && user.getFirm().getId() != null) {
            Session session = entityManager.unwrap(Session.class);
            session.enableFilter("firmFilter")
                   .setParameter("firmId", user.getFirm().getId());
        }
    }
}
