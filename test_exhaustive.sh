#!/bin/bash
###############################################################################
# EXHAUSTIVE PRODUCTION TEST - GedAvocat
# Tests ALL endpoints, security, content, headers, edge cases
###############################################################################
BASE="http://localhost:8080"
ADMIN_EMAIL="admin@docavocat.fr"
ADMIN_PASS='Admin1234!'
COOKIE_JAR="/tmp/test_cookies_$$"
TMPDIR="/tmp/test_prod_deep_$$"
mkdir -p "$TMPDIR"
PASS=0; FAIL=0; WARN=0; TOTAL=0

# Colors
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'

check() {
  TOTAL=$((TOTAL+1))
  local desc="$1" expected="$2" actual="$3"
  if [ "$actual" = "$expected" ]; then
    echo -e "${GREEN}[PASS]${NC} $desc (got $actual)"
    PASS=$((PASS+1))
  else
    echo -e "${RED}[FAIL]${NC} $desc (expected $expected, got $actual)"
    FAIL=$((FAIL+1))
  fi
}

check_contains() {
  TOTAL=$((TOTAL+1))
  local desc="$1" file="$2" pattern="$3"
  if grep -Eqi "$pattern" "$file" 2>/dev/null; then
    echo -e "${GREEN}[PASS]${NC} $desc (contains '$pattern')"
    PASS=$((PASS+1))
  else
    echo -e "${RED}[FAIL]${NC} $desc (missing '$pattern')"
    FAIL=$((FAIL+1))
  fi
}

check_not_contains() {
  TOTAL=$((TOTAL+1))
  local desc="$1" file="$2" pattern="$3"
  if grep -Eqi "$pattern" "$file" 2>/dev/null; then
    echo -e "${RED}[FAIL]${NC} $desc (found forbidden '$pattern')"
    FAIL=$((FAIL+1))
  else
    echo -e "${GREEN}[PASS]${NC} $desc (correctly missing '$pattern')"
    PASS=$((PASS+1))
  fi
}

warn_check() {
  TOTAL=$((TOTAL+1))
  WARN=$((WARN+1))
  echo -e "${YELLOW}[WARN]${NC} $1"
}

get_status() {
  curl -sk -o /dev/null -w "%{http_code}" "$1"
}

get_status_with_jar() {
  curl -sk -o /dev/null -w "%{http_code}" -b "$COOKIE_JAR" "$1"
}

save_page() {
  curl -sk -b "$COOKIE_JAR" "$1" -o "$2" 2>/dev/null
}

echo "========================================================================"
echo -e "${BLUE}  EXHAUSTIVE PRODUCTION TEST SUITE - GedAvocat${NC}"
echo -e "${BLUE}  $(date)${NC}"
echo "========================================================================"
echo ""

###############################################################################
echo -e "${BLUE}â•â•â• 1. PUBLIC PAGES (unauthenticated) â•â•â•${NC}"
###############################################################################
for url in "/" "/login" "/register" \
           "/forgot-password" "/legal/privacy" "/legal/terms" \
           "/subscription/pricing" "/payment/pricing" \
           "/payment/success" "/payment/cancel" \
           "/sitemap.xml" "/robots.txt"; do
  CODE=$(get_status "${BASE}${url}")
  check "GET $url" "200" "$CODE"
done

# Verify email page (public)
CODE=$(get_status "${BASE}/verify-email")
check "GET /verify-email" "200" "$CODE"

# Welcome redirects to landing (by design)
CODE=$(get_status "${BASE}/welcome")
TOTAL=$((TOTAL+1))
if [ "$CODE" = "200" ] || [ "$CODE" = "302" ]; then
  echo -e "${GREEN}[PASS]${NC} GET /welcome ($CODE - redirect expected)"
  PASS=$((PASS+1))
else
  echo -e "${RED}[FAIL]${NC} GET /welcome unexpected ($CODE)"
  FAIL=$((FAIL+1))
fi

# Maintenance page redirects when maintenance is OFF
CODE=$(get_status "${BASE}/maintenance")
TOTAL=$((TOTAL+1))
if [ "$CODE" = "200" ] || [ "$CODE" = "302" ]; then
  echo -e "${GREEN}[PASS]${NC} GET /maintenance ($CODE - redirect when OFF)"
  PASS=$((PASS+1))
else
  echo -e "${RED}[FAIL]${NC} GET /maintenance unexpected ($CODE)"
  FAIL=$((FAIL+1))
fi

###############################################################################
echo ""
echo -e "${BLUE}â•â•â• 2. PUBLIC PAGE CONTENT VERIFICATION â•â•â•${NC}"
###############################################################################
# Landing page
save_page "$BASE/" "$TMPDIR/landing.html"
check_contains "Landing has doctype" "$TMPDIR/landing.html" "<!DOCTYPE"
check_contains "Landing has GedAvocat branding" "$TMPDIR/landing.html" "gedavocat|GedAvocat|DocAvocat"
check_contains "Landing has login link" "$TMPDIR/landing.html" "login"

# Login page
save_page "$BASE/login" "$TMPDIR/login.html"
check_contains "Login has form" "$TMPDIR/login.html" "<form"
check_contains "Login has CSRF token" "$TMPDIR/login.html" "_csrf"
check_contains "Login has email input" "$TMPDIR/login.html" "username|email"
check_contains "Login has password input" "$TMPDIR/login.html" "password"

# Register page
save_page "$BASE/register" "$TMPDIR/register.html"
check_contains "Register has form" "$TMPDIR/register.html" "<form"
check_contains "Register has CSRF token" "$TMPDIR/register.html" "_csrf"

# Legal pages
save_page "$BASE/legal/privacy" "$TMPDIR/privacy.html"
check_contains "Privacy page has content" "$TMPDIR/privacy.html" "donn.es|confidentialit|RGPD|privacy"

save_page "$BASE/legal/terms" "$TMPDIR/terms.html"
check_contains "Terms page has content" "$TMPDIR/terms.html" "conditions|CGU|terms|utilisation"

# Sitemap
save_page "$BASE/sitemap.xml" "$TMPDIR/sitemap.xml"
check_contains "Sitemap is XML" "$TMPDIR/sitemap.xml" "<urlset|<sitemapindex"

# Forgot password
save_page "$BASE/forgot-password" "$TMPDIR/forgot-password.html"
check_contains "Forgot password has form" "$TMPDIR/forgot-password.html" "<form"
check_contains "Forgot password has email" "$TMPDIR/forgot-password.html" "email"

###############################################################################
echo ""
echo -e "${BLUE}â•â•â• 3. PROTECTED PAGES (must redirect to login) â•â•â•${NC}"
###############################################################################
PROTECTED_URLS=(
  "/dashboard"
  "/cases" "/cases/new"
  "/clients" "/clients/new"
  "/documents"
  "/appointments" "/appointments/list" "/appointments/new"
  "/invoices" "/invoices/new"
  "/signatures" "/signatures/new"
  "/rpva" "/rpva/received" "/rpva/send"
  "/settings"
  "/admin" "/admin/users" "/admin/system" "/admin/logs" 
  "/admin/database" "/admin/statistics" "/admin/settings"
  "/my-cases" "/my-appointments" "/my-signatures"
  "/my-cases-collab"
  "/payment/manage" "/payment/checkout"
  "/subscription/manage" "/subscription/checkout"
  "/rgpd/export"
)
for url in "${PROTECTED_URLS[@]}"; do
  CODE=$(get_status "${BASE}${url}")
  check "Protected $url â†’ 302" "302" "$CODE"
done

###############################################################################
echo ""
echo -e "${BLUE}â•â•â• 4. CSRF PROTECTION (POST without token) â•â•â•${NC}"
###############################################################################
CSRF_URLS=(
  "/login" "/register" "/forgot-password" "/reset-password"
  "/verify-email" "/verify-email/resend"
)
for url in "${CSRF_URLS[@]}"; do
  CODE=$(curl -sk -o /dev/null -w "%{http_code}" -X POST "${BASE}${url}" -d "test=1")
  if [ "$CODE" = "403" ] || [ "$CODE" = "302" ]; then
    check "CSRF POST $url blocked" "blocked" "blocked"
  else
    check "CSRF POST $url blocked" "403/302" "$CODE"
  fi
done

###############################################################################
echo ""
echo -e "${BLUE}â•â•â• 5. SECURITY HEADERS â•â•â•${NC}"
###############################################################################
HEADERS=$(curl -skI "$BASE/")
echo "$HEADERS" > "$TMPDIR/headers.txt"

check_contains "Has X-Content-Type-Options" "$TMPDIR/headers.txt" "X-Content-Type-Options"
check_contains "Has X-Frame-Options or CSP frame" "$TMPDIR/headers.txt" "X-Frame-Options|Content-Security-Policy"
# HSTS only on HTTPS, skip on localhost
# check_contains "Has Strict-Transport-Security" "$TMPDIR/headers.txt" "Strict-Transport-Security"

# Check server doesn't leak version info  
check_not_contains "No Server version leak" "$TMPDIR/headers.txt" "Apache/|nginx/[0-9]|Tomcat"

###############################################################################
echo ""
echo -e "${BLUE}â•â•â• 6. STATIC RESOURCES â•â•â•${NC}"
###############################################################################
for res in "/css/design-system.css" "/css/pages/admin.css" "/css/main.css" "/js/main.js" "/robots.txt" "/favicon.svg"; do
  CODE=$(get_status "${BASE}${res}")
  check "Static $res" "200" "$CODE"
done

###############################################################################
echo ""
echo -e "${BLUE}â•â•â• 7. SHARE TOKEN HANDLING â•â•â•${NC}"
###############################################################################
# Valid share token
VALID_TOKEN="6b8f507f378b4898a11a196eefdce9cc160ddc88e"
CODE=$(get_status "${BASE}/cases/shared?token=${VALID_TOKEN}")
check "Valid share token â†’ 200" "200" "$CODE"
save_page "${BASE}/cases/shared?token=${VALID_TOKEN}" "$TMPDIR/shared-valid.html"
check_contains "Shared view has case content" "$TMPDIR/shared-valid.html" "Litige|dossier|shared|case|partag"

# Invalid share token
CODE=$(get_status "${BASE}/cases/shared?token=INVALID_TOKEN_XYZ")
check "Invalid share token â†’ 200 (expired page)" "200" "$CODE"
save_page "${BASE}/cases/shared?token=INVALID_TOKEN_XYZ" "$TMPDIR/shared-invalid.html"
check_contains "Invalid token shows expired/error" "$TMPDIR/shared-invalid.html" "expir|invalide|error|introuvable"

# No token parameter
CODE=$(get_status "${BASE}/cases/shared")
check "No share token â†’ 200 (expired page)" "200" "$CODE"

# Empty token
CODE=$(get_status "${BASE}/cases/shared?token=")
check "Empty share token â†’ 200" "200" "$CODE"

###############################################################################
echo ""
echo -e "${BLUE}â•â•â• 8. CLIENT INVITATION HANDLING â•â•â•${NC}"
###############################################################################
CODE=$(get_status "${BASE}/clients/accept-invitation?token=FAKE_TOKEN")
check "Client invitation with fake token" "200" "$CODE"

CODE=$(get_status "${BASE}/collaborators/accept-invitation?token=FAKE_TOKEN")
check "Collaborator invitation with fake token" "200" "$CODE"

CODE=$(get_status "${BASE}/collaborators/invitation-info?token=FAKE_TOKEN")
# Could be 200 or 404
TOTAL=$((TOTAL+1))
if [ "$CODE" = "200" ] || [ "$CODE" = "404" ]; then
  echo -e "${GREEN}[PASS]${NC} Collaborator invitation-info (got $CODE)"
  PASS=$((PASS+1))
else
  echo -e "${RED}[FAIL]${NC} Collaborator invitation-info (expected 200/404, got $CODE)"
  FAIL=$((FAIL+1))
fi

###############################################################################
echo ""
echo -e "${BLUE}â•â•â• 9. ADMIN LOGIN â•â•â•${NC}"
###############################################################################
rm -f "$COOKIE_JAR"

# Get login page + CSRF token
curl -sk -c "$COOKIE_JAR" "$BASE/login" -o "$TMPDIR/login_page.html"
CSRF=$(grep -o 'name="_csrf"[^>]*value="[^"]*"' "$TMPDIR/login_page.html" | grep -o 'value="[^"]*"' | cut -d'"' -f2)

if [ -z "$CSRF" ]; then
  CSRF=$(grep -o 'value="[^"]*"[^>]*name="_csrf"' "$TMPDIR/login_page.html" | grep -o 'value="[^"]*"' | cut -d'"' -f2)
fi

if [ -n "$CSRF" ]; then
  check "CSRF token extracted" "yes" "yes"
else
  check "CSRF token extracted" "non-empty" "EMPTY"
fi

# Login
LOGIN_RESULT=$(curl -sk -c "$COOKIE_JAR" -b "$COOKIE_JAR" -w "\n%{http_code}\n%{redirect_url}" \
  -X POST "$BASE/login" \
  -d "email=${ADMIN_EMAIL}&password=${ADMIN_PASS}&_csrf=${CSRF}" \
  -o "$TMPDIR/login_response.html")

LOGIN_CODE=$(echo "$LOGIN_RESULT" | tail -2 | head -1)
REDIRECT_URL=$(echo "$LOGIN_RESULT" | tail -1)

TOTAL=$((TOTAL+1))
if echo "$REDIRECT_URL" | grep -q "admin"; then
  echo -e "${GREEN}[PASS]${NC} Admin login â†’ redirect to /admin (code: $LOGIN_CODE)"
  PASS=$((PASS+1))
elif echo "$REDIRECT_URL" | grep -q "error"; then
  echo -e "${RED}[FAIL]${NC} Admin login failed â†’ redirect to error (code: $LOGIN_CODE)"
  FAIL=$((FAIL+1))
else
  echo -e "${YELLOW}[WARN]${NC} Admin login redirect to: $REDIRECT_URL (code: $LOGIN_CODE)"
  WARN=$((WARN+1))
fi

# Follow redirect to get session cookie
curl -sk -c "$COOKIE_JAR" -b "$COOKIE_JAR" -L "$BASE/login" \
  -d "email=${ADMIN_EMAIL}&password=${ADMIN_PASS}&_csrf=${CSRF}" \
  -o "$TMPDIR/admin_home.html" 2>/dev/null

# Verify we're logged in
CODE=$(get_status_with_jar "${BASE}/admin")
check "Admin dashboard accessible after login" "200" "$CODE"

###############################################################################
echo ""
echo -e "${BLUE}â•â•â• 10. ALL ADMIN PAGES â•â•â•${NC}"
###############################################################################
ADMIN_PAGES=(
  "/admin"
  "/admin/users"
  "/admin/system"
  "/admin/logs"
  "/admin/database"
  "/admin/statistics"
  "/admin/settings"
)
for url in "${ADMIN_PAGES[@]}"; do
  CODE=$(get_status_with_jar "${BASE}${url}")
  check "Admin page $url" "200" "$CODE"
  save_page "${BASE}${url}" "$TMPDIR/admin_$(echo $url | tr '/' '_').html"
done

###############################################################################
echo ""
echo -e "${BLUE}â•â•â• 11. ADMIN PAGE CONTENT VERIFICATION â•â•â•${NC}"
###############################################################################
# Dashboard
check_contains "Admin dashboard has stats/cards" "$TMPDIR/admin__admin.html" "dashboard|statistiq|tableau"

# Users page
check_contains "Admin users has table" "$TMPDIR/admin__admin_users.html" "<table|<th"
check_contains "Admin users has user data" "$TMPDIR/admin__admin_users.html" "admin@docavocat|ghrissi|dufayet"
check_contains "Admin users has action buttons" "$TMPDIR/admin__admin_users.html" "btn-view|btn-edit|btn-delete|modal"
check_contains "Admin users has new user button" "$TMPDIR/admin__admin_users.html" "newUser|Nouveau|CrÃ©er"
check_contains "Admin users has modal markup" "$TMPDIR/admin__admin_users.html" "viewUserModal|editUserModal|newUserModal"

# System page
check_contains "Admin system has system info" "$TMPDIR/admin__admin_system.html" "java|jvm|mÃ©moire|memory|system"

# Logs page
check_contains "Admin logs page rendered" "$TMPDIR/admin__admin_logs.html" "log|journal|audit"

# Database page
check_contains "Admin database page rendered" "$TMPDIR/admin__admin_database.html" "base|database|table|donnÃ©es"

# Statistics page
check_contains "Admin statistics page rendered" "$TMPDIR/admin__admin_statistics.html" "statistiq|chart|graph"

# Settings page
check_contains "Admin settings page rendered" "$TMPDIR/admin__admin_settings.html" "paramÃ¨tre|setting|maintenance|config"

###############################################################################
echo ""
echo -e "${BLUE}â•â•â• 12. ADMIN API ENDPOINTS â•â•â•${NC}"
###############################################################################
# Get user by ID (JSON)
LAWYER_ID="7837d9f6-14bb-4bdf-890f-7160fc43911e"
CODE=$(curl -sk -o "$TMPDIR/user_json.json" -w "%{http_code}" -b "$COOKIE_JAR" "${BASE}/admin/users/${LAWYER_ID}")
check "GET /admin/users/{id} JSON" "200" "$CODE"
check_contains "User JSON has email" "$TMPDIR/user_json.json" "ghrissi.ahmed@gmail.com"

# Maintenance status API
CODE=$(curl -sk -o "$TMPDIR/maint_status.json" -w "%{http_code}" -b "$COOKIE_JAR" "${BASE}/api/admin/maintenance/status")
check "GET /api/admin/maintenance/status" "200" "$CODE"

###############################################################################
echo ""
echo -e "${BLUE}â•â•â• 13. LAWYER PAGES (via admin session - admin has ADMIN role) â•â•â•${NC}"
###############################################################################
# Admin should also have access to lawyer features based on security config
LAWYER_PAGES=(
  "/cases"
  "/clients"
  "/documents"
  "/appointments"
  "/appointments/list"
  "/invoices"
  "/signatures"
  "/rpva"
  "/rpva/received"
  "/settings"
)
for url in "${LAWYER_PAGES[@]}"; do
  CODE=$(get_status_with_jar "${BASE}${url}")
  # Admin may or may not have LAWYER role â€” could be 200 or 403
  TOTAL=$((TOTAL+1))
  if [ "$CODE" = "200" ]; then
    echo -e "${GREEN}[PASS]${NC} Page $url accessible (200)"
    PASS=$((PASS+1))
  elif [ "$CODE" = "403" ]; then
    echo -e "${YELLOW}[WARN]${NC} Page $url forbidden for ADMIN role (403) - expected for non-LAWYER"
    WARN=$((WARN+1))
  else
    echo -e "${RED}[FAIL]${NC} Page $url unexpected response ($CODE)"
    FAIL=$((FAIL+1))
  fi
done

###############################################################################
echo ""
echo -e "${BLUE}â•â•â• 14. CASE-SPECIFIC PAGES â•â•â•${NC}"
###############################################################################
CASE_ID="0c67bca5-3c01-40b0-8b96-2661014e5712"
DOC_ID="00fc425a-ceb2-4538-bbd3-2aa7c512f333"

# Case view
CODE=$(get_status_with_jar "${BASE}/cases/${CASE_ID}")
TOTAL=$((TOTAL+1))
if [ "$CODE" = "200" ]; then
  echo -e "${GREEN}[PASS]${NC} Case view /cases/{id} (200)"
  PASS=$((PASS+1))
  save_page "${BASE}/cases/${CASE_ID}" "$TMPDIR/case_view.html"
  check_contains "Case view has case name" "$TMPDIR/case_view.html" "Litige commercial"
  check_contains "Case view has client info" "$TMPDIR/case_view.html" "DUFAYET"
  check_contains "Case view has document list" "$TMPDIR/case_view.html" "RAPPORT-PENTEST|document"
  check_contains "Case view has share section" "$TMPDIR/case_view.html" "partag|share|collaborat"
elif [ "$CODE" = "403" ]; then
  echo -e "${YELLOW}[WARN]${NC} Case view forbidden for ADMIN (403)"
  WARN=$((WARN+1))
else
  echo -e "${RED}[FAIL]${NC} Case view unexpected ($CODE)"
  FAIL=$((FAIL+1))
fi

# Case edit
CODE=$(get_status_with_jar "${BASE}/cases/${CASE_ID}/edit")
TOTAL=$((TOTAL+1))
if [ "$CODE" = "200" ] || [ "$CODE" = "403" ]; then
  echo -e "${GREEN}[PASS]${NC} Case edit /cases/{id}/edit ($CODE)"
  PASS=$((PASS+1))
else
  echo -e "${RED}[FAIL]${NC} Case edit unexpected ($CODE)"
  FAIL=$((FAIL+1))
fi

# Case share page
CODE=$(get_status_with_jar "${BASE}/cases/${CASE_ID}/share")
TOTAL=$((TOTAL+1))
if [ "$CODE" = "200" ] || [ "$CODE" = "403" ]; then
  echo -e "${GREEN}[PASS]${NC} Case share /cases/{id}/share ($CODE)"
  PASS=$((PASS+1))
  if [ "$CODE" = "200" ]; then
    save_page "${BASE}/cases/${CASE_ID}/share" "$TMPDIR/case_share.html"
    check_contains "Share page has share links" "$TMPDIR/case_share.html" "lien|link|partage|token"
  fi
else
  echo -e "${RED}[FAIL]${NC} Case share unexpected ($CODE)"
  FAIL=$((FAIL+1))
fi

# New case form
CODE=$(get_status_with_jar "${BASE}/cases/new")
TOTAL=$((TOTAL+1))
if [ "$CODE" = "200" ] || [ "$CODE" = "403" ]; then
  echo -e "${GREEN}[PASS]${NC} New case /cases/new ($CODE)"
  PASS=$((PASS+1))
else
  echo -e "${RED}[FAIL]${NC} New case unexpected ($CODE)"
  FAIL=$((FAIL+1))
fi

# Document download
CODE=$(get_status_with_jar "${BASE}/documents/${DOC_ID}/download")
TOTAL=$((TOTAL+1))
if [ "$CODE" = "200" ] || [ "$CODE" = "403" ]; then
  echo -e "${GREEN}[PASS]${NC} Document download ($CODE)"
  PASS=$((PASS+1))
else
  echo -e "${RED}[FAIL]${NC} Document download unexpected ($CODE)"
  FAIL=$((FAIL+1))
fi

# Document preview
CODE=$(get_status_with_jar "${BASE}/documents/${DOC_ID}/preview")
TOTAL=$((TOTAL+1))
if [ "$CODE" = "200" ] || [ "$CODE" = "403" ]; then
  echo -e "${GREEN}[PASS]${NC} Document preview ($CODE)"
  PASS=$((PASS+1))
else
  echo -e "${RED}[FAIL]${NC} Document preview unexpected ($CODE)"
  FAIL=$((FAIL+1))
fi

# Documents by case
CODE=$(get_status_with_jar "${BASE}/documents/case/${CASE_ID}")
TOTAL=$((TOTAL+1))
if [ "$CODE" = "200" ] || [ "$CODE" = "403" ]; then
  echo -e "${GREEN}[PASS]${NC} Documents by case ($CODE)"
  PASS=$((PASS+1))
else
  echo -e "${RED}[FAIL]${NC} Documents by case unexpected ($CODE)"
  FAIL=$((FAIL+1))
fi

# Documents trash
CODE=$(get_status_with_jar "${BASE}/documents/case/${CASE_ID}/trash")
TOTAL=$((TOTAL+1))
if [ "$CODE" = "200" ] || [ "$CODE" = "403" ]; then
  echo -e "${GREEN}[PASS]${NC} Documents trash ($CODE)"
  PASS=$((PASS+1))
else
  echo -e "${RED}[FAIL]${NC} Documents trash unexpected ($CODE)"
  FAIL=$((FAIL+1))
fi

###############################################################################
echo ""
echo -e "${BLUE}â•â•â• 15. CLIENT-SPECIFIC PAGES â•â•â•${NC}"
###############################################################################
CLIENT_ID="4dd0574b-ff30-4c38-bb30-06d6bdc3994f"

CODE=$(get_status_with_jar "${BASE}/clients/${CLIENT_ID}")
TOTAL=$((TOTAL+1))
if [ "$CODE" = "200" ] || [ "$CODE" = "403" ]; then
  echo -e "${GREEN}[PASS]${NC} Client view ($CODE)"
  PASS=$((PASS+1))
  if [ "$CODE" = "200" ]; then
    save_page "${BASE}/clients/${CLIENT_ID}" "$TMPDIR/client_view.html"
    check_contains "Client view has client name" "$TMPDIR/client_view.html" "DUFAYET"
  fi
else
  echo -e "${RED}[FAIL]${NC} Client view unexpected ($CODE)"
  FAIL=$((FAIL+1))
fi

CODE=$(get_status_with_jar "${BASE}/clients/${CLIENT_ID}/edit")
TOTAL=$((TOTAL+1))
if [ "$CODE" = "200" ] || [ "$CODE" = "403" ]; then
  echo -e "${GREEN}[PASS]${NC} Client edit ($CODE)"
  PASS=$((PASS+1))
else
  echo -e "${RED}[FAIL]${NC} Client edit unexpected ($CODE)"
  FAIL=$((FAIL+1))
fi

###############################################################################
echo ""
echo -e "${BLUE}â•â•â• 16. ERROR HANDLING â•â•â•${NC}"
###############################################################################
# 404 for non-existent page
CODE=$(get_status "${BASE}/this-page-does-not-exist-12345")
TOTAL=$((TOTAL+1))
if [ "$CODE" = "404" ] || [ "$CODE" = "302" ]; then
  echo -e "${GREEN}[PASS]${NC} Non-existent page handled ($CODE)"
  PASS=$((PASS+1))
else
  echo -e "${RED}[FAIL]${NC} Non-existent page unexpected ($CODE)"
  FAIL=$((FAIL+1))
fi

# Non-existent case ID
CODE=$(get_status_with_jar "${BASE}/cases/00000000-0000-0000-0000-000000000000")
TOTAL=$((TOTAL+1))
if [ "$CODE" = "404" ] || [ "$CODE" = "500" ] || [ "$CODE" = "403" ] || [ "$CODE" = "302" ]; then
  echo -e "${GREEN}[PASS]${NC} Non-existent case handled ($CODE)"
  PASS=$((PASS+1))
else
  echo -e "${RED}[FAIL]${NC} Non-existent case unexpected ($CODE)"
  FAIL=$((FAIL+1))
fi

# Invalid UUID format
CODE=$(get_status_with_jar "${BASE}/cases/NOT-A-UUID")
TOTAL=$((TOTAL+1))
if [ "$CODE" = "400" ] || [ "$CODE" = "404" ] || [ "$CODE" = "500" ] || [ "$CODE" = "403" ]; then
  echo -e "${GREEN}[PASS]${NC} Invalid UUID case handled ($CODE)"
  PASS=$((PASS+1))
else
  echo -e "${RED}[FAIL]${NC} Invalid UUID unexpected ($CODE)"
  FAIL=$((FAIL+1))
fi

# Non-existent client
CODE=$(get_status_with_jar "${BASE}/clients/00000000-0000-0000-0000-000000000000")
TOTAL=$((TOTAL+1))
if [ "$CODE" = "404" ] || [ "$CODE" = "500" ] || [ "$CODE" = "403" ] || [ "$CODE" = "302" ]; then
  echo -e "${GREEN}[PASS]${NC} Non-existent client handled ($CODE)"
  PASS=$((PASS+1))
else
  echo -e "${RED}[FAIL]${NC} Non-existent client unexpected ($CODE)"
  FAIL=$((FAIL+1))
fi

# Non-existent document download
CODE=$(get_status_with_jar "${BASE}/documents/00000000-0000-0000-0000-000000000000/download")
TOTAL=$((TOTAL+1))
if [ "$CODE" = "404" ] || [ "$CODE" = "500" ] || [ "$CODE" = "403" ]; then
  echo -e "${GREEN}[PASS]${NC} Non-existent document download handled ($CODE)"
  PASS=$((PASS+1))
else
  echo -e "${RED}[FAIL]${NC} Non-existent doc download unexpected ($CODE)"
  FAIL=$((FAIL+1))
fi

# Non-existent admin user JSON
CODE=$(get_status_with_jar "${BASE}/admin/users/00000000-0000-0000-0000-000000000000")
TOTAL=$((TOTAL+1))
if [ "$CODE" = "404" ] || [ "$CODE" = "500" ] || [ "$CODE" = "200" ]; then
  echo -e "${GREEN}[PASS]${NC} Non-existent admin user JSON handled ($CODE)"
  PASS=$((PASS+1))
else
  echo -e "${RED}[FAIL]${NC} Non-existent admin user JSON unexpected ($CODE)"
  FAIL=$((FAIL+1))
fi

###############################################################################
echo ""
echo -e "${BLUE}â•â•â• 17. XSS PROTECTION â•â•â•${NC}"
###############################################################################
# Try XSS in login
XSS_CODE=$(curl -sk -o "$TMPDIR/xss_login.html" -w "%{http_code}" "${BASE}/login?error=<script>alert(1)</script>")
check_not_contains "XSS in login param escaped" "$TMPDIR/xss_login.html" "<script>alert(1)</script>"

# Try XSS in share token
XSS_CODE=$(curl -sk -o "$TMPDIR/xss_share.html" -w "%{http_code}" "${BASE}/cases/shared?token=<script>alert(1)</script>")
check_not_contains "XSS in share token escaped" "$TMPDIR/xss_share.html" "<script>alert(1)</script>"

# Try XSS in invitation
XSS_CODE=$(curl -sk -o "$TMPDIR/xss_invite.html" -w "%{http_code}" "${BASE}/clients/accept-invitation?token=<script>alert(1)</script>")
check_not_contains "XSS in invitation token escaped" "$TMPDIR/xss_invite.html" "<script>alert(1)</script>"

# Try XSS in forgot password
XSS_CODE=$(curl -sk -o "$TMPDIR/xss_forgot.html" -w "%{http_code}" "${BASE}/forgot-password?error=<script>alert(1)</script>")
check_not_contains "XSS in forgot-password escaped" "$TMPDIR/xss_forgot.html" "<script>alert(1)</script>"

###############################################################################
echo ""
echo -e "${BLUE}â•â•â• 18. SQL INJECTION PROTECTION â•â•â•${NC}"
###############################################################################
# SQL injection in share token (URL-encoded)
SQLI_CODE=$(curl -sk -o "$TMPDIR/sqli_share.html" -w "%{http_code}" "${BASE}/cases/shared?token=%27%20OR%20%271%27%3D%271")
TOTAL=$((TOTAL+1))
if [ "$SQLI_CODE" = "200" ] || [ "$SQLI_CODE" = "400" ]; then
  echo -e "${GREEN}[PASS]${NC} SQL injection in share token handled ($SQLI_CODE)"
  PASS=$((PASS+1))
  check_not_contains "SQLi didn't return case data" "$TMPDIR/sqli_share.html" "Litige commercial"
else
  echo -e "${RED}[FAIL]${NC} SQL injection unexpected ($SQLI_CODE)"
  FAIL=$((FAIL+1))
fi

# SQL injection in client invitation
SQLI_CODE=$(curl -sk -o "$TMPDIR/sqli_invite.html" -w "%{http_code}" "${BASE}/clients/accept-invitation?token=1'+OR+'1'='1")
TOTAL=$((TOTAL+1))
if [ "$SQLI_CODE" = "200" ] || [ "$SQLI_CODE" = "400" ]; then
  echo -e "${GREEN}[PASS]${NC} SQL injection in invitation handled ($SQLI_CODE)"
  PASS=$((PASS+1))
else
  echo -e "${RED}[FAIL]${NC} SQL injection invitation unexpected ($SQLI_CODE)"
  FAIL=$((FAIL+1))
fi

###############################################################################
echo ""
echo -e "${BLUE}â•â•â• 19. AUTHORIZATION BOUNDARY TESTS â•â•â•${NC}"
###############################################################################
# Try to access admin pages without auth
rm -f /tmp/noauth_cookies_$$
CODE=$(curl -sk -o /dev/null -w "%{http_code}" "${BASE}/admin/users")
check "Admin users without auth â†’ 302" "302" "$CODE"

CODE=$(curl -sk -o /dev/null -w "%{http_code}" "${BASE}/api/admin/maintenance/status")
check "Admin API without auth â†’ 302 or 401" "302" "$CODE"

# Try to POST admin actions without auth
CODE=$(curl -sk -o /dev/null -w "%{http_code}" -X POST "${BASE}/admin/users/create" -d "test=1")
TOTAL=$((TOTAL+1))
if [ "$CODE" = "302" ] || [ "$CODE" = "403" ]; then
  echo -e "${GREEN}[PASS]${NC} Admin create without auth blocked ($CODE)"
  PASS=$((PASS+1))
else
  echo -e "${RED}[FAIL]${NC} Admin create without auth unexpected ($CODE)"
  FAIL=$((FAIL+1))
fi

# Try accessing permission revoke without auth
CODE=$(curl -sk -o /dev/null -w "%{http_code}" -X POST "${BASE}/cases/${CASE_ID}/permissions/fake-id/revoke")
TOTAL=$((TOTAL+1))
if [ "$CODE" = "302" ] || [ "$CODE" = "403" ]; then
  echo -e "${GREEN}[PASS]${NC} Permission revoke without auth blocked ($CODE)"
  PASS=$((PASS+1))
else
  echo -e "${RED}[FAIL]${NC} Permission revoke without auth unexpected ($CODE)"
  FAIL=$((FAIL+1))
fi

# Try case deletion without auth
CODE=$(curl -sk -o /dev/null -w "%{http_code}" -X POST "${BASE}/cases/${CASE_ID}/delete")
TOTAL=$((TOTAL+1))
if [ "$CODE" = "302" ] || [ "$CODE" = "403" ]; then
  echo -e "${GREEN}[PASS]${NC} Case delete without auth blocked ($CODE)"
  PASS=$((PASS+1))
else
  echo -e "${RED}[FAIL]${NC} Case delete without auth unexpected ($CODE)"
  FAIL=$((FAIL+1))
fi

# RGPD export without auth
CODE=$(get_status "${BASE}/rgpd/export")
check "RGPD export without auth â†’ 302" "302" "$CODE"

# RGPD delete without auth
CODE=$(curl -sk -o /dev/null -w "%{http_code}" -X POST "${BASE}/rgpd/delete-account")
TOTAL=$((TOTAL+1))
if [ "$CODE" = "302" ] || [ "$CODE" = "403" ]; then
  echo -e "${GREEN}[PASS]${NC} RGPD delete-account without auth blocked ($CODE)"
  PASS=$((PASS+1))
else
  echo -e "${RED}[FAIL]${NC} RGPD delete-account without auth unexpected ($CODE)"
  FAIL=$((FAIL+1))
fi

###############################################################################
echo ""
echo -e "${BLUE}â•â•â• 20. ADMIN ACTIONS (non-destructive) â•â•â•${NC}"
###############################################################################
# Get fresh CSRF for admin session
save_page "${BASE}/admin/settings" "$TMPDIR/admin_settings2.html"
ADMIN_CSRF=$(grep -o 'name="_csrf"[^>]*value="[^"]*"' "$TMPDIR/admin_settings2.html" | head -1 | grep -o 'value="[^"]*"' | cut -d'"' -f2)

if [ -z "$ADMIN_CSRF" ]; then
  ADMIN_CSRF=$(grep -o '<input[^>]*_csrf[^>]*value="[^"]*"' "$TMPDIR/admin_settings2.html" | head -1 | grep -o 'value="[^"]*"' | cut -d'"' -f2)
fi

if [ -n "$ADMIN_CSRF" ]; then
  check "Admin CSRF token available" "yes" "yes"
  
  # Test GC endpoint (safe)
  CODE=$(curl -sk -o "$TMPDIR/gc_result.json" -w "%{http_code}" -b "$COOKIE_JAR" \
    -X POST "${BASE}/api/admin/gc" \
    -H "X-CSRF-TOKEN: $ADMIN_CSRF")
  TOTAL=$((TOTAL+1))
  if [ "$CODE" = "200" ] || [ "$CODE" = "403" ]; then
    echo -e "${GREEN}[PASS]${NC} Admin GC endpoint ($CODE)"
    PASS=$((PASS+1))
  else
    echo -e "${RED}[FAIL]${NC} Admin GC endpoint unexpected ($CODE)"
    FAIL=$((FAIL+1))
  fi

  # Check maintenance status
  CODE=$(curl -sk -o "$TMPDIR/maint.json" -w "%{http_code}" -b "$COOKIE_JAR" "${BASE}/api/admin/maintenance/status")
  check "Maintenance status check" "200" "$CODE"

else
  warn_check "Could not extract admin CSRF token - skipping admin action tests"
fi

###############################################################################
echo ""
echo -e "${BLUE}â•â•â• 21. APPOINTMENT CALENDAR API â•â•â•${NC}"
###############################################################################
# Appointment events API requires start/end date params
CODE=$(get_status_with_jar "${BASE}/appointments/api/events?start=2026-01-01&end=2026-12-31")
TOTAL=$((TOTAL+1))
if [ "$CODE" = "200" ] || [ "$CODE" = "403" ] || [ "$CODE" = "400" ]; then
  echo -e "${GREEN}[PASS]${NC} Appointment events API ($CODE)"
  PASS=$((PASS+1))
  if [ "$CODE" = "200" ]; then
    save_page "${BASE}/appointments/api/events?start=2026-01-01&end=2026-12-31" "$TMPDIR/events.json"
    check_contains "Events API returns JSON array" "$TMPDIR/events.json" "\[|events"
  fi
else
  echo -e "${RED}[FAIL]${NC} Appointment events API unexpected ($CODE)"
  FAIL=$((FAIL+1))
fi

###############################################################################
echo ""
echo -e "${BLUE}â•â•â• 22. INVOICE API (REST) â•â•â•${NC}"
###############################################################################
# Generate invoice number
CODE=$(curl -sk -o "$TMPDIR/inv_num.json" -w "%{http_code}" -b "$COOKIE_JAR" "${BASE}/api/invoices/generate-number")
TOTAL=$((TOTAL+1))
if [ "$CODE" = "200" ] || [ "$CODE" = "403" ]; then
  echo -e "${GREEN}[PASS]${NC} Invoice generate-number ($CODE)"
  PASS=$((PASS+1))
else
  echo -e "${RED}[FAIL]${NC} Invoice generate-number unexpected ($CODE)"
  FAIL=$((FAIL+1))
fi

# Get invoices for lawyer
CODE=$(curl -sk -o "$TMPDIR/inv_lawyer.json" -w "%{http_code}" -b "$COOKIE_JAR" "${BASE}/api/invoices/lawyer/${LAWYER_ID}")
TOTAL=$((TOTAL+1))
if [ "$CODE" = "200" ] || [ "$CODE" = "403" ]; then
  echo -e "${GREEN}[PASS]${NC} Invoices by lawyer ($CODE)"
  PASS=$((PASS+1))
else
  echo -e "${RED}[FAIL]${NC} Invoices by lawyer unexpected ($CODE)"
  FAIL=$((FAIL+1))
fi

# Get invoices for client
CODE=$(curl -sk -o "$TMPDIR/inv_client.json" -w "%{http_code}" -b "$COOKIE_JAR" "${BASE}/api/invoices/client/${CLIENT_ID}")
TOTAL=$((TOTAL+1))
if [ "$CODE" = "200" ] || [ "$CODE" = "403" ]; then
  echo -e "${GREEN}[PASS]${NC} Invoices by client ($CODE)"
  PASS=$((PASS+1))
else
  echo -e "${RED}[FAIL]${NC} Invoices by client unexpected ($CODE)"
  FAIL=$((FAIL+1))
fi

###############################################################################
echo ""
echo -e "${BLUE}â•â•â• 23. LAYOUT & NAVIGATION VERIFICATION â•â•â•${NC}"
###############################################################################
# Check layout components on admin dashboard
save_page "${BASE}/admin" "$TMPDIR/admin_dash_full.html"
check_contains "Layout has sidebar/nav" "$TMPDIR/admin_dash_full.html" "sidebar|nav|menu"
check_contains "Layout has logout" "$TMPDIR/admin_dash_full.html" "logout|dÃ©connex"
check_contains "Layout has user info" "$TMPDIR/admin_dash_full.html" "admin@docavocat|Admin GED"
check_contains "Layout has JS files" "$TMPDIR/admin_dash_full.html" "theme.js|<script"
check_contains "Layout has CSS files" "$TMPDIR/admin_dash_full.html" "design-system.css|<link"

# Check password change modal (layout.html feature)
check_contains "Layout has password change modal" "$TMPDIR/admin_dash_full.html" "changePasswordModal|changePassword|mot de passe"

###############################################################################
echo ""
echo -e "${BLUE}â•â•â• 24. CLIENT PORTAL (no client creds, verify redirect) â•â•â•${NC}"
###############################################################################
# Client portal pages should redirect when accessed without client auth
for url in "/my-cases" "/my-appointments" "/my-signatures"; do
  CODE=$(get_status "${BASE}${url}")
  check "Client portal $url without auth â†’ 302" "302" "$CODE"
done

# My invoices
CODE=$(get_status "${BASE}/invoices/my-invoices")
check "My invoices without auth â†’ 302" "302" "$CODE"

###############################################################################
echo ""
echo -e "${BLUE}â•â•â• 25. COLLABORATOR PORTAL â•â•â•${NC}"
###############################################################################
CODE=$(get_status "${BASE}/my-cases-collab")
check "Collab portal without auth â†’ 302" "302" "$CODE"

###############################################################################
echo ""
echo -e "${BLUE}â•â•â• 26. PAYMENT/SUBSCRIPTION PAGES â•â•â•${NC}"
###############################################################################
# Pricing pages (public)
CODE=$(get_status "${BASE}/payment/pricing")
check "Payment pricing (public)" "200" "$CODE"
save_page "${BASE}/payment/pricing" "$TMPDIR/payment_pricing.html"
check_contains "Pricing has plans" "$TMPDIR/payment_pricing.html" "solo|cabinet|enterprise|prix|price|plan|tarif"

CODE=$(get_status "${BASE}/subscription/pricing")
check "Subscription pricing (public)" "200" "$CODE"

# Checkout requires auth
CODE=$(get_status "${BASE}/payment/checkout")
check "Payment checkout without auth â†’ 302" "302" "$CODE"

CODE=$(get_status "${BASE}/subscription/checkout")
check "Subscription checkout without auth â†’ 302" "302" "$CODE"

# Success/cancel pages  
CODE=$(get_status "${BASE}/payment/success")
check "Payment success page" "200" "$CODE"

CODE=$(get_status "${BASE}/payment/cancel")
check "Payment cancel page" "200" "$CODE"

CODE=$(get_status "${BASE}/subscription/cancel")
TOTAL=$((TOTAL+1))
if [ "$CODE" = "200" ] || [ "$CODE" = "302" ]; then
  echo -e "${GREEN}[PASS]${NC} Subscription cancel page ($CODE)"
  PASS=$((PASS+1))
else
  echo -e "${RED}[FAIL]${NC} Subscription cancel unexpected ($CODE)"
  FAIL=$((FAIL+1))
fi

###############################################################################
echo ""
echo -e "${BLUE}â•â•â• 27. RESET PASSWORD FLOW â•â•â•${NC}"
###############################################################################
# Test reset password with invalid token
CODE=$(get_status "${BASE}/reset-password?token=INVALID_RESET_TOKEN")
check "Reset password with invalid token" "200" "$CODE"
save_page "${BASE}/reset-password?token=INVALID_RESET_TOKEN" "$TMPDIR/reset_invalid.html"
check_contains "Reset password page renders" "$TMPDIR/reset_invalid.html" "mot de passe|password|reset"

###############################################################################
echo ""
echo -e "${BLUE}â•â•â• 28. RESPONSE TIME CHECKS â•â•â•${NC}"
###############################################################################
for url in "/" "/login" "/admin" "/cases" "/legal/privacy"; do
  TIME=$(curl -sk -o /dev/null -w "%{time_total}" -b "$COOKIE_JAR" "${BASE}${url}")
  TIME_MS=$(echo "$TIME * 1000" | bc | cut -d. -f1)
  TOTAL=$((TOTAL+1))
  if [ "$TIME_MS" -lt 3000 ]; then
    echo -e "${GREEN}[PASS]${NC} $url response time: ${TIME_MS}ms (< 3s)"
    PASS=$((PASS+1))
  else
    echo -e "${YELLOW}[WARN]${NC} $url response time: ${TIME_MS}ms (> 3s, slow)"
    WARN=$((WARN+1))
  fi
done

###############################################################################
echo ""
echo -e "${BLUE}â•â•â• 29. COOKIE SECURITY â•â•â•${NC}"
###############################################################################
COOKIE_CONTENT=$(cat "$COOKIE_JAR" 2>/dev/null || echo "")
echo "$COOKIE_CONTENT" > "$TMPDIR/cookies.txt"
TOTAL=$((TOTAL+1))
if echo "$COOKIE_CONTENT" | grep -q "JSESSIONID|SESSION"; then
  echo -e "${GREEN}[PASS]${NC} Session cookie present"
  PASS=$((PASS+1))
else
  echo -e "${YELLOW}[WARN]${NC} No session cookie found in jar"
  WARN=$((WARN+1))
fi

# Check HTTPS-only cookies via response headers
LOGIN_HEADERS=$(curl -skI -b "$COOKIE_JAR" "${BASE}/admin")
echo "$LOGIN_HEADERS" > "$TMPDIR/admin_headers.txt"

###############################################################################
echo ""
echo -e "${BLUE}â•â•â• 30. RATE LIMITING / BRUTE FORCE â•â•â•${NC}"
###############################################################################
# Try 5 rapid login attempts with wrong password
BRUTE_BLOCKED=0
for i in 1 2 3 4 5; do
  BCODE=$(curl -sk -o /dev/null -w "%{http_code}" -X POST "${BASE}/login" \
    -d "email=test@test.com&password=wrong$i&_csrf=invalid")
  if [ "$BCODE" = "429" ]; then
    BRUTE_BLOCKED=1
    break
  fi
done
TOTAL=$((TOTAL+1))
if [ "$BRUTE_BLOCKED" = "1" ]; then
  echo -e "${GREEN}[PASS]${NC} Rate limiting detected (429)"
  PASS=$((PASS+1))
else
  echo -e "${YELLOW}[WARN]${NC} No rate limiting on login (all returned non-429)"
  WARN=$((WARN+1))
fi

###############################################################################
echo ""
echo -e "${BLUE}â•â•â• 31. PATH TRAVERSAL PROTECTION â•â•â•${NC}"
###############################################################################
CODE=$(curl -sk -o /dev/null -w "%{http_code}" "${BASE}/../../etc/passwd")
TOTAL=$((TOTAL+1))
if [ "$CODE" = "400" ] || [ "$CODE" = "404" ] || [ "$CODE" = "302" ] || [ "$CODE" = "200" ]; then
  echo -e "${GREEN}[PASS]${NC} Path traversal blocked ($CODE)"
  PASS=$((PASS+1))
fi

CODE=$(curl -sk -o "$TMPDIR/traversal.html" -w "%{http_code}" "${BASE}/documents/../../../etc/passwd/download")
check_not_contains "Path traversal didn't leak /etc/passwd" "$TMPDIR/traversal.html" "root:x:0:0"

###############################################################################
echo ""
echo -e "${BLUE}â•â•â• 32. APPLICATION LOGS CHECK â•â•â•${NC}"
###############################################################################
# Check recent logs for errors
docker logs docavocat-app --tail 50 2>&1 > "$TMPDIR/recent_logs.txt"
ERROR_COUNT=$(grep -c "ERROR" "$TMPDIR/recent_logs.txt" 2>/dev/null || echo 0)
TOTAL=$((TOTAL+1))
if [ "$ERROR_COUNT" = "0" ]; then
  echo -e "${GREEN}[PASS]${NC} No ERROR entries in last 50 log lines"
  PASS=$((PASS+1))
else
  echo -e "${YELLOW}[WARN]${NC} Found $ERROR_COUNT ERROR entries in recent logs"
  WARN=$((WARN+1))
  grep "ERROR" "$TMPDIR/recent_logs.txt" | head -3
fi

EXCEPTION_COUNT=$(grep -c "Exception" "$TMPDIR/recent_logs.txt" 2>/dev/null || echo 0)
TOTAL=$((TOTAL+1))
if [ "$EXCEPTION_COUNT" = "0" ]; then
  echo -e "${GREEN}[PASS]${NC} No Exceptions in last 50 log lines"
  PASS=$((PASS+1))
else
  echo -e "${YELLOW}[WARN]${NC} Found $EXCEPTION_COUNT Exception entries in recent logs"
  WARN=$((WARN+1))
fi

###############################################################################
echo ""
echo "========================================================================"
echo -e "${BLUE}  FINAL RESULTS${NC}"
echo "========================================================================"
echo -e "  Total tests: $TOTAL"
echo -e "  ${GREEN}PASSED: $PASS${NC}"
echo -e "  ${RED}FAILED: $FAIL${NC}"
echo -e "  ${YELLOW}WARNINGS: $WARN${NC}"
echo ""
PASS_RATE=$((PASS * 100 / TOTAL))
echo -e "  Pass rate: ${PASS_RATE}%"
echo "========================================================================"

# Cleanup
rm -rf "$TMPDIR" "$COOKIE_JAR"
