#!/bin/bash
# ===================================================================
# Script de génération du schéma BDD pour initialisation
# ===================================================================

echo "Génération du schéma SQL depuis les entités JPA..."

# Temporairement modifier ddl-auto pour générer le schéma
mvn spring-boot:run \
  -Dspring-boot.run.arguments=--spring.jpa.hibernate.ddl-auto=create \
  -Dspring-boot.run.arguments=--spring.jpa.properties.javax.persistence.schema-generation.scripts.action=create \
  -Dspring-boot.run.arguments=--spring.jpa.properties.javax.persistence.schema-generation.scripts.create-target=docker/init/01-schema.sql \
  -Dspring-boot.run.arguments=--spring.profiles.active=h2

echo "Schéma généré dans docker/init/01-schema.sql"
