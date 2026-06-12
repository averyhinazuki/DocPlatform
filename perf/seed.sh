#!/usr/bin/env bash
# Seeds two tenants for the quota load test:
#   loadtest-a  — the "noisy" tenant that will flood report requests
#   loadtest-b  — the "quiet" tenant that must not be starved
# Each gets an admin user and one CSV template. Emits perf/seed.env with template IDs.
set -euo pipefail

BASE="${BASE:-http://localhost:8080/api}"
PASS="LoadTest123!"
DIR="$(cd "$(dirname "$0")" && pwd)"

seed_tenant() { # $1 slug, $2 cookie jar
  local slug="$1" jar="$2"
  # Tenant + admin user are idempotent-ish: ignore "already exists" failures
  curl -sf -X POST "$BASE/tenants" -H 'Content-Type: application/json' \
    -d "{\"name\":\"$slug\",\"slug\":\"$slug\",\"plan\":\"basic\"}" > /dev/null || true
  curl -sf -X POST "$BASE/auth/register" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$slug-admin\",\"password\":\"$PASS\",\"tenantSlug\":\"$slug\"}" > /dev/null || true
  curl -sf -c "$jar" -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$slug-admin\",\"password\":\"$PASS\"}" > /dev/null

  # One CSV template; reuse if it already exists
  local tpl_id
  tpl_id=$(curl -sf -b "$jar" "$BASE/templates" | python3 -c "
import json,sys
ts=[t for t in json.load(sys.stdin) if t.get('name')=='loadtest-csv']
print(ts[0]['id'] if ts else '')")
  if [ -z "$tpl_id" ]; then
    curl -sf -b "$jar" -X POST "$BASE/templates" -H 'Content-Type: application/json' \
      -d '{"name":"loadtest-csv","type":"loadtest","thymeleafTemplate":"<p th:text=\"${v1}\"></p>","variables":["v1"]}' > /dev/null
    tpl_id=$(curl -sf -b "$jar" "$BASE/templates" | python3 -c "
import json,sys
print([t for t in json.load(sys.stdin) if t.get('name')=='loadtest-csv'][0]['id'])")
  fi
  echo "$tpl_id"
}

TPL_A=$(seed_tenant loadtest-a /tmp/lt-a.cookies)
TPL_B=$(seed_tenant loadtest-b /tmp/lt-b.cookies)

# Multipart "request" JSON parts used by the JMeter samplers
cat > /tmp/lt-req-a.json <<EOF
{"reportType":"loadtest","format":"CSV","templateId":"$TPL_A","params":{"v1":"noisy"},"recipients":[]}
EOF
cat > /tmp/lt-req-b.json <<EOF
{"reportType":"loadtest","format":"CSV","templateId":"$TPL_B","params":{"v1":"quiet"},"recipients":[]}
EOF

cat > "$DIR/seed.env" <<EOF
TPL_A=$TPL_A
TPL_B=$TPL_B
EOF
echo "seeded: TPL_A=$TPL_A TPL_B=$TPL_B"
