@echo off
REM ============================================================
REM Script de Configuration Git pour gedavocat-springboot
REM ============================================================

echo.
echo ========================================
echo Configuration Git - gedavocat-springboot
echo ========================================
echo.

cd /d C:\Users\el_ch\git\gedavocat-springboot

echo [1/5] Verification de la configuration actuelle...
echo.
git remote -v
echo.

echo [2/5] Quelle methode souhaitez-vous utiliser ?
echo.
echo 1. Personal Access Token (HTTPS) - Recommande pour Windows
echo 2. SSH (Plus securise mais necessite configuration)
echo 3. Mise a jour des credentials Windows
echo 4. Diagnostic complet
echo 5. Annuler
echo.

set /p choice="Votre choix (1-5) : "

if "%choice%"=="1" goto :token
if "%choice%"=="2" goto :ssh
if "%choice%"=="3" goto :credentials
if "%choice%"=="4" goto :diagnostic
if "%choice%"=="5" goto :end

:token
echo.
echo ========================================
echo Configuration avec Personal Access Token
echo ========================================
echo.
echo Etapes :
echo 1. Allez sur https://gitlab.com/-/profile/personal_access_tokens
echo 2. Creez un nouveau token avec les scopes : read_repository, write_repository
echo 3. Copiez le token genere
echo.
set /p token="Collez votre Personal Access Token ici : "

if "%token%"=="" (
    echo Erreur : Token vide
    goto :end
)

echo.
echo [3/5] Mise a jour du remote avec le token...
git remote set-url origin https://ahmed.ghrissi:%token%@gitlab.com/ahmed.ghrissi/gedavocat-springboot.git

echo.
echo [4/5] Verification de la connexion...
git ls-remote origin

if %ERRORLEVEL% EQU 0 (
    echo.
    echo [5/5] SUCCESS ! Connexion reussie
    echo.
    echo Vous pouvez maintenant utiliser :
    echo   git pull
    echo   git push
    echo.
) else (
    echo.
    echo ERREUR : La connexion a echoue
    echo Verifiez que le token est valide et possede les bonnes permissions
    echo.
)
goto :end

:ssh
echo.
echo ========================================
echo Configuration SSH
echo ========================================
echo.
echo [3/5] Verification de la cle SSH...

if not exist "%USERPROFILE%\.ssh\id_ed25519.pub" (
    echo Cle SSH non trouvee. Generation d'une nouvelle cle...
    echo.
    set /p email="Entrez votre email : "
    ssh-keygen -t ed25519 -C "%email%"
) else (
    echo Cle SSH trouvee !
)

echo.
echo [4/5] Votre cle publique SSH :
echo.
type %USERPROFILE%\.ssh\id_ed25519.pub
echo.
echo Copiez cette cle et ajoutez-la sur GitLab :
echo https://gitlab.com/-/profile/keys
echo.
pause

echo.
echo [5/5] Mise a jour du remote vers SSH...
git remote set-url origin git@gitlab.com:ahmed.ghrissi/gedavocat-springboot.git

echo.
echo Test de connexion SSH...
ssh -T git@gitlab.com

echo.
echo Remote mis a jour vers SSH !
echo.
git remote -v
goto :end

:credentials
echo.
echo ========================================
echo Mise a jour des Credentials Windows
echo ========================================
echo.
echo [3/5] Ouverture du Gestionnaire d'identifications...
start control /name Microsoft.CredentialManager
echo.
echo Instructions :
echo 1. Cherchez les entrees "gitlab.com" ou "git:https://gitlab.com"
echo 2. Supprimez toutes les entrees GitLab
echo 3. Fermez le gestionnaire
echo.
pause

echo.
echo [4/5] Configuration du credential helper...
git config --global credential.helper wincred

echo.
echo [5/5] Test de connexion (vous serez invite a entrer vos credentials)...
echo Username : ahmed.ghrissi
echo Password : Votre Personal Access Token (PAS votre mot de passe GitLab)
echo.
git pull

goto :end

:diagnostic
echo.
echo ========================================
echo Diagnostic Complet
echo ========================================
echo.

echo [1] Configuration Git Globale :
echo ---------------------------------
git config --list --global
echo.

echo [2] Configuration Git Locale :
echo ---------------------------------
git config --list --local
echo.

echo [3] Remotes Configures :
echo ---------------------------------
git remote -v
echo.

echo [4] Status Git :
echo ---------------------------------
git status
echo.

echo [5] Test de Connexion HTTPS :
echo ---------------------------------
git ls-remote https://gitlab.com/ahmed.ghrissi/gedavocat-springboot.git
echo.

echo [6] Informations Utilisateur :
echo ---------------------------------
git config user.name
git config user.email
echo.

echo.
echo Diagnostic termine !
echo.
goto :end

:end
echo.
echo ========================================
echo Documentation complete : GIT_AUTH_FIX.md
echo ========================================
echo.
pause
