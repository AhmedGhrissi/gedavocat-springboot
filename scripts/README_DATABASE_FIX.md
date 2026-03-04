# Correction du schéma de base de données

## Problème

Les erreurs suivantes se produisent lors de la création de **factures**, **rendez-vous** et **dossiers**:

```
Field 'invoice_date' doesn't have a default value
Field 'total_amount' doesn't have a default value
Field 'appointment_date' doesn't have a default value
Field 'start_time' doesn't have a default value
```

## Cause

Le schéma de la base de données contient des **anciennes colonnes** qui ne sont plus mappées dans les entités Java:

### Table `invoices`
- ❌ Anciennes: `invoice_date`, `total_ht`, `total_tva`, `total_ttc`
- ✅ Nouvelles: `issue_date`, `subtotal_amount`, `tax_amount`, `total_amount`

### Table `appointments`
- ❌ Anciennes: `appointment_date`, `end_date`
- ✅ Nouvelles: `start_time`, `end_time`
- ❌ Manquant: `is_all_day`, `firm_id`

### Table `cases`
- ⚠️ Conflit: colonnes `name` ET `title` (legacy)
- ⚠️ Conflit: `reference` avec contrainte NOT NULL mais généré automatiquement

Hibernate en mode `ddl-auto=update` a ajouté les nouvelles colonnes mais **n'a pas supprimé les anciennes**.
Les anciennes colonnes ont des contraintes `NOT NULL` sans valeur par défaut, ce qui cause l'échec des INSERT.

## Solutions

### Solution 1: Corrections appliquées dans les entités (Temporaire)

Les entités Java ont été modifiées pour mapper les anciennes colonnes en mode `insertable=false, updatable=false`:

#### Invoice.java
```java
// Nouvelles colonnes (utilisées)
@Column(name = "issue_date")
private LocalDate invoiceDate = LocalDate.now();

@Column(name = "subtotal_amount", precision = 10, scale = 2)
private BigDecimal totalHT = BigDecimal.ZERO;

@Column(name = "total_amount", precision = 10, scale = 2)
private BigDecimal totalTTC = BigDecimal.ZERO;

// Anciennes colonnes (ignorées lors des INSERT/UPDATE)
@Column(name = "invoice_date", insertable = false, updatable = false)
private LocalDate legacyInvoiceDate;

@Column(name = "total_ht", insertable = false, updatable = false)
private BigDecimal legacyTotalHT;
```

#### Appointment.java
```java
// Nouvelles colonnes (utilisées)
@Column(name = "start_time")
private LocalDateTime appointmentDate = LocalDateTime.now();

@Column(name = "end_time")
private LocalDateTime endDate;

@Column(name = "is_all_day")
private Boolean isAllDay = false;

// Multi-tenant
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "firm_id", nullable = false)
private Firm firm;

// Ancienne colonne (ignorée)
@Column(name = "appointment_date", insertable = false, updatable = false)
private LocalDateTime legacyAppointmentDate;
```

**Cette solution permet à l'application de fonctionner même avec les anciennes colonnes**, mais elle n'est pas optimale.

### Solution 2: Correction du schéma SQL (Recommandée)

Exécutez le script SQL de migration pour supprimer les anciennes colonnes et recréer les tables avec le bon schéma:

#### Option A: Via PowerShell (Windows)
```powershell
cd scripts
.\fix_database_schema.ps1
```

#### Option B: Via MySQL client directement
```bash
mysql -ugedavocat -p gedavocat < scripts/fix_database_schema.sql
```

⚠️ **ATTENTION**: Ce script supprime les données existantes dans les tables `invoices` et `invoice_items`.

### Solution 3: Recréation complète du schéma (Développement uniquement)

Pour l'environnement de développement, vous pouvez recréer tout le schéma:

1. Modifiez `application.properties`:
   ```properties
   spring.jpa.hibernate.ddl-auto=create-drop
   ```

2. Redémarrez l'application:
   ```bash
   mvn spring-boot:run
   ```

3. Remettez `spring.jpa.hibernate.ddl-auto=update` après le redémarrage

⚠️ **ATTENTION**: Cette méthode supprime TOUTES les données de TOUTES les tables.

## Schéma correct

### Table `invoices`
```sql
CREATE TABLE invoices (
    id VARCHAR(36) PRIMARY KEY,
    invoice_number VARCHAR(50) NOT NULL UNIQUE,
    entity_version BIGINT,
    
    -- Relations
    client_id VARCHAR(36) NOT NULL,
    case_id VARCHAR(36),
    firm_id VARCHAR(36) NOT NULL,
    
    -- Dates
    issue_date DATE,
    due_date DATE,
    paid_date DATE,
    
    -- Statuts
    status VARCHAR(50) DEFAULT 'DRAFT',
    payment_status VARCHAR(50) DEFAULT 'UNPAID',
    
    -- Montants
    subtotal_amount DECIMAL(10,2) DEFAULT 0.00,
    tax_amount DECIMAL(10,2) DEFAULT 0.00,
    total_amount DECIMAL(10,2) DEFAULT 0.00,
    paid_amount DECIMAL(10,2) DEFAULT 0.00,
    
    -- Autres
    currency VARCHAR(3) DEFAULT 'EUR',
    notes TEXT,
    payment_method VARCHAR(50),
    document_url VARCHAR(500),
    
    -- Timestamps
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    
    FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE,
    FOREIGN KEY (case_id) REFERENCES cases(id) ON DELETE SET NULL,
    FOREIGN KEY (firm_id) REFERENCES firms(id) ON DELETE CASCADE
);
```

## Vérification

Pour vérifier que le schéma est correct:

```sql
-- Vérifier la structure de la table invoices
DESCRIBE invoices;

-- Vérifier qu'il n'y a pas les anciennes colonnes
SHOW CREATE TABLE invoices;
```

## Migration Flyway (Recommandation future)

Pour éviter ces problèmes à l'avenir, il est recommandé d'utiliser Flyway pour gérer les migrations de base de données:

1. Ajouter la dépendance Flyway dans `pom.xml`
2. Configurer `spring.jpa.hibernate.ddl-auto=validate`
3. Créer des scripts de migration versionnés dans `src/main/resources/db/migration/`

Un script de migration a déjà été créé: `src/main/resources/db/migration/V5__fix_invoice_table_schema.sql`
