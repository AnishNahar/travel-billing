# Travel transactions & snapshot invoices

A small Spring Boot service that stores structured travel transactions — **flight**, **hotel**, **rail** and
**navan_fee** (trip fee, agent call fee) — and builds **frozen invoices** from them.

- Java 21, Spring Boot 3.5, Spring JDBC, Flyway, **file-backed H2** (`./data/travel-billing.mv.db`)
- No LLM or external service is called at runtime; no API keys are needed

Design notes are in [ARCHITECTURE.md](ARCHITECTURE.md).

## Run

Requires JDK 21+ (`java -version`). Maven is not needed — the wrapper downloads it.

```bash
./mvnw spring-boot:run
```

The API listens on `http://localhost:8080` (`GET /health` → `{"status":"ok"}`).
Optional environment variables: `PORT` (default `8080`), `DB_PATH` (default `./data/travel-billing`).
To start from an empty database, stop the app and `rm -rf data`.

## Test

```bash
./mvnw test
```

The tests boot the real app on a random port and call it over HTTP, using a fresh file-backed H2 database under
`target/test-db/`.

| # | Required behaviour | Test |
|---|---|---|
| 1 | Create flight / hotel / rail / trip_fee / agent_call_fee; GET returns coupons, address, legs, fee_kind | `TransactionApiTest.createsEveryTypeFromTheSeedsAndGetReturnsTheTypedStructure` |
| 2 | Duplicate `external_id` → 409 with existing id, one row | `TransactionApiTest.duplicateExternalIdDoesNotInsertASecondRow` |
| 3 | Trip invoice totals match `expected-invoice.json` (685.70 EUR) | `InvoiceApiTest.tripInvoiceMatchesTheExpectedFixture` |
| 4 | Snapshot: changing the stored flight leaves the invoice at 220.00 | `InvoiceApiTest.invoiceIsAFrozenSnapshotOfTheTransactions` |
| 5 | EUR + USD invoice → 409 | `InvoiceApiTest.mixedCurrenciesAreRejected` |
| 6 | Concurrent duplicate POSTs → one row | `ConcurrentCreateTest` (8 parallel POSTs × 10 rounds, plus a test that bypasses the app pre-check and hits the DB constraint directly) |
| 7 | PATCH invoiced transaction → 409, invoice unchanged | `InvoiceApiTest.patchingAnInvoicedTransactionIs409AndChangesNothing` |
| 8 | DELETE invoiced transaction → 409, row still there | `InvoiceApiTest.deletingAnInvoicedTransactionIs409AndTheRowStays` |
| 9 | DELETE invoice keeps its transactions | `InvoiceApiTest.deletingAnInvoiceKeepsItsTransactions` |
| 10 | List: default 50, `limit=1000` → 400 | `PaginationTest` |
| + | Overlapping PATCHes never leave a mix of two writers' `tax_lines`/`fee_lines` | `ConcurrentUpdateTest` |

## API

| Method | Path | Success | Errors |
|---|---|---|---|
| `POST` | `/transactions` | 201 + stored record | 400 invalid body / unknown `type` / unknown `fee_kind`; **409 duplicate `external_id`** (body has `existing_id`) |
| `GET` | `/transactions/{id}` | 200 | 404 |
| `GET` | `/transactions?limit=&cursor=` | 200 page | 400 if `limit` is outside 1–100 |
| `PATCH` | `/transactions/{id}` | 200 | 400; 404; **409 if the transaction is on any invoice** |
| `DELETE` | `/transactions/{id}` | 204 | 404; **409 if the transaction is on any invoice** |
| `POST` | `/invoices` | 201 + invoice | 400; 404 unknown transaction id; 409 mixed currency; 409 same id twice |
| `GET` | `/invoices/{id}` | 200 | 404 |
| `GET` | `/invoices?limit=&cursor=` | 200 page | 400 if `limit` is outside 1–100 |
| `PATCH` | `/invoices/{id}` | 200, snapshot rebuilt | 400; 404 invoice or transaction; 409 mixed currency / same id twice (invoice unchanged) |
| `DELETE` | `/invoices/{id}` | 204 (transactions are kept) | 404 |

Choices the brief left open:

- **Duplicate `external_id` → `409 Conflict`** with `{"error":"duplicate_external_id","existing_id":"txn_1"}`.
  The identity check runs *before* body validation, so a retried booking always gets its existing id back, even if
  the retry body is incomplete (the fixture `duplicate_flight` has no coupons).
- **The same transaction id twice on one invoice → `409`** (`duplicate_transaction_id`). Never silently de-duplicated.
  A transaction *may* be on more than one invoice (e.g. a re-issued invoice). Once it is on any invoice it is
  frozen: PATCH and DELETE return 409.
- **`limit` > 100 → `400`**, not capped silently. Default `limit` is 50.
- **Pagination is keyset (cursor), newest first.** Each page returns `next_cursor` while there may be more rows; pass
  it back as `?cursor=`. There is no `offset`, because OFFSET reads and throws away every skipped row. If the last
  page happens to be exactly full, its `next_cursor` returns one empty page.
- **Money** is `BigDecimal` / `DECIMAL(19,2)` — exactly 2 decimal places, never `double`. Amounts with more than
  2 decimals are rejected with 400, not rounded. Negative amounts are rejected. Tax `rate` is a fraction
  (`0.07` = 7 %), `DECIMAL(9,6)`.
- **PATCH /transactions/{id}** accepts any of `total`, `currency`, `occurred_at`, `tax_lines`, `fee_lines`,
  `metadata`. Lists and `metadata` are replaced whole; the result is validated like a create. `type` and
  `external_id` cannot change.
- **`total` is authoritative.** It is the client's gross amount; the service does not recompute it from the fare,
  taxes and fees.
- Invoice `totals` has one key per kind present — `flight`, `hotel`, `rail`, `trip_fee`, `agent_call_fee` — plus
  `grand_total`, the same shape as `expected-invoice.json`. `tax_total` and `fee_total` are informational: they are
  already inside `grand_total`, which is the sum of the gross transaction totals.

## Database, indexes and volume

The schema is [`V1__init.sql`](src/main/resources/db/migration/V1__init.sql); Flyway applies it on startup.

| Need | Where it lives in the schema |
|---|---|
| One row per `external_id`, even under concurrent POSTs | `uq_transactions_external_id`: a UNIQUE constraint (unique index) on `transactions.external_id` |
| Cheap `GET /transactions/{id}` and `GET /invoices/{id}` | Primary keys; child rows are keyed `(transaction_id, line_no)` and `(invoice_id, line_no)` |
| Cheap "is this transaction on an invoice?" (PATCH/DELETE check) | `ix_invoice_lines_transaction_id`, plus a foreign key so the database itself refuses to delete an invoiced transaction |
| Bounded lists | `ORDER BY id DESC LIMIT :limit` with a keyset cursor (`WHERE id < :cursor`). `limit` defaults to 50, max 100. Nothing is paginated in memory |
| Atomic writes | Header, tax lines, fee lines and metadata are written in one database transaction; PATCH/DELETE hold a row lock (`SELECT … FOR UPDATE`) |

## Example curls

[`scripts/demo.sh`](scripts/demo.sh) runs all of these in order. On a **fresh** database the ids are deterministic
(`txn_1` … `txn_6`, `inv_1`, `inv_2`):

```bash
rm -rf data && ./mvnw spring-boot:run   # terminal 1
./scripts/demo.sh                        # terminal 2
```

The request bodies in [`examples/`](examples) are the seed payloads from `fixtures/task-b/seed-payloads.json`, one
file per type.

**Create one of each type**

```bash
curl -s -X POST localhost:8080/transactions -H 'Content-Type: application/json' -d @examples/flight.json          # txn_1
curl -s -X POST localhost:8080/transactions -H 'Content-Type: application/json' -d @examples/hotel.json           # txn_2
curl -s -X POST localhost:8080/transactions -H 'Content-Type: application/json' -d @examples/rail.json            # txn_3
curl -s -X POST localhost:8080/transactions -H 'Content-Type: application/json' -d @examples/trip_fee.json        # txn_4
curl -s -X POST localhost:8080/transactions -H 'Content-Type: application/json' -d @examples/agent_call_fee.json  # txn_5
```

**Duplicate create — same `external_id` twice, only one row**

```bash
curl -s -X POST localhost:8080/transactions -H 'Content-Type: application/json' -d @examples/duplicate_flight.json
# 409 {"error":"duplicate_external_id","message":"...","existing_id":"txn_1"}
curl -s -X POST localhost:8080/transactions -H 'Content-Type: application/json' -d @examples/flight.json
# 409 again, same existing_id
curl -s 'localhost:8080/transactions?limit=100' | grep -o '"external_id":"flight-TXL-LHR-2026-03-11"' | wc -l
# 1
```

**Get / list transactions**

```bash
curl -s localhost:8080/transactions/txn_1
curl -s 'localhost:8080/transactions?limit=2'                 # {"items":[...],"limit":2,"next_cursor":"txn_4"}
curl -s 'localhost:8080/transactions?limit=2&cursor=txn_4'
curl -s 'localhost:8080/transactions?limit=1000'              # 400
```

**Create an invoice by transaction id(s)**

```bash
curl -s -X POST localhost:8080/invoices -H 'Content-Type: application/json' \
  -d '{"transaction_ids":["txn_1","txn_2","txn_3","txn_4","txn_5"]}'
# 201 inv_1, totals {flight 220.00, hotel 331.70, rail 94.00, trip_fee 25.00, agent_call_fee 15.00, grand_total 685.70}
# tax_rollup [Airport tax 15.00, VAT 0.07 21.70], fee_rollup [YQ 25.00, Booking fee 4.00]
curl -s -X POST localhost:8080/invoices -H 'Content-Type: application/json' -d '{"transaction_id":"txn_4"}'
# 201 inv_2 (single-id shape)
```

**Get / list invoices**

```bash
curl -s localhost:8080/invoices/inv_1
curl -s 'localhost:8080/invoices?limit=10'
```

**PATCH / DELETE a transaction**

```bash
curl -s -X PATCH localhost:8080/transactions/txn_1 -H 'Content-Type: application/json' -d '{"total": 300.00}'
# 409 {"error":"transaction_invoiced",...}  (txn_1 is on inv_1)
curl -s -X DELETE localhost:8080/transactions/txn_2
# 409 {"error":"transaction_invoiced",...}
```

**Mixed currency → 409**

```bash
curl -s -X POST localhost:8080/transactions -H 'Content-Type: application/json' -d @examples/hotel_usd.json   # txn_6, USD
curl -s -X POST localhost:8080/invoices -H 'Content-Type: application/json' -d '{"transaction_ids":["txn_1","txn_6"]}'
# 409 {"error":"mixed_currency",...}
```

**PATCH / DELETE an invoice**

```bash
curl -s -X PATCH localhost:8080/invoices/inv_1 -H 'Content-Type: application/json' -d '{"transaction_ids":["txn_2","txn_3"]}'
# 200, snapshot rebuilt from the current rows: grand_total 425.70

curl -s -X PATCH localhost:8080/transactions/txn_1 -H 'Content-Type: application/json' -d '{"total": 230.00}'
# 200 — txn_1 is no longer on any invoice, so it can be edited again
curl -s -X DELETE localhost:8080/transactions/txn_1                       # 204

curl -s -X DELETE localhost:8080/invoices/inv_2                           # 204
curl -s localhost:8080/transactions/txn_4                                 # 200 — transactions are kept
```

## Project layout

```
src/main/java/com/example/travelbilling/
  web/          HTTP only: controllers, pagination params, error mapping
  transaction/  TransactionService, TransactionRequestReader (validation), ExternalIdGuard (duplicate identity),
                TransactionRepository + MetadataRepository (SQL), domain records, metadata/ per-type records
  invoice/      InvoiceService (locking), InvoiceAssembler (snapshot + rollups, pure), InvoiceRepository (SQL)
  common/       Money, ids, pagination, exceptions
src/main/resources/db/migration/V1__init.sql   schema, constraints and indexes
src/test/java/...                              HTTP-level tests + an InvoiceAssembler unit test
```
