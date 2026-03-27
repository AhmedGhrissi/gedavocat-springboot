package com.gedavocat.rpva.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * Représente un acte de procédure prêt à être déposé sur e-Barreau via le RPVA.
 * Structure conforme aux exigences du Catalogue API 1.5 du CNB.
 * Option C — Export manuel : l'avocat dépose lui-même sur e-Barreau.
 */
@Data
@Builder
public class ActeProcedure {

    // ── Identification du dossier ──────────────────────────────────────────────

    /** Numéro de rôle de l'affaire (ex: "24/00123") */
    private String numeroRole;

    /** Juridiction concernée (ex: "TJ NICE", "CA AIX-EN-PROVENCE") */
    private String juridiction;

    /** Code de la juridiction (code officiel Ministère de la Justice) */
    private String codeJuridiction;

    /** Nature de l'acte */
    private TypeActe typeActe;

    /** Date de l'audience visée (si applicable) */
    private LocalDate dateAudience;

    // ── Parties ───────────────────────────────────────────────────────────────

    private Partie demandeur;
    private Partie defendeur;

    /** Avocat expéditeur (identifié via sa clé RPVA) */
    private Avocat avocatRedacteur;

    // ── Documents attachés ────────────────────────────────────────────────────

    /** Liste des pièces jointes — PDF/A obligatoire, max 10 Mo total sur e-Barreau */
    private List<PieceJointe> piecesJointes;

    private String objetMessage;
    private String corpsMessage;

    // ── Métadonnées ───────────────────────────────────────────────────────────

    private LocalDate dateGeneration;
    private String referenceInterne;

    // ── Sous-classes ──────────────────────────────────────────────────────────

    @Data
    @Builder
    public static class Partie {
        private String nom;
        private String prenom;
        private String raisonSociale;
        private String siren;
        private TypePartie type;
    }

    @Data
    @Builder
    public static class Avocat {
        private String nom;
        private String prenom;
        private String barreau;
        private String numeroOrdre;
        private String identifiantCNBF;
    }

    @Data
    @Builder
    public static class PieceJointe {
        private String nom;
        private byte[] contenu;
        private String mimeType;
        private long taille;
    }

    public enum TypeActe {
        CONCLUSIONS, ASSIGNATION, MEMOIRE, REQUETE, PIECE_COMMUNIQUEE, BORDEREAU_PIECES, AUTRE
    }

    public enum TypePartie {
        PERSONNE_PHYSIQUE, PERSONNE_MORALE
    }
}
