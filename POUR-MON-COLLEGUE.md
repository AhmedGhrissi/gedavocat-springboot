# Guide de déploiement  Pour mon collègue

Le déploiement de DocAvocat se fait via **GitLab CI/CD**.
Tu n'as pas besoin de clé SSH, de Java, ni d'accès direct au serveur.

---

## Ce dont tu as besoin

### 1. Accès GitLab au projet

Demande à un membre de l'équipe de t'ajouter au projet GitLab avec le rôle
**Developer** (minimum) ou **Maintainer**.

URL du projet : _(à compléter par l'équipe)_

### 2. Git en local

```
git clone https://gitlab.com/<namespace>/docavocat.git
cd docavocat
```

---

## Déployer une mise à jour

### Étape 1  Committer et pousser sur `main`

```bash
git add .
git commit -m "feat: description de la modification"
git push origin main
```

### Étape 2  Attendre la CI (build + tests + build Docker)

Dans GitLab : **CI/CD  Pipelines**

La CI tourne automatiquement. Elle :
- Compile le projet Maven
- Lance les tests unitaires
- Construit l'image Docker ARM64 et la pousse dans le registry

Durée : ~5-8 minutes.

### Étape 3  Déclencher le déploiement manuellement

Une fois la CI verte :
1. GitLab  **CI/CD  Pipelines**
2. Cliquer sur le pipeline du dernier commit sur `main`
3. Trouver le job **`deploy`** (stage "deploy")
4. Cliquer sur ** (play)**

Le job :
- Envoie la configuration sur le serveur
- Fait `docker compose pull app` + `docker compose up -d`
- Vérifie que le site répond HTTP 200

Durée : ~2 minutes.

---

## En cas de problème

### Voir les logs du pipeline

GitLab  CI/CD  Pipelines  cliquer sur le pipeline concerné

### Voir les logs du serveur (nécessite la clé SSH)

```bash
ssh -i "$HOME/.ssh/gedavocat_hetzner" root@88.198.163.251 "docker logs -f docavocat-app"
```

Si tu n'as pas la clé SSH, demande à quelqu'un de l'équipe.

### État des conteneurs (nécessite la clé SSH)

```bash
ssh -i "$HOME/.ssh/gedavocat_hetzner" root@88.198.163.251 "docker ps"
```

Les deux conteneurs doivent être `healthy` :
- `docavocat-app`  application Spring Boot
- `docavocat-mysql`  base de données MySQL

---

## Si tu as la clé SSH (accès serveur direct)

### Récupérer la clé SSH

Demande le fichier `gedavocat_hetzner` à un membre de l'équipe
(Signal, Bitwarden, clé USB  jamais par mail).

Une fois obtenu :
```powershell
# Placer dans le bon répertoire
Copy-Item gedavocat_hetzner "$HOME\.ssh\gedavocat_hetzner"

# Corriger les permissions (obligatoire sous Windows)
icacls "$HOME\.ssh\gedavocat_hetzner" /inheritance:r /grant:r "${env:USERNAME}:(R)"

# Vérifier
ssh -i "$HOME\.ssh\gedavocat_hetzner" root@88.198.163.251 "echo OK"
```

---

## Architecture de référence

| Élément          | Détail                                         |
|------------------|------------------------------------------------|
| Serveur          | Hetzner  `88.198.163.251` (ARM64)             |
| App              | Docker container `docavocat-app`  port 8080   |
| Base de données  | Docker container `docavocat-mysql`  port 3307 |
| SSL / Nginx      | Sur le host (Let''s Encrypt)                    |
| Registry Docker  | GitLab Container Registry                      |
| URL production   | https://docavocat.fr                           |

---

## Ce qu il ne faut JAMAIS faire

- Committer `.env.prod` dans git
- Partager la clé SSH par mail ou message non chiffré
- Modifier directement des fichiers sur le serveur
  (ils seraient écrasés au prochain déploiement)

---

*Derniere mise a jour : fevrier 2026*
