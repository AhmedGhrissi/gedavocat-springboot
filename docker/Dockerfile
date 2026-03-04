# ============================================================
# DocAvocat — Dockerfile multi-stage
#
# Stage 1 (builder) : Compilation Maven
#   • Tourne sur la plateforme NATIVE du runner CI (x86_64 → rapide)
#   • Produit le JAR (bytecode Java = architecture-indépendant)
#
# Stage 2 (runtime) : Image de production JRE
#   • Cible linux/arm64 (serveur Hetzner aarch64)
#   • Utilisateur non-root, curl pour l'healthcheck
# ============================================================

# ── Stage 1 : Build ─────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# Télécharger les dépendances en premier (layer cachée si pom.xml inchangé)
COPY pom.xml .
RUN mvn dependency:go-offline --batch-mode -q

# Compiler et packager
COPY src/ src/
RUN mvn clean package -DskipTests --batch-mode -q

# ── Stage 2 : Runtime ───────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy

LABEL app="docavocat"
LABEL maintainer="contact@docavocat.fr"

# Utilisateur dédié non-root
RUN groupadd -r docavocat && useradd -r -g docavocat docavocat

# curl pour le healthcheck
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Dossiers de données (montés via volumes dans docker-compose)
RUN mkdir -p \
        /opt/gedavocat/uploads/documents \
        /opt/gedavocat/uploads/signatures \
        /opt/gedavocat/uploads/invoices \
        /opt/gedavocat/logs \
        /opt/gedavocat/temp \
        /opt/gedavocat/backups \
    && chown -R docavocat:docavocat /opt/gedavocat

# Copier le JAR depuis le stage builder
COPY --from=builder /build/target/gedavocat-app-*.jar app.jar
RUN chown docavocat:docavocat app.jar

# Script d'entrée : corrige les permissions des volumes puis lance l'app
COPY docker/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# Sécurité : exécution en tant qu'utilisateur non-root
USER docavocat

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=5 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["/entrypoint.sh"]
