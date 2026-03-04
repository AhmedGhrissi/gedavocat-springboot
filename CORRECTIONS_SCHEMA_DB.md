# Corrections apportées au schéma de base de données

**Date:** 3 mars 2026  
**Statut:** ✅ Application redémarrée avec succès

## 🔍 Problèmes identifiés

Trois entités principales avaient des problèmes de mapping entre le schéma SQL et les entités Java :

### 1. **Invoice** (Factures)
- ❌ Anciennes colonnes SQL: `invoice_date`, `total_ht`, `total_tva`, `total_ttc`
- ✅ Nouvelles colonnes SQL: `issue_date`, `subtotal_amount`, `tax_amount`, `total_amount`
- ❌ Manquants: `paid_amount`, `payment_status`, `case_id`

### 2. **Appointment** (Rendez-vous)
- ❌ Anciennes colonnes SQL: `appointment_date`, `end_date`
- ✅ Nouvelles colonnes SQL: `start_time`, `end_time`
- ❌ Manquants: `is_all_day`, `firm_id`

### 3. **Case** (Dossiers)
- ⚠️ Conflit de colonnes: `name` ET `title`
- ⚠️ Contrainte NOT NULL sur `reference` (auto-généré)

## ✅ Corrections appliquées

### Modifications des entités Java

#### 📄 [Invoice.java](../src/main/java/com/gedavocat/model/Invoice.java)
```java
// Nouvelles colonnes (avec valeurs par défaut)
@Column(name = "issue_date")
private LocalDate invoiceDate = LocalDate.now();

@Column(name = "subtotal_amount", precision = 10, scale = 2)
private BigDecimal totalHT = BigDecimal.ZERO;

@Column(name = "tax_amount", precision = 10, scale = 2)
private BigDecimal totalTVA = BigDecimal.ZERO;

@Column(name = "total_amount", precision = 10, scale = 2)
private BigDecimal totalTTC = BigDecimal.ZERO;

@Column(name = "paid_amount", precision = 10, scale = 2)
private BigDecimal paidAmount = BigDecimal.ZERO;

@Enumerated(EnumType.STRING)
@Column(name = "payment_status", length = 50)
private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

// Relation vers le dossier
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "case_id")
private Case caseEntity;

// Anciennes colonnes (lecture seule, ignorées lors INSERT/UPDATE)
@Column(name = "invoice_date", insertable = false, updatable = false)
private LocalDate legacyInvoiceDate;

@Column(name = "total_ht", insertable = false, updatable = false)
private BigDecimal legacyTotalHT;

// etc.
```

#### 📄 [Appointment.java](../src/main/java/com/gedavocat/model/Appointment.java)
```java
// Nouvelles colonnes (avec valeurs par défaut)
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

// Ancienne colonne (lecture seule)
@Column(name = "appointment_date", insertable = false, updatable = false)
private LocalDateTime legacyAppointmentDate;
```

### Scripts SQL créés

#### 📄 [fix_database_schema.sql](fix_database_schema.sql)
Script complet pour recréer les tables `invoices`, `invoice_items` et `appointments` avec le bon schéma.

⚠️ **ATTENTION:** Supprime les données existantes dans ces tables.

#### 📄 [fix_database_schema.ps1](fix_database_schema.ps1)
Script PowerShell pour exécuter facilement la correction SQL depuis Windows.

#### 📄 [V5__fix_invoice_table_schema.sql](../src/main/resources/db/migration/V5__fix_invoice_table_schema.sql)
Migration Flyway pour une migration progressive (non destructive) de la table `invoices`.

## 🚀 État actuel

### ✅ Solution temporaire appliquée
- Les entités Java mappent les **anciennes ET nouvelles colonnes**
- Les anciennes colonnes sont en mode `insertable=false, updatable=false`
- L'application fonctionne avec le schéma actuel de la base de données
- **Statut:** Application démarrée avec succès ✅

### ⚠️ Solution recommandée (à appliquer)
Pour un schéma propre et optimisé, exécuter le script de correction SQL :

```powershell
cd scripts
.\fix_database_schema.ps1
```

Ou manuellement :
```bash
mysql -ugedavocat -p gedavocat < scripts/fix_database_schema.sql
```

## 📋 Prochaines étapes recommandées

1. **Tester la création de factures, rendez-vous et dossiers** avec la solution temporaire
2. **Sauvegarder les données existantes** si nécessaire
3. **Exécuter le script SQL** pour nettoyer le schéma
4. **Redémarrer l'application** après la correction
5. **Migrer vers Flyway** pour éviter ces problèmes à l'avenir

## 🔧 Migration Flyway (Recommandation future)

Pour éviter ces problèmes à l'avenir :

1. Ajouter Flyway au `pom.xml`
2. Configurer `spring.jpa.hibernate.ddl-auto=validate`
3. Gérer les migrations via scripts versionnés dans `db/migration/`

## 📊 Récapitulatif

| Table | Problème | Correction | Statut |
|-------|----------|------------|--------|
| **invoices** | Colonnes dupliquées | Mapping legacy columns | ✅ Fonctionne |
| **appointments** | Colonnes dupliquées + firm_id manquant | Mapping legacy + ajout firm | ✅ Fonctionne |
| **cases** | Colonnes title/name | ALTER TABLE NULL | ⚠️ Partiel |
| **invoice_items** | - | OK | ✅ OK |

## 📝 Notes importantes

- **Ne pas modifier `ddl-auto`** en production (risque de perte de données)
- **Tester d'abord en développement** avant d'appliquer en production
- **Les anciennes colonnes peuvent être supprimées** après vérification que tout fonctionne
- **Utiliser Flyway** pour les futures migrations

---

**Dernière mise à jour :** 3 mars 2026, 10:37  
**Auteur :** GitHub Copilot  
**Version application :** 1.0.0
