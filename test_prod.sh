#!/bin/bash
BASE=http://localhost:8080
PASS=0
FAIL=0
WARN=0

ok()   { PASS=$((PASS+1)); echo "  [PASS] $1"; }
fail() { FAIL=$((FAIL+1)); echo "  [FAIL] $1"; }
warn() { WARN=$((WARN+1)); echo "  [WARN] $1"; }
check() {
  local desc="$1" expected="$2" actual="$3"
  if [ "$expected" = "$actual" ]; then ok "$desc (=$actual)"; else fail "$desc (expected=$expected, got=$actual)"; fi
}

echo "=========================================="
echo "  PRODUCTION SMOKE TESTS"
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

# ── 3. PROTECTED PAGES (should 302 when unauthenticated) ──
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

# ── 5. PERMISSION REVOKE (unauth) ──
echo ""
echo "=== 5. REVOKE ENDPOINT SECURITY ==="
REVOKE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/cases/fake-id/permissions/fake-perm/revoke")
check "POST revoke (no auth) → 302" "302" "$REVOKE"

# ── 6. INVALID SHARE TOKEN ──
echo ""
echo "=== 6. INVALID SHARE TOKEN ==="
SHARE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/cases/shared?token=INVALID_TOKEN_12345")
if [ "$SHARE" = "500" ]; then warn "Invalid share token returns 500 (should be 400/404)"; else ok "Invalid share token → $SHARE"; fi

# ── 7. ADMIN LOGIN ──
echo ""
echo "=== 7. ADMIN LOGIN ==="
CSRF=$(curl -s -c /tmp/testcook.txt "$BASE/login" | grep -oP 'name="_csrf" value="\K[^"]+')
if [ -z "$CSRF" ]; then fail "CSRF token not found on login page"; else ok "CSRF token extracted"; fi
LOGIN=$(curl -s -b /tmp/testcook.txt -c /tmp/testcook.txt -o /dev/null -w "%{http_code}" -X POST "$BASE/login" \
  -d "username=admin@gedavocat.com&password=AdminDocAvocat2026!&_csrf=$CSRF")
check "Admin login POST → 302 redirect" "302" "$LOGIN"

# ── 8. AUTHENTICATED ADMIN PAGES ──
echo ""
echo "=== 8. AUTHENTICATED ADMIN PAGES ==="
for url in /dashboard /admin/users /cases /clients /admin /admin/system /admin/database /admin/logs /admin/statistics; do
  CODE=$(curl -s -b /tmp/testcook.txt -o /dev/null -w "%{http_code}" -L "$BASE$url")
  check "GET $url (admin)" "200" "$CODE"
done

# ── 9. ADMIN USERS PAGE STRUCTURE ──
echo ""
echo "=== 9. ADMIN USERS PAGE STRUCTURE ==="
ADMIN_HTML=$(curl -s -b /tmp/testcook.txt "$BASE/admin/users")
echo "  Page size: ${#ADMIN_HTML} chars"

USER_COUNT=$(echo "$ADMIN_HTML" | grep -oP 'data-user-id="[^"]+"' | wc -l)
echo "  Users in table: $USER_COUNT"
if [ "$USER_COUNT" -gt 0 ]; then ok "Users table has data ($USER_COUNT rows)"; else warn "No users in table (might be empty DB)"; fi

for pattern in viewUserModal editUserModal newUserModal DOMContentLoaded 'body.appendChild' getOrCreateInstance btn-view-user btn-edit-user btn-delete-user adminLoadUser confirmDelete; do
  COUNT=$(echo "$ADMIN_HTML" | grep -c "$pattern" || true)
  if [ "$COUNT" -gt 0 ]; then ok "Markup has '$pattern' ($COUNT)"; else fail "Markup MISSING '$pattern'"; fi
done

# ── 10. ADMIN USER API (JSON) ──
echo ""
echo "=== 10. ADMIN USER API ==="
USER_ID=$(echo "$ADMIN_HTML" | grep -oP 'data-user-id="\K[^"]+' | head -1)
if [ -n "$USER_ID" ]; then
  ok "Found user ID: $USER_ID"
  USER_JSON_CODE=$(curl -s -b /tmp/testcook.txt -o /dev/null -w "%{http_code}" -H "Accept: application/json" "$BASE/admin/users/$USER_ID")
  check "GET /admin/users/$USER_ID JSON" "200" "$USER_JSON_CODE"
  USER_JSON=$(curl -s -b /tmp/testcook.txt -H "Accept: application/json" "$BASE/admin/users/$USER_ID")
  # Check JSON fields
  for field in firstName lastName email role accountEnabled createdAt; do
    if echo "$USER_JSON" | grep -q "\"$field\""; then ok "JSON has field '$field'"; else fail "JSON missing '$field'"; fi
  done
  echo "  User JSON preview: $(echo "$USER_JSON" | head -c 300)"
else
  warn "No user IDs found — skipping API test"
fi

# ── 11. DASHBOARD / LAYOUT CHECKS ──
echo ""
echo "=== 11. DASHBOARD & LAYOUT ==="
DASH=$(curl -s -b /tmp/testcook.txt "$BASE/dashboard")
echo "  Dashboard size: ${#DASH} chars"
for pattern in changePasswordForm 'autocomplete="username"' settingsModal 'bootstrap.bundle' sidebar topbar; do
  COUNT=$(echo "$DASH" | grep -c "$pattern" || true)
  if [ "$COUNT" -gt 0 ]; then ok "Dashboard has '$pattern' ($COUNT)"; else fail "Dashboard MISSING '$pattern'"; fi
done

# ── 12. CASES LIST & VIEW ──
echo ""
echo "=== 12. CASES ==="
CASES=$(curl -s -b /tmp/testcook.txt "$BASE/cases")
echo "  Cases page size: ${#CASES} chars"
CASE_ID=$(echo "$CASES" | grep -oP 'href="/cases/\K[a-f0-9-]+' | head -1)
if [ -n "$CASE_ID" ]; then
  ok "Found case: $CASE_ID"
  CASE_CODE=$(curl -s -b /tmp/testcook.txt -o /dev/null -w "%{http_code}" "$BASE/cases/$CASE_ID")
  check "GET /cases/$CASE_ID" "200" "$CASE_CODE"
  
  CASE_HTML=$(curl -s -b /tmp/testcook.txt "$BASE/cases/$CASE_ID")
  for pattern in uploadModal fa-user-times 'permissions.*revoke\|fa-user-times'; do
    COUNT=$(echo "$CASE_HTML" | grep -c "$pattern" || true)
    if [ "$COUNT" -gt 0 ]; then ok "Case view has '$pattern' ($COUNT)"; else warn "Case view missing '$pattern' (may have no collaborators)"; fi
  done
  
  # Share page
  SHARE_CODE=$(curl -s -b /tmp/testcook.txt -o /dev/null -w "%{http_code}" "$BASE/cases/$CASE_ID/share")
  check "GET /cases/$CASE_ID/share" "200" "$SHARE_CODE"
  
  # Edit page
  EDIT_CODE=$(curl -s -b /tmp/testcook.txt -o /dev/null -w "%{http_code}" "$BASE/cases/$CASE_ID/edit")
  check "GET /cases/$CASE_ID/edit" "200" "$EDIT_CODE"
else
  warn "No cases found in list"
fi

# ── 13. NEW CASE FORM ──
echo ""
echo "=== 13. NEW CASE FORM ==="
NEW_CASE=$(curl -s -b /tmp/testcook.txt -o /dev/null -w "%{http_code}" "$BASE/cases/new")
check "GET /cases/new" "200" "$NEW_CASE"

# ── 14. CLIENTS ──
echo ""
echo "=== 14. CLIENTS ==="
CLIENTS=$(curl -s -b /tmp/testcook.txt "$BASE/clients")
echo "  Clients page size: ${#CLIENTS} chars"
CLIENT_ID=$(echo "$CLIENTS" | grep -oP 'href="/clients/\K[a-f0-9-]+' | head -1)
if [ -n "$CLIENT_ID" ]; then
  ok "Found client: $CLIENT_ID"
  CLIENT_CODE=$(curl -s -b /tmp/testcook.txt -o /dev/null -w "%{http_code}" "$BASE/clients/$CLIENT_ID")
  check "GET /clients/$CLIENT_ID" "200" "$CLIENT_CODE"
fi

# ── 15. APPOINTMENTS ──
echo ""
echo "=== 15. APPOINTMENTS ==="
APT_CODE=$(curl -s -b /tmp/testcook.txt -o /dev/null -w "%{http_code}" "$BASE/appointments")
check "GET /appointments" "200" "$APT_CODE"

# ── 16. WRONG-ROLE ACCESS ──
echo ""
echo "=== 16. CLIENT PORTAL AS ADMIN ==="
MY_PORTAL=$(curl -s -b /tmp/testcook.txt -o /dev/null -w "%{http_code}" "$BASE/my-portal")
if [ "$MY_PORTAL" = "302" ] || [ "$MY_PORTAL" = "403" ]; then
  ok "Admin blocked from client portal ($MY_PORTAL)"
else
  fail "Admin can access client portal ($MY_PORTAL)"
fi

# ── 17. WRONG LOGIN ──
echo ""
echo "=== 17. BAD CREDENTIALS ==="
CSRF2=$(curl -s -c /tmp/testcook2.txt "$BASE/login" | grep -oP 'name="_csrf" value="\K[^"]+')
BAD_LOGIN=$(curl -s -b /tmp/testcook2.txt -c /tmp/testcook2.txt -o /dev/null -w "%{http_code}" -X POST "$BASE/login" \
  -d "username=wrong@wrong.com&password=WrongPassword123&_csrf=$CSRF2" -L)
if [ "$BAD_LOGIN" = "200" ]; then ok "Bad credentials → login page (200 with error)"; else warn "Bad credentials → $BAD_LOGIN"; fi
rm -f /tmp/testcook2.txt

# ── 18. APP LOGS CHECK ──
echo ""
echo "=== 18. RECENT ERROR LOGS ==="
ERRORS=$(docker logs docavocat-app --since 2m 2>&1 | grep -i "ERROR\|Exception\|WARN" | tail -10)
if [ -n "$ERRORS" ]; then
  warn "Recent log errors/warnings:"
  echo "$ERRORS" | while read line; do echo "    $line"; done
else
  ok "No recent errors in logs"
fi

# ── SUMMARY ──
echo ""
echo "=========================================="
echo "  RESULTS: $PASS passed, $FAIL failed, $WARN warnings"
echo "=========================================="

rm -f /tmp/testcook.txt
