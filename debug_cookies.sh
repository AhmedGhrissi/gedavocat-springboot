#!/bin/bash
BASE=http://localhost:8080
rm -f /tmp/dc.txt
echo "=== Step 1: Get login page + CSRF ==="
CSRF=$(curl -s -c /tmp/dc.txt "$BASE/login" | grep -oP 'name="_csrf" value="\K[^"]+')
echo "CSRF: $CSRF"
echo "Cookies after login page:"
cat /tmp/dc.txt

echo ""
echo "=== Step 2: POST login ==="
curl -v -b /tmp/dc.txt -c /tmp/dc.txt -o /dev/null -X POST "$BASE/login" \
  -d "username=admin@gedavocat.com&password=AdminDocAvocat2026!&_csrf=$CSRF" 2>&1 | grep -E 'Set-Cookie|Location|< HTTP'

echo ""
echo "Cookies after login POST:"
cat /tmp/dc.txt

echo ""
echo "=== Step 3: Follow to dashboard ==="
DASH=$(curl -s -b /tmp/dc.txt -c /tmp/dc.txt -o /dev/null -w "%{http_code}" "$BASE/dashboard")
echo "Dashboard: $DASH"

echo ""
echo "=== Step 4: Access admin/users ==="
ADMIN=$(curl -s -b /tmp/dc.txt -o /dev/null -w "%{http_code}" "$BASE/admin/users")
echo "Admin users: $ADMIN"

echo ""
echo "Final cookies:"
cat /tmp/dc.txt
rm -f /tmp/dc.txt
