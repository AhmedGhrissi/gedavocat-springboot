# ============================================================
# DocAvocat — Dockerfile Production (Multi-stage)
#
# Stage 1 : Build Maven (JDK 17) → génère le .jar
# Stage 2 : Runtime (JRE 17 slim) → image minimale sécurisée
#
# Sécurité :
# - Utilisateur non-root (docavocat, UID 1001)
# - Image JRE-only (pas de compilateur en prod)
# - No-new-privileges, capabilities drop dans compose
# - Volumes séparés pour données persistantes
# ============================================================

# ── Stage 1 : Build ─────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# Copier d'abord le POM pour profiter du cache Docker sur les dépendances
COPY pom.xml .
RUN mvn dependency:go-offline -B --no-transfer-progress

# Copier le code source et construire
COPY src ./src
RUN mvn package -DskipTests -B --no-transfer-progress \
    && mv target/*.jar target/app.jar

# ── Stage 2 : Runtime ───────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy

# Métadonnées
LABEL maintainer="DocAvocat <contact@docavocat.fr>"
LABEL description="GED Avocat - Système de Gestion Électronique de Documents pour Avocats"
LABEL version="1.0.0"

# Créer l'utilisateur applicatif (non-root)
RUN groupadd -r docavocat --gid=1001 \
    && useradd -r -g docavocat --uid=1001 -d /app -s /sbin/nologin docavocat

# Installer curl pour le healthcheck, puis nettoyer
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Répertoire de l'application
WORKDIR /app

# Copier le JAR et l'entrypoint depuis le stage builder
COPY --from=builder /build/target/app.jar /app/app.jar
COPY docker/entrypoint.sh /app/entrypoint.sh

# Créer les répertoires de données avec les bons droits
RUN mkdir -p /opt/gedavocat/uploads/documents \
             /opt/gedavocat/uploads/signatures \
             /opt/gedavocat/uploads/invoices \
             /opt/gedavocat/temp \
             /opt/gedavocat/logs \
             /opt/gedavocat/backups \
             /app/config/keys \
    && chown -R docavocat:docavocat /opt/gedavocat /app \
    && chmod +x /app/entrypoint.sh \
    && chmod 600 /app/app.jar

# Exposer le port applicatif
EXPOSE 8080

# Basculer vers l'utilisateur non-root
USER docavocat

# Healthcheck intégré
HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
    CMD curl -f http://localhost:8080/login || exit 1

ENTRYPOINT ["/app/entrypoint.sh"]
