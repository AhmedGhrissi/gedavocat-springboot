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
import org.springframework.test.annotation.Rollback;
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
@Rollback(false)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MultiTenantIsolationTest {

    @Autowired
    private FirmRepository firmRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CaseRepository caseRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private FirmService firmService;

    @PersistenceContext
    private EntityManager entityManager;

    // Données de test
    private static Firm firmA;
    private static Firm firmB;
    private static User lawyerA;
    private static User lawyerB;
    private static Case caseA1;
    private static Case caseB1;
    private static Client clientA1;
    private static Client clientB1;
    private static Document documentA1;
    private static Document documentB1;

    @BeforeEach
    void setUp() {
        // Nettoyer le contexte de sécurité
        SecurityContextHolder.clearContext();
    }

    @Test
    @Order(1)
    @DisplayName("01 - Créer 2 cabinets séparés")
    void testCreateTwoFirms() {
        // Cabinet A
        firmA = new Firm();
        firmA.setId(UUID.randomUUID().toString());
        firmA.setName("Cabinet Dupont & Associés");
        firmA.setSubscriptionPlan(Firm.SubscriptionPlan.CABINET);
        firmA.setSubscriptionStatus(Firm.SubscriptionStatus.ACTIVE);
        firmA.setMaxLawyers(5);
        firmA.setMaxClients(75);
        firmA = firmRepository.save(firmA);

        // Cabinet B
        firmB = new Firm();
        firmB.setId(UUID.randomUUID().toString());
        firmB.setName("Cabinet Martin & Partners");
        firmB.setSubscriptionPlan(Firm.SubscriptionPlan.CABINET);
        firmB.setSubscriptionStatus(Firm.SubscriptionStatus.ACTIVE);
        firmB.setMaxLawyers(5);
        firmB.setMaxClients(75);
        firmB = firmRepository.save(firmB);

        assertNotNull(firmA.getId());
        assertNotNull(firmB.getId());
        assertNotEquals(firmA.getId(), firmB.getId());
    }

    @Test
    @Order(2)
    @DisplayName("02 - Créer avocats dans chaque cabinet")
    void testCreateLawyersInEachFirm() {
        // Avocat Cabinet A
        lawyerA = new User();
        lawyerA.setId(UUID.randomUUID().toString());
        lawyerA.setEmail("avocat.a@cabinet-dupont.fr");
        lawyerA.setName("Maître Dupont");
        lawyerA.setFirstName("Maître");
        lawyerA.setLastName("Dupont");
        lawyerA.setPassword("password_encoded");
        lawyerA.setRole(User.UserRole.LAWYER);
        lawyerA.setFirm(firmA);
        lawyerA.setAccountEnabled(true);
        lawyerA.setEmailVerified(true);
        lawyerA = userRepository.save(lawyerA);

        // Avocat Cabinet B
        lawyerB = new User();
        lawyerB.setId(UUID.randomUUID().toString());
        lawyerB.setEmail("avocat.b@cabinet-martin.fr");
        lawyerB.setName("Maître Martin");
        lawyerB.setFirstName("Maître");
        lawyerB.setLastName("Martin");
        lawyerB.setPassword("password_encoded");
        lawyerB.setRole(User.UserRole.LAWYER);
        lawyerB.setFirm(firmB);
        lawyerB.setAccountEnabled(true);
        lawyerB.setEmailVerified(true);
        lawyerB = userRepository.save(lawyerB);

        assertEquals(firmA.getId(), lawyerA.getFirm().getId());
        assertEquals(firmB.getId(), lawyerB.getFirm().getId());
    }

    @Test
    @Order(3)
    @DisplayName("03 - Créer clients dans chaque cabinet")
    void testCreateClientsInEachFirm() {
        // Client Cabinet A
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

        // Client Cabinet B
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

        assertEquals(firmA.getId(), clientA1.getFirm().getId());
        assertEquals(firmB.getId(), clientB1.getFirm().getId());
    }

    @Test
    @Order(4)
    @DisplayName("04 - Créer dossiers dans chaque cabinet")
    void testCreateCasesInEachFirm() {
        // Dossier Cabinet A
        caseA1 = new Case();
        caseA1.setId(UUID.randomUUID().toString());
        caseA1.setFirm(firmA);
        caseA1.setLawyer(lawyerA);
        caseA1.setClient(clientA1);
        caseA1.setName("Affaire Dupuis vs Entreprise X");
        caseA1.setReference("A-2026-001");
        caseA1.setCaseType(Case.CaseType.CIVIL);
        caseA1.setStatus(Case.CaseStatus.OPEN);
        caseA1 = caseRepository.save(caseA1);

        // Dossier Cabinet B
        caseB1 = new Case();
        caseB1.setId(UUID.randomUUID().toString());
        caseB1.setFirm(firmB);
        caseB1.setLawyer(lawyerB);
        caseB1.setClient(clientB1);
        caseB1.setName("Affaire Durand Succession");
        caseB1.setReference("B-2026-001");
        caseB1.setCaseType(Case.CaseType.FAMILLE);
        caseB1.setStatus(Case.CaseStatus.IN_PROGRESS);
        caseB1 = caseRepository.save(caseB1);

        assertEquals(firmA.getId(), caseA1.getFirm().getId());
        assertEquals(firmB.getId(), caseB1.getFirm().getId());
    }

    @Test
    @Order(5)
    @DisplayName("05 - Créer documents dans chaque cabinet")
    void testCreateDocumentsInEachFirm() {
        // Document Cabinet A
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

        // Document Cabinet B
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

        assertEquals(firmA.getId(), documentA1.getFirm().getId());
        assertEquals(firmB.getId(), documentB1.getFirm().getId());
    }

    @Test
    @Order(6)
    @DisplayName("06 - ISOLATION: Avocat A ne voit QUE ses dossiers")
    void testLawyerACanOnlySeeOwnCases() {
        // Simuler connexion Avocat A
        authenticateAs(lawyerA);

        // Lister TOUS les dossiers (avec filtre Hibernate actif)
        List<Case> cases = caseRepository.findAll();

        // L'avocat A doit voir UNIQUEMENT le dossier de son cabinet
        assertEquals(1, cases.size());
        assertEquals(caseA1.getId(), cases.get(0).getId());
        assertEquals(firmA.getId(), cases.get(0).getFirm().getId());
        assertEquals("Affaire Dupuis vs Entreprise X", cases.get(0).getName());

        // Vérifier que le dossier B est INVISIBLE
        assertFalse(cases.stream().anyMatch(c -> c.getId().equals(caseB1.getId())));
    }

    @Test
    @Order(7)
    @DisplayName("07 - ISOLATION: Avocat B ne voit QUE ses dossiers")
    void testLawyerBCanOnlySeeOwnCases() {
        // Simuler connexion Avocat B
        authenticateAs(lawyerB);

        // Lister TOUS les dossiers
        List<Case> cases = caseRepository.findAll();

        // L'avocat B doit voir UNIQUEMENT le dossier de son cabinet
        assertEquals(1, cases.size());
        assertEquals(caseB1.getId(), cases.get(0).getId());
        assertEquals(firmB.getId(), cases.get(0).getFirm().getId());
        assertEquals("Affaire Durand Succession", cases.get(0).getName());

        // Vérifier que le dossier A est INVISIBLE
        assertFalse(cases.stream().anyMatch(c -> c.getId().equals(caseA1.getId())));
    }

    @Test
    @Order(8)
    @DisplayName("08 - ISOLATION: Avocat A ne voit QUE ses clients")
    void testLawyerACanOnlySeeOwnClients() {
        authenticateAs(lawyerA);

        List<Client> clients = clientRepository.findAll();

        assertEquals(1, clients.size());
        assertEquals(clientA1.getId(), clients.get(0).getId());
        assertEquals("Jean Dupuis", clients.get(0).getName());
        assertFalse(clients.stream().anyMatch(c -> c.getId().equals(clientB1.getId())));
    }

    @Test
    @Order(9)
    @DisplayName("09 - ISOLATION: Avocat B ne voit QUE ses clients")
    void testLawyerBCanOnlySeeOwnClients() {
        authenticateAs(lawyerB);

        List<Client> clients = clientRepository.findAll();

        assertEquals(1, clients.size());
        assertEquals(clientB1.getId(), clients.get(0).getId());
        assertEquals("Marie Durand", clients.get(0).getName());
        assertFalse(clients.stream().anyMatch(c -> c.getId().equals(clientA1.getId())));
    }

    @Test
    @Order(10)
    @DisplayName("10 - ISOLATION: Avocat A ne voit QUE ses documents")
    void testLawyerACanOnlySeeOwnDocuments() {
        authenticateAs(lawyerA);

        List<Document> documents = documentRepository.findAll();

        assertEquals(1, documents.size());
        assertEquals(documentA1.getId(), documents.get(0).getId());
        assertEquals("contrat_dupuis.pdf", documents.get(0).getFilename());
        assertFalse(documents.stream().anyMatch(d -> d.getId().equals(documentB1.getId())));
    }

    @Test
    @Order(11)
    @DisplayName("11 - ISOLATION: Avocat B ne voit QUE ses documents")
    void testLawyerBCanOnlySeeOwnDocuments() {
        authenticateAs(lawyerB);

        List<Document> documents = documentRepository.findAll();

        assertEquals(1, documents.size());
        assertEquals(documentB1.getId(), documents.get(0).getId());
        assertEquals("testament_durand.pdf", documents.get(0).getFilename());
        assertFalse(documents.stream().anyMatch(d -> d.getId().equals(documentA1.getId())));
    }

    @Test
    @Order(12)
    @DisplayName("12 - SÉCURITÉ: Accès direct par ID refuse dossier autre cabinet")
    void testDirectAccessToCaseFromOtherFirmReturnsEmpty() {
        authenticateAs(lawyerA);

        // Tentative accès au dossier du Cabinet B par son ID
        var caseFromOtherFirm = caseRepository.findByIdWithFilter(caseB1.getId());

        // Le filtre Hibernate doit retourner VIDE (404 dans le controller)
        assertTrue(caseFromOtherFirm.isEmpty(), 
            "L'avocat ne doit PAS pouvoir accéder au dossier d'un autre cabinet même avec l'ID");
    }

    @Test
    @Order(13)
    @DisplayName("13 - QUOTAS: Vérifier limitation nombre avocats par cabinet")
    void testFirmLawyerQuota() {
        // Cabinet A : plan CABINET (max 5 avocats)
        assertEquals(5, firmA.getMaxLawyers());

        // Compter les avocats actuels
        boolean canAddMore = firmService.canAddMoreLawyers(firmA.getId());
        assertTrue(canAddMore, "Le cabinet A devrait pouvoir ajouter plus d'avocats (1/5)");

        // Créer 4 avocats supplémentaires pour atteindre le quota
        for (int i = 2; i <= 5; i++) {
            User lawyer = new User();
            lawyer.setId(UUID.randomUUID().toString());
            lawyer.setEmail("avocat" + i + "@cabinet-dupont.fr");
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

        // Vérifier quota atteint
        boolean canAddAfterQuota = firmService.canAddMoreLawyers(firmA.getId());
        assertFalse(canAddAfterQuota, "Le cabinet A ne devrait PLUS pouvoir ajouter d'avocats (5/5)");
    }

    @Test
    @Order(14)
    @DisplayName("14 - QUOTAS: Vérifier limitation nombre clients par cabinet")
    void testFirmClientQuota() {
        // Cabinet B : plan CABINET (max 75 clients)
        assertEquals(75, firmB.getMaxClients());

        boolean canAddMore = firmService.canAddMoreClients(firmB.getId());
        assertTrue(canAddMore, "Le cabinet B devrait pouvoir ajouter plus de clients (1/75)");
    }

    // ==========================================
    // MÉTHODES UTILITAIRES
    // ==========================================

    /**
     * Simuler connexion utilisateur (active le filtre multi-tenant)
     */
    private void authenticateAs(User user) {
        UserPrincipal principal = new UserPrincipal(user);
        UsernamePasswordAuthenticationToken auth = 
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
        
        // ACTIVER LE FILTRE HIBERNATE MANUELLEMENT (car pas de requête HTTP dans les tests)
        if (user.getFirm() != null && user.getFirm().getId() != null) {
            Session session = entityManager.unwrap(Session.class);
            session.enableFilter("firmFilter")
                   .setParameter("firmId", user.getFirm().getId());
        }
    }
}
