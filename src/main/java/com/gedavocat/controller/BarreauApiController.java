package com.gedavocat.controller;

import com.gedavocat.model.Barreau;
import com.gedavocat.service.BarreauService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API REST pour gérer les Barreaux
 */
@RestController
@RequestMapping("/api/barreaux")
@RequiredArgsConstructor
public class BarreauApiController {

    private final BarreauService barreauService;

    /**
     * GET /api/barreaux - Récupère tous les barreaux actifs
     */
    @GetMapping
    public ResponseEntity<List<Barreau>> getAllBarreaux() {
        List<Barreau> barreaux = barreauService.getAllBarreauxActifs();
        return ResponseEntity.ok(barreaux);
    }

    /**
     * GET /api/barreaux/search?q=Paris - Recherche de barreaux
     */
    @GetMapping("/search")
    public ResponseEntity<List<Barreau>> searchBarreaux(@RequestParam("q") String query) {
        List<Barreau> barreaux = barreauService.searchBarreaux(query);
        return ResponseEntity.ok(barreaux);
    }

    /**
     * GET /api/barreaux/{id} - Récupère un barreau par son ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Barreau> getBarreauById(@PathVariable Long id) {
        return barreauService.getBarreauById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/barreaux/region/{region} - Barreaux par région
     */
    @GetMapping("/region/{region}")
    public ResponseEntity<List<Barreau>> getBarreauxByRegion(@PathVariable String region) {
        List<Barreau> barreaux = barreauService.getBarreauxByRegion(region);
        return ResponseEntity.ok(barreaux);
    }

    /**
     * GET /api/barreaux/regions - Liste des régions
     */
    @GetMapping("/regions")
    public ResponseEntity<List<String>> getAllRegions() {
        List<String> regions = barreauService.getAllRegions();
        return ResponseEntity.ok(regions);
    }

    /**
     * GET /api/barreaux/stats - Statistiques sur les barreaux
     */
    @GetMapping("/stats")
    public ResponseEntity<BarreauService.BarreauStats> getStats() {
        BarreauService.BarreauStats stats = barreauService.getStatistiques();
        return ResponseEntity.ok(stats);
    }
}
