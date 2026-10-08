#!/usr/bin/env bash
# Walks through every endpoint with curl. Run against a FRESH database so the ids are txn_1..txn_6 / inv_1:
#   rm -rf data && ./mvnw spring-boot:run     # in one terminal
#   ./scripts/demo.sh                         # in another
set -euo pipefail
cd "$(dirname "$0")/.."
BASE_URL="${BASE_URL:-http://localhost:8080}"

step() { printf '\n\033[1m== %s\033[0m\n' "$1"; }
call() { curl -s -w '\n-> HTTP %{http_code}\n' -H 'Content-Type: application/json' "$@"; }

step "Create one transaction of each type"
for type in flight hotel rail trip_fee agent_call_fee; do
  call -X POST "$BASE_URL/transactions" -d @"examples/$type.json"
done

step "POST the same external_id twice -> 409 with existing_id, still one row"
call -X POST "$BASE_URL/transactions" -d @examples/duplicate_flight.json
call -X POST "$BASE_URL/transactions" -d @examples/flight.json
call "$BASE_URL/transactions?limit=100" | grep -o '"external_id":"flight-TXL-LHR-2026-03-11"' | wc -l | xargs echo "rows with that external_id:"

step "Get / list transactions"
call "$BASE_URL/transactions/txn_1"
call "$BASE_URL/transactions?limit=2"
call "$BASE_URL/transactions?limit=1000"

step "Create the trip invoice (flight + hotel + rail + both Navan fees) -> grand_total 685.70"
call -X POST "$BASE_URL/invoices" -d '{"transaction_ids":["txn_1","txn_2","txn_3","txn_4","txn_5"]}'

step "Get / list invoices"
call "$BASE_URL/invoices/inv_1"
call "$BASE_URL/invoices?limit=10"

step "PATCH / DELETE an invoiced transaction -> 409, nothing changes"
call -X PATCH "$BASE_URL/transactions/txn_1" -d '{"total": 300.00}'
call -X DELETE "$BASE_URL/transactions/txn_2"

step "Mixed currency invoice -> 409"
call -X POST "$BASE_URL/transactions" -d @examples/hotel_usd.json
call -X POST "$BASE_URL/invoices" -d '{"transaction_ids":["txn_1","txn_6"]}'

step "PATCH the invoice to a new set of transactions (snapshot rebuilt)"
call -X PATCH "$BASE_URL/invoices/inv_1" -d '{"transaction_ids":["txn_2","txn_3"]}'

step "The flight is no longer invoiced, so it can be edited and then deleted"
call -X PATCH "$BASE_URL/transactions/txn_1" -d '{"total": 230.00}'
call -X DELETE "$BASE_URL/transactions/txn_1"

step "Single-id invoice, then DELETE it (transactions stay)"
call -X POST "$BASE_URL/invoices" -d '{"transaction_id":"txn_4"}'
call -X DELETE "$BASE_URL/invoices/inv_2"
call "$BASE_URL/transactions/txn_4"
