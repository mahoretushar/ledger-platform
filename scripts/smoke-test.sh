#!/usr/bin/env bash
# End-to-end check through the API gateway: login, open accounts, transfer, retry, reverse,
# and wait for the Kafka-driven notification to reach the payee.
set -euo pipefail
GW=${GATEWAY_URL:-http://localhost:8080}
J='Content-Type: application/json'

pass() { printf '  \033[32mPASS\033[0m %s\n' "$1"; }
fail() { printf '  \033[31mFAIL\033[0m %s\n' "$1"; exit 1; }
token() { curl -sf -u "$1:$1-pass" -X POST "$GW/api/v1/auth/token" | jq -r .accessToken; }

echo "Smoke test against $GW"
for i in $(seq 1 60); do curl -sf "$GW/actuator/health" >/dev/null && break; sleep 2; done

ASHA=$(token asha); RAVI=$(token ravi); ADMIN=$(token admin)
[ -n "$ASHA" ] && pass "JWT issued through the gateway" || fail "login"

A=$(curl -sf -H "Authorization: Bearer $ASHA" -H "$J" -d '{"ownerName":"Asha","currency":"INR","openingBalance":1000}' "$GW/api/v1/accounts" | jq -r .id)
B=$(curl -sf -H "Authorization: Bearer $RAVI" -H "$J" -d '{"ownerName":"Ravi","currency":"INR","openingBalance":0}' "$GW/api/v1/accounts" | jq -r .id)
pass "accounts opened ($A, $B)"

KEY="smoke-$(date +%s)-$RANDOM"
BODY="{\"fromAccountId\":\"$A\",\"toAccountId\":\"$B\",\"amount\":250}"
T1=$(curl -sf -H "Authorization: Bearer $ASHA" -H "$J" -H "Idempotency-Key: $KEY" -d "$BODY" "$GW/api/v1/transfers" | jq -r .id)
T2=$(curl -sf -H "Authorization: Bearer $ASHA" -H "$J" -H "Idempotency-Key: $KEY" -d "$BODY" "$GW/api/v1/transfers" | jq -r .id)
[ "$T1" = "$T2" ] && pass "retry with same Idempotency-Key returned the same transfer" || fail "idempotency"

BAL=$(curl -sf -H "Authorization: Bearer $ASHA" "$GW/api/v1/accounts/$A" | jq -r .balance)
[ "$(printf '%.0f' "$BAL")" = "750" ] && pass "payer balance 750 (charged once)" || fail "balance was $BAL"

CODE=$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $RAVI" -H "$J" -H "Idempotency-Key: steal-$RANDOM" \
  -d "{\"fromAccountId\":\"$A\",\"toAccountId\":\"$B\",\"amount\":100}" "$GW/api/v1/transfers")
[ "$CODE" = "403" ] && pass "payee cannot debit the payer's account (403)" || fail "ownership check returned $CODE"

for i in $(seq 1 30); do
  N=$(curl -sf -H "Authorization: Bearer $RAVI" "$GW/api/v1/notifications" | jq "[.[] | select(.transferId==\"$T1\")] | length")
  [ "$N" = "1" ] && break; sleep 1
done
[ "$N" = "1" ] && pass "payee notified via Kafka -> notification-service" || fail "no notification for $T1"

R=$(curl -sf -X POST -H "Authorization: Bearer $ADMIN" "$GW/api/v1/transfers/$T1/reversal" | jq -r .reversalOf)
[ "$R" = "$T1" ] && pass "admin reversal created" || fail "reversal"
BAL=$(curl -sf -H "Authorization: Bearer $ASHA" "$GW/api/v1/accounts/$A" | jq -r .balance)
[ "$(printf '%.0f' "$BAL")" = "1000" ] && pass "payer balance restored to 1000" || fail "balance after reversal $BAL"

RID=$(curl -s -D - -o /dev/null -H "Authorization: Bearer $ASHA" "$GW/api/v1/accounts" | awk -F': ' 'tolower($1)=="x-request-id"{print $2}' | tr -d '\r')
[ -n "$RID" ] && pass "gateway returned X-Request-Id $RID" || fail "request id"
echo "All smoke checks passed."
