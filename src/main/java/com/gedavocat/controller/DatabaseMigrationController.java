package com.gedavocat.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller pour exécuter les migrations de base de données
 * À UTILISER UNIQUEMENT EN DÉVELOPPEMENT / AVEC PRÉCAUTION
 */
@RestController
@RequestMapping("/api/admin/migration")
@PreAuthorize("hasRole('ADMIN')")
@Profile("dev")
public class DatabaseMigrationController {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMigrationController.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostMapping("/invoice-items-schema")
    public ResponseEntity<Map<String, Object>> migrateInvoiceItemsSchema() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Step 1: Add new columns if they don't exist
            try {
                jdbcTemplate.execute(
                    "ALTER TABLE invoice_items " +
                    "ADD COLUMN IF NOT EXISTS unit_price_ht DECIMAL(10,2) NULL AFTER quantity"
                );
                result.put("step1_unit_price_ht", "added");
            } catch (Exception e) {
                log.error("Migration step1 unit_price_ht", e);
                result.put("step1_unit_price_ht", "already exists or error");
            }
            
            try {
                jdbcTemplate.execute(
                    "ALTER TABLE invoice_items " +
                    "ADD COLUMN IF NOT EXISTS tva_rate DECIMAL(5,2) NULL AFTER unit_price_ht"
                );
                result.put("step1_tva_rate", "added");
            } catch (Exception e) {
                log.error("Migration step1 tva_rate", e);
                result.put("step1_tva_rate", "already exists or error");
            }
            
            try {
                jdbcTemplate.execute(
                    "ALTER TABLE invoice_items " +
                    "ADD COLUMN IF NOT EXISTS total_ht DECIMAL(10,2) NULL AFTER tva_rate"
                );
                result.put("step1_total_ht", "added");
            } catch (Exception e) {
                log.error("Migration step1 total_ht", e);
                result.put("step1_total_ht", "already exists or error");
            }
            
            try {
                jdbcTemplate.execute(
                    "ALTER TABLE invoice_items " +
                    "ADD COLUMN IF NOT EXISTS total_tva DECIMAL(10,2) NULL AFTER total_ht"
                );
                result.put("step1_total_tva", "added");
            } catch (Exception e) {
                log.error("Migration step1 total_tva", e);
                result.put("step1_total_tva", "already exists or error");
            }
            
            try {
                jdbcTemplate.execute(
                    "ALTER TABLE invoice_items " +
                    "ADD COLUMN IF NOT EXISTS total_ttc DECIMAL(10,2) NULL AFTER total_tva"
                );
                result.put("step1_total_ttc", "added");
            } catch (Exception e) {
                log.error("Migration step1 total_ttc", e);
                result.put("step1_total_ttc", "already exists or error");
            }
            
            try {
                jdbcTemplate.execute(
                    "ALTER TABLE invoice_items " +
                    "ADD COLUMN IF NOT EXISTS display_order INT NULL AFTER total_ttc"
                );
                result.put("step1_display_order", "added");
            } catch (Exception e) {
                log.error("Migration step1 display_order", e);
                result.put("step1_display_order", "already exists or error");
            }

            // Step 2: Migrate existing data if old columns exist
            try {
                jdbcTemplate.execute(
                    "UPDATE invoice_items " +
                    "SET " +
                    "unit_price_ht = COALESCE(unit_price_ht, unit_price), " +
                    "tva_rate = COALESCE(tva_rate, 20.00), " +
                    "total_ht = COALESCE(total_ht, unit_price * quantity), " +
                    "total_tva = COALESCE(total_tva, (unit_price * quantity * 0.20)), " +
                    "total_ttc = COALESCE(total_ttc, (unit_price * quantity * 1.20)) " +
                    "WHERE unit_price_ht IS NULL AND unit_price IS NOT NULL"
                );
                Integer rowsUpdated = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM invoice_items WHERE unit_price_ht IS NOT NULL", 
                    Integer.class
                );
                result.put("step2_data_migration", "migrated " + (rowsUpdated != null ? rowsUpdated : 0) + " rows");
            } catch (Exception e) {
                log.error("Migration step2 data_migration", e);
                result.put("step2_data_migration", "error or no old data");
            }

            // Step 3: Make new columns NOT NULL
            try {
                jdbcTemplate.execute(
                    "ALTER TABLE invoice_items " +
                    "MODIFY COLUMN unit_price_ht DECIMAL(10,2) NOT NULL"
                );
                result.put("step3_unit_price_ht_not_null", "modified");
            } catch (Exception e) {
                log.error("Migration step3 unit_price_ht_not_null", e);
                result.put("step3_unit_price_ht_not_null", "error");
            }
            
            try {
                jdbcTemplate.execute(
                    "ALTER TABLE invoice_items " +
                    "MODIFY COLUMN tva_rate DECIMAL(5,2) NOT NULL DEFAULT 20.00"
                );
                result.put("step3_tva_rate_not_null", "modified");
            } catch (Exception e) {
                log.error("Migration step3 tva_rate_not_null", e);
                result.put("step3_tva_rate_not_null", "error");
            }

            result.put("status", "success");
            result.put("message", "Migration completed. Check individual steps for details.");
            
        } catch (Exception e) {
            log.error("Erreur migration invoice items", e);
            result.put("status", "error");
            result.put("message", "Erreur lors de la migration");
            return ResponseEntity.internalServerError().body(result);
        }
        
        return ResponseEntity.ok(result);
    }

    @GetMapping("/invoice-items-schema/verify")
    public ResponseEntity<Map<String, Object>> verifyInvoiceItemsSchema() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Check table structure
            var columns = jdbcTemplate.queryForList(
                "SHOW COLUMNS FROM invoice_items"
            );
            result.put("columns", columns);
            
            // Count rows
            Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM invoice_items", 
                Integer.class
            );
            result.put("row_count", rowCount);
            
            result.put("status", "success");
        } catch (Exception e) {
            log.error("Erreur vérification schema invoice items", e);
            result.put("status", "error");
            result.put("message", "Erreur lors de la vérification");
            return ResponseEntity.internalServerError().body(result);
        }
        
        return ResponseEntity.ok(result);
    }
}
