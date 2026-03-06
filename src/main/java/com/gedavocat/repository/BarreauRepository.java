package com.gedavocat.repository;

import com.gedavocat.model.Barreau;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository pour gérer les Barreaux français
 */
@Repository
public interface BarreauRepository extends JpaRepository<Barreau, Long> {

    /**
     * Recherche un barreau par son nom exact
     */
    Optional<Barreau> findByBarreau(String barreau);

    /**
     * Recherche les barreaux actifs
     */
    List<Barreau> findByActifTrueOrderByBarreauAsc();

    /**
     * Recherche tous les barreaux par ordre alphabétique
     */
    List<Barreau> findAllByOrderByBarreauAsc();

    /**
     * Recherche les barreaux par région
     */
    List<Barreau> findByRegionOrderByBarreauAsc(String region);

    /**
     * Recherche les barreaux par cour d'appel
     */
    List<Barreau> findByCourAppelOrderByBarreauAsc(String courAppel);

    /**
     * Recherche par nom de barreau (insensible à la casse)
     */
    Optional<Barreau> findByBarreauIgnoreCase(String barreau);

    /**
     * Recherche des barreaux par nom partiel (autocomplete)
     */
    @Query("SELECT b FROM Barreau b WHERE LOWER(b.barreau) LIKE LOWER(CONCAT('%', :search, '%')) AND b.actif = true ORDER BY b.barreau ASC")
    List<Barreau> searchByBarreauName(@Param("search") String search);

    /**
     * Recherche des barreaux par région ou ville
     */
    @Query("SELECT b FROM Barreau b WHERE " +
           "(LOWER(b.barreau) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(b.region) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(b.villeSiege) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND b.actif = true ORDER BY b.barreau ASC")
    List<Barreau> searchBarreaux(@Param("search") String search);

    /**
     * Compte le nombre de barreaux actifs
     */
    long countByActifTrue();

    /**
     * Récupère toutes les régions distinctes
     */
    @Query("SELECT DISTINCT b.region FROM Barreau b WHERE b.region IS NOT NULL ORDER BY b.region")
    List<String> findAllRegionsDistinct();

    /**
     * Récupère toutes les cours d'appel distinctes
     */
    @Query("SELECT DISTINCT b.courAppel FROM Barreau b WHERE b.courAppel IS NOT NULL ORDER BY b.courAppel")
    List<String> findAllCoursAppelDistinct();

    /**
     * Vérifie si un barreau existe par son nom
     */
    boolean existsByBarreauIgnoreCase(String barreau);
}
