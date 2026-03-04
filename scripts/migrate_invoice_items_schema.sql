-- ===================================================================
-- Migration: invoice_items table schema update
-- ===================================================================
-- Description: Update invoice_items table to use detailed HT/TVA/TTC columns
--              instead of simplified unit_price/total_price columns
-- Author: System Migration
-- Date: 2026-03-03
-- ===================================================================

USE gedavocat;

-- Step 1: Add new columns if they don't exist
ALTER TABLE invoice_items 
    ADD COLUMN IF NOT EXISTS unit_price_ht DECIMAL(10,2) NULL AFTER quantity,
    ADD COLUMN IF NOT EXISTS tva_rate DECIMAL(5,2) NULL AFTER unit_price_ht,
    ADD COLUMN IF NOT EXISTS total_ht DECIMAL(10,2) NULL AFTER tva_rate,
    ADD COLUMN IF NOT EXISTS total_tva DECIMAL(10,2) NULL AFTER total_ht,
    ADD COLUMN IF NOT EXISTS total_ttc DECIMAL(10,2) NULL AFTER total_tva,
    ADD COLUMN IF NOT EXISTS display_order INT NULL AFTER total_ttc;

-- Step 2: Migrate data from old columns to new columns (if old data exists)
-- Assume 20% TVA rate if not specified
UPDATE invoice_items 
SET 
    unit_price_ht = COALESCE(unit_price_ht, unit_price),
    tva_rate = COALESCE(tva_rate, 20.00),
    total_ht = COALESCE(total_ht, unit_price * quantity),
    total_tva = COALESCE(total_tva, (unit_price * quantity * 0.20)),
    total_ttc = COALESCE(total_ttc, (unit_price * quantity * 1.20))
WHERE unit_price_ht IS NULL;

-- Step 3: Make new columns NOT NULL after data migration
ALTER TABLE invoice_items 
    MODIFY COLUMN unit_price_ht DECIMAL(10,2) NOT NULL,
    MODIFY COLUMN tva_rate DECIMAL(5,2) NOT NULL DEFAULT 20.00,
    MODIFY COLUMN total_ht DECIMAL(10,2) NULL,
    MODIFY COLUMN total_tva DECIMAL(10,2) NULL,
    MODIFY COLUMN total_ttc DECIMAL(10,2) NULL;

-- Step 4: Drop old columns (after verifying data migration)
-- IMPORTANT: Only run this after confirming data is properly migrated
-- ALTER TABLE invoice_items 
--     DROP COLUMN IF EXISTS unit_price,
--     DROP COLUMN IF EXISTS total_price;

-- Verification query
SELECT 
    id,
    description,
    quantity,
    unit_price_ht,
    tva_rate,
    total_ht,
    total_tva,
    total_ttc
FROM invoice_items
LIMIT 10;

-- Show table structure
DESCRIBE invoice_items;
