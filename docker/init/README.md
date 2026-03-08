# Dump d'Initialisation de la Base de Données

## Fichier : 01-gedavocat-dump.sql

Ce fichier contient le dump complet de la base de données DocAvocat :
- **22 tables** avec leur structure
- Toutes les données existantes au 9 mars 2026
- ~1000 lignes SQL

## Utilisation Automatique

Ce fichier est **automatiquement chargé** par MySQL lors du premier démarrage du conteneur Docker (fresh install uniquement).

Docker execute tous les fichiers `.sql` dans `/docker-entrypoint-initdb.d/` par ordre alphabétique lors de l'initialisation d'un nouveau volume.

## Utilisation Manuelle

Si vous voulez importer ce dump dans une base existante :

### Localement (Docker) :
```bash
docker exec -i docavocat-mysql mysql -u gedavocat -p gedavocat < 01-gedavocat-dump.sql
```

### Sur serveur distant :
```bash
# Depuis votre machine locale
.\scripts\import-dump-remote.ps1 -Server "docavocat.fr" -User "root"
```

## Réinitialisation Complète

Pour repartir de zéro avec le dump :

```bash
cd docker
docker-compose down -v     # Supprime les volumes
docker-compose up -d mysql # Recrée et charge le dump automatiquement
```

⚠️ **ATTENTION** : `docker-compose down -v` supprime TOUTES les données !

## Source

Ce dump a été généré depuis la base `gedavocat` :
- Base : `gedavocat`
- Utilisateur : `gedavocat`

## Tables Incluses

```
appointments, audit_logs, barreaux_france, case_assignments,
case_share_links, cases, clients, document_shares, documents,
firm_members, firms, invoice_items, invoices, labft_checks,
notifications, payments, permissions, refresh_tokens,
rpva_communications, signature_events, signatures, users
```

## Date de Création

9 mars 2026

## Maintenance

Pour mettre à jour ce dump avec les données actuelles :

```bash
# Exporter depuis le conteneur Docker local
docker exec docavocat-mysql mysqldump -u gedavocat -p'DocAvocat2026!DevDB' gedavocat > docker/init/01-gedavocat-dump.sql

# Ou utiliser le script
.\scripts\export-schema.ps1
```
