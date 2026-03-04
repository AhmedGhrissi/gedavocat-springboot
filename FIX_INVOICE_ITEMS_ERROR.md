# QUICK FIX SUMMARY - invoice_items SQL Error

## Problem
```
java.sql.SQLException: Field 'unit_price' doesn't have a default value
```

## Root Cause
Database schema mismatch:
- **Database**: Has old columns `unit_price` and `total_price`  
- **Entity (InvoiceItem.java)**: Expects `unit_price_ht`, `tva_rate`, `total_ht`, `total_tva`, `total_ttc`

## Solution - Choose ONE method:

### ✅ METHOD 1: Web-based Migration (EASIEST)
1. Start the application (ignore the errors for now)
2. Login as admin
3. Call this endpoint:
   ```
   POST http://localhost:8092/api/admin/migration/invoice-items-schema
   ```
4. Restart the application

### ✅ METHOD 2: MySQL Workbench
1. Open MySQL Workbench
2. Connect to `localhost:3306`
3. Open file: `scripts/migrate_invoice_items_schema.sql`
4. Execute the script
5. Restart the application

### ✅ METHOD 3: Command Line (if MySQL installed)
```bash
cd C:\Users\el_ch\git\gedavocat-springboot\scripts
mysql -u root -proot gedavocat < migrate_invoice_items_schema.sql
```

### ✅ METHOD 4: PowerShell Script
```powershell
cd C:\Users\el_ch\git\gedavocat-springboot\scripts
powershell -ExecutionPolicy Bypass -File run_migration.ps1
```

## Files Created/Updated

### New Files:
- ✅ `scripts/migrate_invoice_items_schema.sql` - Migration SQL script
- ✅ `scripts/run_migration.ps1` - PowerShell migration runner
- ✅ `src/main/java/com/gedavocat/controller/DatabaseMigrationController.java` - Web migration endpoint
- ✅ `MIGRATION_GUIDE_INVOICE_ITEMS.md` - Detailed migration guide

### Updated Files:
- ✅ `docker/init/00-schema-complete.sql` - Fixed for fresh installs
- ✅ `scripts/fix_database_schema.sql` - Updated schema definition
- ✅ `.classpath` - Fixed JRE System Library issue (unrelated but also fixed)

## What the Migration Does
1. Adds new columns: `unit_price_ht`, `tva_rate`, `total_ht`, `total_tva`, `total_ttc`, `display_order`
2. Migrates existing data (if any) from old columns to new ones
3. Sets constraints (NOT NULL, DEFAULT values)
4. Keeps old columns temporarily for safety

## Verification
After migration, restart the app and check:
- No more SQL errors in logs
- Can create invoices with line items
- Tax calculations work (HT, TVA, TTC)

## Need Help?
See `MIGRATION_GUIDE_INVOICE_ITEMS.md` for detailed troubleshooting.
