package com.gedavocat.rpva.controller;

import com.gedavocat.model.Case;
import com.gedavocat.model.User;
import com.gedavocat.repository.CaseRepository;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.rpva.model.ActeProcedure;
import com.gedavocat.rpva.service.RpvaPdfExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;

/**
 * Endpoints REST d'export RPVA pour DocAvocat (Option C — export manuel).
 *
 * GET  /api/rpva/export/fiche/{dossierId}     → PDF/A fiche de transmission
 * GET  /api/rpva/export/checklist/{dossierId} → PDF checklist de dépôt e-Barreau
 * POST /api/rpva/export/fiche                 → Génération à la volée depuis JSON
 *
 * L'avocat génère ses documents depuis DocAvocat et les dépose lui-même sur e-Barreau.
 */
@Slf4j
@RestController
@RequestMapping("/api/rpva/export")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('LAWYER', 'ADMIN', 'AVOCAT_ADMIN')")
public class RpvaExportController {

    private final RpvaPdfExportService pdfExportService;
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;

    /**
     * Génère la fiche de transmission PDF/A à partir d'un dossier DocAvocat.
     * GET /api/rpva/export/fiche/{caseId}
     */
    @GetMapping("/fiche/{caseId}")
    public ResponseEntity<byte[]> exportFiche(
            @PathVariable String caseId,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Export fiche RPVA demandé pour dossier {}", caseId);

        User user = userRepository.findByEmail(userDetails.getUsername())
            .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        Case dossier = caseRepository.findByIdWithClientAndLawyer(caseId).orElse(null);
        if (dossier == null || (dossier.getLawyer() != null
                && !dossier.getLawyer().getId().equals(user.getId()))) {
            return ResponseEntity.status(403).build();
        }

        try {
            ActeProcedure acte = mapCaseToActe(dossier, user);
            byte[] pdf = pdfExportService.genererFicheTransmission(acte);
            return buildPdfResponse(pdf, "fiche-rpva-" + sanitize(caseId) + ".pdf");
        } catch (Exception e) {
            log.error("Erreur génération PDF fiche RPVA pour {}", caseId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Génère la checklist de dépôt e-Barreau à partir d'un dossier DocAvocat.
     * GET /api/rpva/export/checklist/{caseId}
     */
    @GetMapping("/checklist/{caseId}")
    public ResponseEntity<byte[]> exportChecklist(
            @PathVariable String caseId,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Export checklist RPVA demandé pour dossier {}", caseId);

        User user = userRepository.findByEmail(userDetails.getUsername())
            .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        Case dossier = caseRepository.findByIdWithClientAndLawyer(caseId).orElse(null);
        if (dossier == null || (dossier.getLawyer() != null
                && !dossier.getLawyer().getId().equals(user.getId()))) {
            return ResponseEntity.status(403).build();
        }

        try {
            ActeProcedure acte = mapCaseToActe(dossier, user);
            byte[] pdf = pdfExportService.genererChecklistDepot(acte);
            return buildPdfResponse(pdf, "checklist-ebarreau-" + sanitize(caseId) + ".pdf");
        } catch (Exception e) {
            log.error("Erreur génération checklist RPVA pour {}", caseId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Génération à la volée depuis un payload JSON.
     * POST /api/rpva/export/fiche
     */
    @PostMapping("/fiche")
    public ResponseEntity<byte[]> exportFicheFromBody(@RequestBody ActeProcedure acte) {
        try {
            if (acte.getDateGeneration() == null) acte.setDateGeneration(LocalDate.now());
            byte[] pdf = pdfExportService.genererFicheTransmission(acte);
            String filename = "fiche-rpva-" + (acte.getNumeroRole() != null
                ? acte.getNumeroRole().replace("/", "-") : "export") + ".pdf";
            return buildPdfResponse(pdf, filename);
        } catch (IOException e) {
            log.error("Erreur génération PDF fiche RPVA (body)", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Mappe un Case DocAvocat vers un ActeProcedure RPVA.
     * À enrichir avec les données réelles du modèle (juridiction, type d'acte, etc.)
     */
    private ActeProcedure mapCaseToActe(Case dossier, User avocat) {

        // Demandeur : le client du dossier
        ActeProcedure.Partie demandeur = null;
        if (dossier.getClient() != null) {
            demandeur = ActeProcedure.Partie.builder()
                .nom(dossier.getClient().getName() != null ? dossier.getClient().getName() : "—")
                .type(ActeProcedure.TypePartie.PERSONNE_PHYSIQUE)
                .build();
        }

        // Barreau de l'avocat : c'est une entité Barreau avec champ .barreau (String)
        String nomBarreau = "—";
        if (avocat.getBarreau() != null && avocat.getBarreau().getBarreau() != null) {
            nomBarreau = avocat.getBarreau().getBarreau();
        }

        // Avocat rédacteur
        ActeProcedure.Avocat avocatRedacteur = ActeProcedure.Avocat.builder()
            .nom(avocat.getLastName() != null ? avocat.getLastName() : "—")
            .prenom(avocat.getFirstName() != null ? avocat.getFirstName() : "—")
            .barreau(nomBarreau)
            .numeroOrdre(avocat.getBarNumber() != null ? avocat.getBarNumber() : "—")
            .build();

        // Case n'a pas de jurisdiction ni caseNumber — on utilise reference et name
        String numeroRole = dossier.getReference() != null ? dossier.getReference() : "—";
        String juridiction = "À compléter";  // à enrichir si le champ est ajouté à Case

        return ActeProcedure.builder()
            .numeroRole(numeroRole)
            .juridiction(juridiction)
            .typeActe(ActeProcedure.TypeActe.CONCLUSIONS)
            .referenceInterne(dossier.getId())
            .dateGeneration(LocalDate.now())
            .objetMessage("Dossier " + dossier.getName())
            .demandeur(demandeur)
            .avocatRedacteur(avocatRedacteur)
            .piecesJointes(new ArrayList<>())
            .build();
    }

    private ResponseEntity<byte[]> buildPdfResponse(byte[] pdf, String filename) {
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(pdf.length))
            .body(pdf);
    }

    private String sanitize(String s) {
        return s != null ? s.replaceAll("[^a-zA-Z0-9\\-_]", "-") : "export";
    }
}
