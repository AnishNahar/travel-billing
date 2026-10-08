# Architecture

## Layers

`web` (controllers: parse ids and pagination params, map exceptions to status codes) →
`TransactionService` / `InvoiceService` (database transaction boundaries, locking, business rules) →
`TransactionRequestReader` (validation), `ExternalIdGuard` (duplicate identity), `InvoiceAssembler` (snapshot and
rollup maths; pure, unit-tested) → repositories (plain SQL via `NamedParameterJdbcTemplate`). Flyway owns the schema.
I chose plain JDBC over JPA so every lock, index and `LIMIT` is visible in the code.

## Tables and models

| Concern | Tables | Model |
|---|---|---|
| Shared header | `transactions` (`external_id` UNIQUE, `type` CHECK, `currency`, `occurred_at`, `total`, `version`) | `Transaction`, `TransactionData` |
| Taxes vs fees | `transaction_tax_lines` (name, rate, amount) and `transaction_fee_lines` (name, amount): two tables, ordered by `line_no` | `TaxLine`, `FeeLine` |
| Flight | `flight_details` (origin, destination, fare) + `flight_coupons` (flight_number, carrier, from, to, departure, arrival; indexed by flight_number) | `FlightMetadata`, `FlightCoupon` |
| Hotel | `hotel_stays` (property name, street, city, postal_code, country, check-in/out, nights, rate) + `hotel_extras` | `HotelMetadata`, `HotelAddress`, `HotelExtra` |
| Rail | `rail_legs`, one row per leg (a change of train = 2 rows) | `RailMetadata`, `RailLeg` |
| Navan fee | `navan_fees` (`fee_kind` CHECK in `trip_fee`/`agent_call_fee`, description, `related_external_id` indexed) | `NavanFeeMetadata`, `FeeKind` |

There is one table per type, not a JSON blob. Every field the brief names is a typed, queryable column, and the
database itself rejects an unknown `type` or `fee_kind`. `TransactionMetadata` is a sealed interface, so the
repository's `switch` over the types is exhaustive. Adding a type means adding a table and a record, and the
compiler points out every place that needs a new branch. Taxes and fees are never mixed: the tables, the models
and the invoice rollups all keep them separate.

## `external_id` uniqueness and concurrent POSTs

- `UNIQUE (external_id)` on `transactions` is the guarantee.
- `ExternalIdGuard` does a quick lookup first, so a plain retry gets `409` + `existing_id` without doing any work.
- Two overlapping POSTs can both pass that lookup. Both then `INSERT` inside their own database transaction. The
  second insert blocks on the unique index until the first commits, then fails with a duplicate-key error. Spring
  turns that into `DuplicateKeyException`, which the guard returns as `409` with the winner's id.
- Header, tax lines, fee lines and metadata are inserted in **one** database transaction, so a losing insert leaves
  no orphaned child rows.
- `ConcurrentCreateTest` fires 8 parallel POSTs × 10 rounds. It also has a test that skips the pre-check entirely
  and shows the constraint alone rejects the second insert.

## Invoice snapshot

`invoice_lines` stores a **copy** of each transaction at invoicing time:

- `type`, `external_id`, `currency` and `total` as real columns
- the full record (tax lines, fee lines, metadata) as an immutable JSON document in `snapshot`

The tax rollup (grouped by `name` + `rate`, in first-appearance order) and the fee rollup (grouped by `name`) go in
`invoice_tax_rollup` and `invoice_fee_rollup`. Totals go on the `invoices` row. `GET /invoices/{id}` reads only these
tables and never joins back to `transactions`. The test changes the stored flight with SQL and the invoice still
shows 220.00.

JSON is used only for the snapshot, which is never queried by field and never changes after it is written. All the
money aggregates are columns.

**Locking:**

- **Create or PATCH an invoice** locks each listed transaction row with `SELECT … FOR UPDATE`, in ascending id order
  so two overlapping invoices cannot deadlock. It then copies the rows.
- **PATCH or DELETE a transaction** takes the same row lock, then checks `invoice_lines` (`ix_invoice_lines_transaction_id`).
- Because both sides lock the same row, invoicing and editing run one after the other. Either the edit lands first
  and the invoice copies the new values, or the invoice lands first and the edit gets `409`. An invoice can never
  capture half of an edit.
- The `invoice_lines.transaction_id` foreign key has no `ON DELETE`, so the database also refuses to delete an
  invoiced transaction.
- `PATCH /invoices/{id}` locks the invoice row, then deletes and rewrites its lines and rollups in one database
  transaction. Any error (404 or mixed currency) rolls back and leaves the old snapshot in place.

## Money

`BigDecimal` everywhere and `DECIMAL(19,2)` in the database. JSON is parsed with `USE_BIG_DECIMAL_FOR_FLOATS`, so
`331.7` never passes through a `double`. Amounts with more than 2 decimals are rejected, not rounded.

## High volume

**What breaks first, and what this service does about it:**

1. **Duplicate POSTs under retries.** The unique index makes this a single index probe plus a cheap 409. There is
   no lock service, and no "check then insert" race that the constraint doesn't cover.
2. **Concurrent PATCH or DELETE on the same row.** Every write runs in one database transaction holding that row's
   lock. Last write wins, but the tax and fee lists are always swapped as a whole. Writes to *different*
   transactions never block each other. `LOCK_TIMEOUT=10000` turns a stuck lock into an error instead of a hang.
3. **Large lists.** `GET /transactions` and `GET /invoices` always use `ORDER BY id DESC LIMIT :limit`, with a
   keyset cursor (`WHERE id < :cursor`). That is a primary-key range scan, so page 10,000 costs the same as page 1.
   `limit` is capped at 100 (400 above that). List rows are headers only, so a page is one query and never N+1.
4. **Lookups.** These are all index seeks:
   - by id: primary key
   - by `external_id`: the unique index
   - "is this transaction invoiced?": `ix_invoice_lines_transaction_id`
   - invoice children: `(invoice_id, line_no)` primary keys

   Child tables are keyed `(transaction_id, line_no)`, so loading a transaction is a few primary-key range reads.

**The next limit:** H2 is an embedded database. One app process owns the database file, so the service can't scale
out to several instances. For production I'd move the same schema to Postgres; only the identity syntax and the
`CLOB` column would change. After that, the first
pressure would be write throughput on `transactions`. Postgres handles that with connection pooling, and
partitioning by `occurred_at` later if needed.
