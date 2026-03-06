package com.gedavocat.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entité représentant un Barreau français
 * Contient les informations géographiques et juridictionnelles des barreaux de France
 */
@Entity
@Table(name = "barreaux_france", indexes = {
    @Index(name = "idx_barreau_nom", columnList = "barreau"),
    @Index(name = "idx_barreau_region", columnList = "region"),
    @Index(name = "idx_barreau_ville", columnList = "ville_siege")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Barreau {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Nom du barreau (ex: "Paris", "Lyon", "Marseille")
     */
    @Column(nullable = false, length = 255)
    private String barreau;

    /**
     * Région administrative française (ex: "Île-de-France", "Auvergne-Rhône-Alpes")
     */
    @Column(length = 255)
    private String region;

    /**
     * Ville où siège le barreau
     */
    @Column(name = "ville_siege", length = 255)
    private String villeSiege;

    /**
     * Cour d'appel de rattachement
     */
    @Column(name = "cour_appel", length = 255)
    private String courAppel;

    /**
     * Tribunal judiciaire de rattachement
     */
    @Column(name = "tribunal_judiciaire", length = 255)
    private String tribunalJudiciaire;

    /**
     * Cour d'appel métropolitaine (pour les barreaux d'outre-mer principalement)
     */
    @Column(name = "cour_appel_metropolitaine", length = 255)
    private String courAppelMetropolitaine;

    /**
     * Nombre d'avocats inscrits dans ce barreau (optionnel, pour statistiques)
     */
    @Column(name = "nombre_avocats")
    private Integer nombreAvocats;

    /**
     * Indique si le barreau est actif
     */
    @Column(nullable = false)
    private Boolean actif = true;

    /**
     * Constructeur avec les champs essentiels
     */
    public Barreau(String barreau, String region, String villeSiege, String courAppel) {
        this.barreau = barreau;
        this.region = region;
        this.villeSiege = villeSiege;
        this.courAppel = courAppel;
        this.actif = true;
    }

    /**
     * Retourne le libellé complet du barreau
     */
    public String getLibelleComplet() {
        return String.format("%s (%s)", barreau, villeSiege != null ? villeSiege : region);
    }

    /**
     * Retourne une description formatée du barreau
     */
    @Override
    public String toString() {
        return String.format("Barreau de %s - %s", barreau, region);
    }
}
