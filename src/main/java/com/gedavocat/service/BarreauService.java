package com.gedavocat.service;

import com.gedavocat.model.Barreau;
import com.gedavocat.repository.BarreauRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service pour gérer les Barreaux français
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class BarreauService {

    private final BarreauRepository barreauRepository;

    /**
     * Récupère tous les barreaux actifs (mis en cache pour performance)
     */
    @Cacheable(value = "barreaux", key = "'all_actifs'")
    public List<Barreau> getAllBarreauxActifs() {
        log.debug("Récupération de tous les barreaux actifs");
        return barreauRepository.findByActifTrueOrderByBarreauAsc();
    }

    /**
     * Récupère tous les barreaux
     */
    @Cacheable(value = "barreaux", key = "'all'")
    public List<Barreau> getAllBarreaux() {
        log.debug("Récupération de tous les barreaux");
        return barreauRepository.findAllByOrderByBarreauAsc();
    }

    /**
     * Recherche un barreau par son ID
     */
    public Optional<Barreau> getBarreauById(Long id) {
        return barreauRepository.findById(id);
    }

    /**
     * Recherche un barreau par son nom
     */
    public Optional<Barreau> getBarreauByName(String nom) {
        return barreauRepository.findByBarreauIgnoreCase(nom);
    }

    /**
     * Recherche des barreaux par critères (autocomplete)
     */
    public List<Barreau> searchBarreaux(String search) {
        if (search == null || search.trim().isEmpty()) {
            return getAllBarreauxActifs();
        }
        log.debug("Recherche de barreaux avec critère: {}", search);
        return barreauRepository.searchBarreaux(search.trim());
    }

    /**
     * Récupère les barreaux par région
     */
    @Cacheable(value = "barreaux", key = "'region_' + #region")
    public List<Barreau> getBarreauxByRegion(String region) {
        return barreauRepository.findByRegionOrderByBarreauAsc(region);
    }

    /**
     * Récupère les barreaux par cour d'appel
     */
    @Cacheable(value = "barreaux", key = "'cour_appel_' + #courAppel")
    public List<Barreau> getBarreauxByCourAppel(String courAppel) {
        return barreauRepository.findByCourAppelOrderByBarreauAsc(courAppel);
    }

    /**
     * Récupère toutes les régions distinctes
     */
    @Cacheable(value = "barreaux", key = "'regions'")
    public List<String> getAllRegions() {
        return barreauRepository.findAllRegionsDistinct();
    }

    /**
     * Récupère toutes les cours d'appel distinctes
     */
    @Cacheable(value = "barreaux", key = "'cours_appel'")
    public List<String> getAllCoursAppel() {
        return barreauRepository.findAllCoursAppelDistinct();
    }

    /**
     * Compte le nombre total de barreaux actifs
     */
    public long countBarreauxActifs() {
        return barreauRepository.countByActifTrue();
    }

    /**
     * Vérifie si un barreau existe
     */
    public boolean barreauExists(String nom) {
        return barreauRepository.existsByBarreauIgnoreCase(nom);
    }

    /**
     * Crée un nouveau barreau (admin uniquement)
     */
    @Transactional
    public Barreau createBarreau(Barreau barreau) {
        log.info("Création d'un nouveau barreau: {}", barreau.getBarreau());
        
        if (barreauRepository.existsByBarreauIgnoreCase(barreau.getBarreau())) {
            throw new IllegalArgumentException("Un barreau avec ce nom existe déjà: " + barreau.getBarreau());
        }
        
        if (barreau.getActif() == null) {
            barreau.setActif(true);
        }
        
        return barreauRepository.save(barreau);
    }

    /**
     * Met à jour un barreau (admin uniquement)
     */
    @Transactional
    public Barreau updateBarreau(Long id, Barreau barreauUpdated) {
        log.info("Mise à jour du barreau ID: {}", id);
        
        Barreau barreau = barreauRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Barreau non trouvé: " + id));
        
        // Mise à jour des champs
        if (barreauUpdated.getBarreau() != null) {
            barreau.setBarreau(barreauUpdated.getBarreau());
        }
        if (barreauUpdated.getRegion() != null) {
            barreau.setRegion(barreauUpdated.getRegion());
        }
        if (barreauUpdated.getVilleSiege() != null) {
            barreau.setVilleSiege(barreauUpdated.getVilleSiege());
        }
        if (barreauUpdated.getCourAppel() != null) {
            barreau.setCourAppel(barreauUpdated.getCourAppel());
        }
        if (barreauUpdated.getTribunalJudiciaire() != null) {
            barreau.setTribunalJudiciaire(barreauUpdated.getTribunalJudiciaire());
        }
        if (barreauUpdated.getCourAppelMetropolitaine() != null) {
            barreau.setCourAppelMetropolitaine(barreauUpdated.getCourAppelMetropolitaine());
        }
        if (barreauUpdated.getNombreAvocats() != null) {
            barreau.setNombreAvocats(barreauUpdated.getNombreAvocats());
        }
        if (barreauUpdated.getActif() != null) {
            barreau.setActif(barreauUpdated.getActif());
        }
        
        return barreauRepository.save(barreau);
    }

    /**
     * Désactive un barreau (soft delete)
     */
    @Transactional
    public void desactiverBarreau(Long id) {
        log.info("Désactivation du barreau ID: {}", id);
        
        Barreau barreau = barreauRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Barreau non trouvé: " + id));
        
        barreau.setActif(false);
        barreauRepository.save(barreau);
    }

    /**
     * Réactive un barreau
     */
    @Transactional
    public void reactiverBarreau(Long id) {
        log.info("Réactivation du barreau ID: {}", id);
        
        Barreau barreau = barreauRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Barreau non trouvé: " + id));
        
        barreau.setActif(true);
        barreauRepository.save(barreau);
    }

    /**
     * Supprime définitivement un barreau (admin uniquement, à utiliser avec précaution)
     */
    @Transactional
    public void deleteBarreau(Long id) {
        log.warn("Suppression définitive du barreau ID: {}", id);
        barreauRepository.deleteById(id);
    }

    /**
     * Retourne des statistiques sur les barreaux
     */
    public BarreauStats getStatistiques() {
        long total = barreauRepository.count();
        long actifs = barreauRepository.countByActifTrue();
        int nbRegions = getAllRegions().size();
        int nbCoursAppel = getAllCoursAppel().size();
        
        return new BarreauStats(total, actifs, nbRegions, nbCoursAppel);
    }

    /**
     * Classe interne pour les statistiques
     */
    public record BarreauStats(
        long totalBarreaux,
        long barreauxActifs,
        int nombreRegions,
        int nombreCoursAppel
    ) {}
}
