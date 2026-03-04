# Migration Guide: invoice_items Table Schema Update

## Problem Description

The application is failing with the error:
```
java.sql.SQLException: Field 'unit_price' doesn't have a default value
```

This occurs because there's a mismatch between:
- **Database Schema**: Has old columns `unit_price` and `total_price`
- **Entity Model** (`InvoiceItem.java`): Uses new columns `unit_price_ht`, `tva_rate`, `total_ht`, `total_tva`, `total_ttc`

## Solution

You need to update the database schema to match the entity model.

## Option 1: Using the Migration Controller (RECOMMENDED)

If you can start the application and log in as an admin:

1. **Start the application** (it may show errors but should start)
2. **Login as an admin user**
3. **Execute the migration** via REST API:

   ```bash
   # First, verify current schema
   curl -X GET http://localhost:8092/api/admin/migration/invoice-items-schema/verify \
     -H "Authorization: Bearer YOUR_JWT_TOKEN"
   
   # Then run the migration
   curl -X POST http://localhost:8092/api/admin/migration/invoice-items-schema \
     -H "Authorization: Bearer YOUR_JWT_TOKEN"
   ```

4. **Restart the application** to ensure all changes are applied

## Option 2: Using MySQL Command Line

If MySQL is installed and accessible:

```bash
# Navigate to project directory
cd C:\Users\el_ch\git\gedavocat-springboot

# Run the migration script
mysql -u root -proot gedavocat < scripts\migrate_invoice_items_schema.sql
```

## Option 3: Using Docker (if MySQL is in Docker)

```bash
# Find the MySQL container name
docker ps | findstr mysql

# Execute the migration script
docker exec -i <mysql-container-name> mysql -u root -proot gedavocat < scripts\migrate_invoice_items_schema.sql

# Or connect interactively
docker exec -it <mysql-container-name> mysql -u root -proot gedavocat
```

## Option 4: Using MySQL Workbench or Another GUI Tool

1. Open MySQL Workbench and connect to your database (localhost:3306)
2. Select the `gedavocat` database
3. Open the file: `scripts/migrate_invoice_items_schema.sql`
4. Execute the script

## Option 5: Manual SQL Execution

If you can connect to MySQL through any means, execute these commands:

```sql
USE gedavocat;

-- Add new columns
ALTER TABLE invoice_items ADD COLUMN IF NOT EXISTS unit_price_ht DECIMAL(10,2) NULL AFTER quantity;
ALTER TABLE invoice_items ADD COLUMN IF NOT EXISTS tva_rate DECIMAL(5,2) NULL AFTER unit_price_ht;
ALTER TABLE invoice_items ADD COLUMN IF NOT EXISTS total_ht DECIMAL(10,2) NULL AFTER tva_rate;
ALTER TABLE invoice_items ADD COLUMN IF NOT EXISTS total_tva DECIMAL(10,2) NULL AFTER total_ht;
ALTER TABLE invoice_items ADD COLUMN IF NOT EXISTS total_ttc DECIMAL(10,2) NULL AFTER total_tva;
ALTER TABLE invoice_items ADD COLUMN IF NOT EXISTS display_order INT NULL AFTER total_ttc;

-- Migrate existing data (if any)
UPDATE invoice_items 
SET 
    unit_price_ht = COALESCE(unit_price_ht, unit_price),
    tva_rate = COALESCE(tva_rate, 20.00),
    total_ht = COALESCE(total_ht, unit_price * quantity),
    total_tva = COALESCE(total_tva, (unit_price * quantity * 0.20)),
    total_ttc = COALESCE(total_ttc, (unit_price * quantity * 1.20))
WHERE unit_price_ht IS NULL AND unit_price IS NOT NULL;

-- Make new columns NOT NULL
ALTER TABLE invoice_items MODIFY COLUMN unit_price_ht DECIMAL(10,2) NOT NULL;
ALTER TABLE invoice_items MODIFY COLUMN tva_rate DECIMAL(5,2) NOT NULL DEFAULT 20.00;

-- Verify the changes
DESCRIBE invoice_items;
```

## Option 6: Drop and Recreate (DEVELOPMENT ONLY - LOSES ALL DATA)

⚠️ **WARNING**: This will delete all invoice items!

```sql
USE gedavocat;
DROP TABLE IF EXISTS invoice_items;
-- Then restart the application and Hibernate will recreate the table
```

## Verification

After migration, verify the schema:

```sql
USE gedavocat;
DESCRIBE invoice_items;
```

Expected columns:
- `id` VARCHAR(36)
- `invoice_id` VARCHAR(36)
- `description` VARCHAR(500)
- `quantity` DECIMAL(10,2)
- `unit_price_ht` DECIMAL(10,2) NOT NULL
- `tva_rate` DECIMAL(5,2) NOT NULL
- `total_ht` DECIMAL(10,2)
- `total_tva` DECIMAL(10,2)
- `total_ttc` DECIMAL(10,2)
- `display_order` INT
- `created_at` DATETIME(6)

## Files Updated

The following files have been updated to reflect the new schema:
- ✅ `docker/init/00-schema-complete.sql` - Initial schema for fresh installations
- ✅ `scripts/fix_database_schema.sql` - Schema fix script
- ✅ `scripts/migrate_invoice_items_schema.sql` - NEW migration script
- ✅ `src/main/java/com/gedavocat/controller/DatabaseMigrationController.java` - NEW web-based migration endpoint

## Troubleshooting

### Problem: Cannot connect to MySQL
- Check if MySQL service is running: `services.msc` → MySQL
- Check if Docker container is running: `docker ps`
- Verify connection in `application.properties`: `spring.datasource.url`

### Problem: Migration fails with "column already exists"
- This is normal if you ran the migration before
- Check the verification query to see current schema

### Problem: Application still shows errors after migration
- Restart the application completely
- Clear the `target/classes` directory: `mvn clean`
- Rebuild: `mvn clean install -DskipTests`

## Next Steps

After successful migration:
1. Restart the application
2. Test creating an invoice with line items
3. Verify calculations are working correctly (HT, TVA, TTC)

## Support

If you encounter issues, check:
- Application logs: Look for SQL errors or Hibernate schema validation errors
- MySQL error logs
- Contact system administrator
