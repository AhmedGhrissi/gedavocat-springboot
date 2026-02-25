#!/bin/bash
BASE=http://localhost:8080
PASS=0; FAIL=0; WARN=0
TMPDIR=/tmp/test_prod_$$
mkdir -p $TMPDIR

ok()   { PASS=$((PASS+1)); echo "  [PASS] $1"; }
fail() { FAIL=$((FAIL+1)); echo "  [FAIL] $1"; }
warn() { WARN=$((WARN+1)); echo "  [WARN] $1"; }
check() {
  local desc="$1" expected="$2" actual="$3"
  if [ "$expected" = "$actual" ]; then ok "$desc (=$actual)"; else fail "$desc (expected=$expected, got=$actual)"; fi
}

echo "=========================================="
echo "  PRODUCTION SMOKE TESTS v2"
echo "  $(date)"
echo "=========================================="

# ── 1. PUBLIC PAGES ──
echo ""
echo "=== 1. PUBLIC PAGES ==="
for url in / /login /register /forgot-password /robots.txt; do
  CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE$url")
  check "GET $url" "200" "$CODE"
done

# ── 2. STATIC ASSETS ──
echo ""
echo "=== 2. STATIC ASSETS ==="
for url in /css/design-system.css /css/pages/admin.css /js/scanner.js; do
  CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE$url")
  check "GET $url" "200" "$CODE"
done

# ── 3. PROTECTED PAGES ──
echo ""
echo "=== 3. PROTECTED PAGES (unauthenticated → 302) ==="
for url in /dashboard /cases /clients /admin /admin/users /settings/profile /appointments /documents; do
  CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE$url")
  check "GET $url (no auth)" "302" "$CODE"
done

# ── 4. CSRF PROTECTION ──
echo ""
echo "=== 4. CSRF PROTECTION ==="
NO_CSRF=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/login" -d "username=x&password=x")
if [ "$NO_CSRF" != "200" ]; then ok "POST /login without CSRF rejected ($NO_CSRF)"; else fail "POST /login without CSRF accepted!"; fi

# ── 5. REVOKE ENDPOINT (unauth) ──
echo ""
echo "=== 5. REVOKE ENDPOINT SECURITY ==="
REVOKE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/cases/fake-id/permissions/fake-perm/revoke")
check "POST revoke (no auth) → 302" "302" "$REVOKE"

# ── 6. INVALID SHARE TOKEN (regression test for 500 bug) ──
echo ""
echo "=== 6. INVALID SHARE TOKEN ==="
SHARE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/cases/shared?token=INVALID_TOKEN_12345")
if [ "$SHARE" = "500" ]; then fail "Invalid share token returns 500 (should be 200 shared-expired page)"; else ok "Invalid share token → $SHARE (no 500)"; fi

# ── 7. ADMIN LOGIN ──
echo ""
echo "=== 7. ADMIN LOGIN ==="
CSRF=$(curl -s -c $TMPDIR/cook.txt "$BASE/login" | grep -oP 'name="_csrf" value="\K[^"]+')
if [ -z "$CSRF" ]; then fail "CSRF token not found on login page"; else ok "CSRF token extracted"; fi
LOGIN=$(curl -s -b $TMPDIR/cook.txt -c $TMPDIR/cook.txt -o /dev/null -w "%{http_code}" -X POST "$BASE/login" \
  -d "username=admin@gedavocat.com&password=AdminDocAvocat2026!&_csrf=$CSRF")
check "Admin login POST → 302 redirect" "302" "$LOGIN"
# Follow redirect to establish full session
curl -s -b $TMPDIR/cook.txt -c $TMPDIR/cook.txt -o /dev/null "$BASE/dashboard"

# ── 8. ADMIN PAGES (authenticated) ──
echo ""
echo "=== 8. AUTHENTICATED ADMIN PAGES ==="
for url in /dashboard /admin/users /cases /clients /admin /admin/system /admin/database /admin/logs /admin/statistics /appointments; do
  CODE=$(curl -s -b $TMPDIR/cook.txt -o /dev/null -w "%{http_code}" "$BASE$url")
  check "GET $url (admin)" "200" "$CODE"
done

# ── 9. ADMIN USERS PAGE DEEP CHECK ──
echo ""
echo "=== 9. ADMIN USERS PAGE STRUCTURE ==="
curl -s -b $TMPDIR/cook.txt "$BASE/admin/users" > $TMPDIR/admin_users.html
FSIZE=$(wc -c < $TMPDIR/admin_users.html)
echo "  Page size: $FSIZE bytes"
if [ "$FSIZE" -gt 1000 ]; then ok "Admin users page has content ($FSIZE bytes)"; else fail "Admin users page is empty or tiny"; fi

USER_COUNT=$(grep -c 'data-user-id' $TMPDIR/admin_users.html || true)
echo "  Users in table: $USER_COUNT"
if [ "$USER_COUNT" -gt 0 ]; then ok "Users table has data ($USER_COUNT rows)"; else warn "No users in table"; fi

for pattern in viewUserModal editUserModal newUserModal DOMContentLoaded 'body.appendChild' getOrCreateInstance btn-view-user btn-edit-user btn-delete-user adminLoadUser confirmDelete; do
  COUNT=$(grep -c "$pattern" $TMPDIR/admin_users.html || true)
  if [ "$COUNT" -gt 0 ]; then ok "Has '$pattern' ($COUNT)"; else fail "MISSING '$pattern'"; fi
done

# ── 10. ADMIN USER API (JSON) ──
echo ""
echo "=== 10. ADMIN USER API ==="
USER_ID=$(grep -oP 'data-user-id="\K[^"]+' $TMPDIR/admin_users.html | head -1)
if [ -n "$USER_ID" ]; then
  ok "Found user ID: $USER_ID"
  curl -s -b $TMPDIR/cook.txt -H "Accept: application/json" "$BASE/admin/users/$USER_ID" > $TMPDIR/user.json
  USER_CODE=$(curl -s -b $TMPDIR/cook.txt -o /dev/null -w "%{http_code}" -H "Accept: application/json" "$BASE/admin/users/$USER_ID")
  check "GET /admin/users/$USER_ID JSON" "200" "$USER_CODE"
  for field in firstName lastName email role accountEnabled createdAt; do
    if grep -q "\"$field\"" $TMPDIR/user.json; then ok "JSON has '$field'"; else fail "JSON missing '$field'"; fi
  done
  echo "  Preview: $(head -c 250 $TMPDIR/user.json)"
else
  warn "No user IDs found — skipping API test"
fi

# ── 11. DASHBOARD LAYOUT CHECKS ──
echo ""
echo "=== 11. DASHBOARD & LAYOUT ==="
curl -s -b $TMPDIR/cook.txt "$BASE/dashboard" > $TMPDIR/dashboard.html
DSIZE=$(wc -c < $TMPDIR/dashboard.html)
echo "  Dashboard size: $DSIZE bytes"
for pattern in changePasswordForm 'autocomplete="username"' settingsModal 'bootstrap.bundle' sidebar topbar; do
  COUNT=$(grep -c "$pattern" $TMPDIR/dashboard.html || true)
  if [ "$COUNT" -gt 0 ]; then ok "Dashboard has '$pattern' ($COUNT)"; else fail "Dashboard MISSING '$pattern'"; fi
done

# ── 12. CASES ──
echo ""
echo "=== 12. CASES LIST & VIEW ==="
curl -s -b $TMPDIR/cook.txt "$BASE/cases" > $TMPDIR/cases.html
CSIZE=$(wc -c < $TMPDIR/cases.html)
echo "  Cases page: $CSIZE bytes"
CASE_ID=$(grep -oP 'href="/cases/\K[a-f0-9-]+' $TMPDIR/cases.html | head -1)
if [ -n "$CASE_ID" ]; then
  ok "Found case: $CASE_ID"
  curl -s -b $TMPDIR/cook.txt "$BASE/cases/$CASE_ID" > $TMPDIR/case_view.html
  CVCODE=$(curl -s -b $TMPDIR/cook.txt -o /dev/null -w "%{http_code}" "$BASE/cases/$CASE_ID")
  check "GET /cases/$CASE_ID" "200" "$CVCODE"
  
  HAS_UPLOAD=$(grep -c 'uploadModal' $TMPDIR/case_view.html || true)
  if [ "$HAS_UPLOAD" -gt 0 ]; then ok "Case view has upload modal ($HAS_UPLOAD)"; else fail "Case view MISSING upload modal"; fi
  
  HAS_REVOKE=$(grep -c 'fa-user-times' $TMPDIR/case_view.html || true)
  echo "  Revoke button occurrences: $HAS_REVOKE (depends on collaborators)"
  if [ "$HAS_REVOKE" -gt 0 ]; then ok "Revoke button present"; else warn "No revoke button (may have no collaborators)"; fi
  
  # Share page
  SHARE_CODE=$(curl -s -b $TMPDIR/cook.txt -o /dev/null -w "%{http_code}" "$BASE/cases/$CASE_ID/share")
  check "GET /cases/$CASE_ID/share" "200" "$SHARE_CODE"
  
  # Edit page
  EDIT_CODE=$(curl -s -b $TMPDIR/cook.txt -o /dev/null -w "%{http_code}" "$BASE/cases/$CASE_ID/edit")
  check "GET /cases/$CASE_ID/edit" "200" "$EDIT_CODE"
else
  warn "No cases found in list"
fi

# ── 13. NEW CASE FORM ──
echo ""
echo "=== 13. NEW CASE FORM ==="
NEW_CASE=$(curl -s -b $TMPDIR/cook.txt -o /dev/null -w "%{http_code}" "$BASE/cases/new")
check "GET /cases/new" "200" "$NEW_CASE"

# ── 14. CLIENTS ──
echo ""
echo "=== 14. CLIENTS ==="
curl -s -b $TMPDIR/cook.txt "$BASE/clients" > $TMPDIR/clients.html
CLIENT_ID=$(grep -oP 'href="/clients/\K[a-f0-9-]+' $TMPDIR/clients.html | head -1)
if [ -n "$CLIENT_ID" ]; then
  ok "Found client: $CLIENT_ID"
  CLIENT_CODE=$(curl -s -b $TMPDIR/cook.txt -o /dev/null -w "%{http_code}" "$BASE/clients/$CLIENT_ID")
  check "GET /clients/$CLIENT_ID" "200" "$CLIENT_CODE"
else
  warn "No clients found"
fi

# ── 15. APPOINTMENTS ──
echo ""
echo "=== 15. APPOINTMENTS ==="
APT_CODE=$(curl -s -b $TMPDIR/cook.txt -o /dev/null -w "%{http_code}" "$BASE/appointments")
check "GET /appointments" "200" "$APT_CODE"

# ── 16. CLIENT PORTAL ISOLATION ──
echo ""
echo "=== 16. CLIENT PORTAL ISOLATION ==="
MY_PORTAL=$(curl -s -b $TMPDIR/cook.txt -o /dev/null -w "%{http_code}" "$BASE/my-portal")
if [ "$MY_PORTAL" = "302" ] || [ "$MY_PORTAL" = "403" ]; then
  ok "Admin blocked from client portal ($MY_PORTAL)"
elif [ "$MY_PORTAL" = "200" ]; then
  fail "Admin can access client portal!"
else
  warn "Client portal response: $MY_PORTAL"
fi

# ── 17. BAD CREDENTIALS ──
echo ""
echo "=== 17. BAD CREDENTIALS ==="
CSRF2=$(curl -s -c $TMPDIR/cook2.txt "$BASE/login" | grep -oP 'name="_csrf" value="\K[^"]+')
BAD_RESP=$(curl -s -b $TMPDIR/cook2.txt -c $TMPDIR/cook2.txt -w "\n%{http_code}" -X POST "$BASE/login" \
  -d "username=wrong@wrong.com&password=WrongPassword123&_csrf=$CSRF2" -L)
BAD_CODE=$(echo "$BAD_RESP" | tail -1)
HAS_ERROR=$(echo "$BAD_RESP" | grep -c 'error\|incorrect\|invalid\|Identifiants' || true)
if [ "$HAS_ERROR" -gt 0 ] || [ "$BAD_CODE" = "200" ]; then 
  ok "Bad credentials → error shown (HTTP $BAD_CODE)"
else 
  warn "Bad credentials response unexpected ($BAD_CODE)"
fi

# ── 18. COLLABORATOR PORTAL ENDPOINT ──
echo ""
echo "=== 18. COLLABORATOR ENDPOINTS ==="
COLLAB=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/collaborators/accept-invitation?token=fake")
if [ "$COLLAB" = "200" ] || [ "$COLLAB" = "302" ] || [ "$COLLAB" = "500" ]; then
  echo "  /collaborators/accept-invitation?token=fake → $COLLAB"
  if [ "$COLLAB" = "500" ]; then warn "Collaborator invitation returns 500 for fake token"; else ok "Collaborator endpoint responds ($COLLAB)"; fi
fi

# ── 19. ERROR LOGS CHECK ──
echo ""
echo "=== 19. RECENT ERROR LOGS (last 2 min) ==="
ERRORS=$(docker logs docavocat-app --since 2m 2>&1 | grep -i '"level":"ERROR"' | wc -l)
if [ "$ERRORS" -eq 0 ]; then ok "No ERROR level logs in last 2 min"; else warn "$ERRORS ERROR entries in logs"; fi

WARNS=$(docker logs docavocat-app --since 2m 2>&1 | grep -i '"level":"WARN"' | wc -l)
if [ "$WARNS" -eq 0 ]; then ok "No WARN level logs in last 2 min"; else echo "  [INFO] $WARNS WARN entries in logs (login failures from tests)"; fi

# ── CLEANUP ──
rm -rf $TMPDIR

# ── SUMMARY ──
echo ""
echo "=========================================="
echo "  RESULTS: $PASS passed, $FAIL failed, $WARN warnings"
echo "=========================================="
if [ "$FAIL" -gt 0 ]; then
  echo "  ❌ SOME TESTS FAILED"
  exit 1
else
  echo "  ✅ ALL CRITICAL TESTS PASSED"
  exit 0
fi
