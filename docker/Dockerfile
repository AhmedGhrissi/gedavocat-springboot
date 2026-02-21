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
# ARG BUILDPLATFORM permet au stage builder de tourner en x86_64 natif
# même quand on build --platform linux/arm64 (cross-compilation efficace)
ARG BUILDPLATFORM=linux/amd64
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-17-alpine AS builder

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

USER docavocat

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=5 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-Xms256m", "-Xmx512m", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-Dspring.profiles.active=prod", \
    "-jar", "app.jar"]
