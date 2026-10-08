package com.example.travelbilling.invoice;

import com.example.travelbilling.common.PageRequest;
import com.example.travelbilling.common.Sql;
import com.example.travelbilling.transaction.TransactionId;
import com.example.travelbilling.transaction.TransactionType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Invoice header, snapshot lines and rollups. Never joins back to the live transaction tables. */
@Repository
public class InvoiceRepository {

    private static final String HEADER_COLUMNS =
            "id, currency, tax_total, fee_total, grand_total, line_count, created_at, updated_at";

    private static final RowMapper<InvoiceSummary> SUMMARY_MAPPER = (rs, n) -> new InvoiceSummary(
            new InvoiceId(rs.getLong("id")),
            rs.getString("currency"),
            rs.getInt("line_count"),
            rs.getBigDecimal("grand_total"),
            Sql.instant(rs, "created_at"),
            Sql.instant(rs, "updated_at"));

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public InvoiceRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public InvoiceId insert(InvoiceDraft draft, Instant now) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO invoices (currency, tax_total, fee_total, grand_total, line_count, created_at, updated_at)
                VALUES (:currency, :taxTotal, :feeTotal, :grandTotal, :lineCount, :now, :now)
                """, headerParams(draft, now), keys, new String[] {"ID"});
        InvoiceId id = new InvoiceId(Objects.requireNonNull(keys.getKey()).longValue());
        insertContents(id, draft);
        return id;
    }

    /** Swaps the whole snapshot in place; the caller holds the invoice row lock. */
    public void replace(InvoiceId id, InvoiceDraft draft, Instant now) {
        jdbc.update("""
                UPDATE invoices
                SET currency = :currency, tax_total = :taxTotal, fee_total = :feeTotal, grand_total = :grandTotal,
                    line_count = :lineCount, version = version + 1, updated_at = :now
                WHERE id = :id
                """, headerParams(draft, now).addValue("id", id.value()));
        Map<String, Object> params = Map.of("id", id.value());
        jdbc.update("DELETE FROM invoice_lines WHERE invoice_id = :id", params);
        jdbc.update("DELETE FROM invoice_tax_rollup WHERE invoice_id = :id", params);
        jdbc.update("DELETE FROM invoice_fee_rollup WHERE invoice_id = :id", params);
        insertContents(id, draft);
    }

    public Optional<Invoice> findById(InvoiceId id) {
        Map<String, Object> params = Map.of("id", id.value());
        List<Invoice> headers = jdbc.query("SELECT " + HEADER_COLUMNS + " FROM invoices WHERE id = :id", params,
                (rs, n) -> new Invoice(
                        id,
                        rs.getString("currency"),
                        List.of(),
                        List.of(),
                        List.of(),
                        new InvoiceTotals(rs.getBigDecimal("tax_total"), rs.getBigDecimal("fee_total"),
                                rs.getBigDecimal("grand_total")),
                        Sql.instant(rs, "created_at"),
                        Sql.instant(rs, "updated_at")));
        if (headers.isEmpty()) {
            return Optional.empty();
        }
        Invoice header = headers.getFirst();

        List<InvoiceLine> lines = jdbc.query("""
                SELECT line_no, transaction_id, external_id, type, total, snapshot
                FROM invoice_lines WHERE invoice_id = :id ORDER BY line_no
                """, params, (rs, n) -> new InvoiceLine(
                rs.getInt("line_no"),
                new TransactionId(rs.getLong("transaction_id")),
                rs.getString("external_id"),
                TransactionType.fromWire(rs.getString("type")).orElseThrow(),
                rs.getBigDecimal("total"),
                readJson(rs.getString("snapshot"))));
        List<TaxRollupLine> taxRollup = jdbc.query(
                "SELECT name, rate, amount FROM invoice_tax_rollup WHERE invoice_id = :id ORDER BY line_no",
                params, (rs, n) -> new TaxRollupLine(
                        rs.getString("name"),
                        rs.getBigDecimal("rate") == null ? null : rs.getBigDecimal("rate").stripTrailingZeros(),
                        rs.getBigDecimal("amount")));
        List<FeeRollupLine> feeRollup = jdbc.query(
                "SELECT name, amount FROM invoice_fee_rollup WHERE invoice_id = :id ORDER BY line_no",
                params, (rs, n) -> new FeeRollupLine(rs.getString("name"), rs.getBigDecimal("amount")));

        return Optional.of(new Invoice(id, header.currency(), lines, taxRollup, feeRollup, header.totals(),
                header.createdAt(), header.updatedAt()));
    }

    public boolean lockForUpdate(InvoiceId id) {
        return !jdbc.queryForList("SELECT id FROM invoices WHERE id = :id FOR UPDATE",
                Map.of("id", id.value()), Long.class).isEmpty();
    }

    /** Lines and rollups go with it via ON DELETE CASCADE. Transactions are untouched. */
    public void delete(InvoiceId id) {
        jdbc.update("DELETE FROM invoices WHERE id = :id", Map.of("id", id.value()));
    }

    /** Newest first, keyset on the primary key. */
    public List<InvoiceSummary> list(PageRequest page) {
        MapSqlParameterSource params = new MapSqlParameterSource("limit", page.limit());
        String where = "";
        if (page.beforeId() != null) {
            where = "WHERE id < :beforeId ";
            params.addValue("beforeId", page.beforeId());
        }
        return jdbc.query("SELECT " + HEADER_COLUMNS + " FROM invoices " + where + "ORDER BY id DESC LIMIT :limit",
                params, SUMMARY_MAPPER);
    }

    private static MapSqlParameterSource headerParams(InvoiceDraft draft, Instant now) {
        return new MapSqlParameterSource()
                .addValue("currency", draft.currency())
                .addValue("taxTotal", draft.totals().taxTotal())
                .addValue("feeTotal", draft.totals().feeTotal())
                .addValue("grandTotal", draft.totals().grandTotal())
                .addValue("lineCount", draft.lines().size())
                .addValue("now", Sql.timestamp(now));
    }

    private void insertContents(InvoiceId id, InvoiceDraft draft) {
        List<SqlParameterSource> lineRows = new ArrayList<>();
        for (InvoiceLine line : draft.lines()) {
            lineRows.add(new MapSqlParameterSource()
                    .addValue("invoiceId", id.value())
                    .addValue("lineNo", line.lineNo())
                    .addValue("transactionId", line.transactionId().value())
                    .addValue("externalId", line.externalId())
                    .addValue("type", line.type().wireName())
                    .addValue("currency", draft.currency())
                    .addValue("total", line.total())
                    .addValue("snapshot", writeJson(line.snapshot())));
        }
        batch("""
                INSERT INTO invoice_lines (invoice_id, line_no, transaction_id, external_id, type, currency, total, snapshot)
                VALUES (:invoiceId, :lineNo, :transactionId, :externalId, :type, :currency, :total, :snapshot)
                """, lineRows);

        List<SqlParameterSource> taxRows = new ArrayList<>();
        for (int i = 0; i < draft.taxRollup().size(); i++) {
            TaxRollupLine tax = draft.taxRollup().get(i);
            taxRows.add(new MapSqlParameterSource()
                    .addValue("invoiceId", id.value())
                    .addValue("lineNo", i + 1)
                    .addValue("name", tax.name())
                    .addValue("rate", tax.rate())
                    .addValue("amount", tax.amount()));
        }
        batch("""
                INSERT INTO invoice_tax_rollup (invoice_id, line_no, name, rate, amount)
                VALUES (:invoiceId, :lineNo, :name, :rate, :amount)
                """, taxRows);

        List<SqlParameterSource> feeRows = new ArrayList<>();
        for (int i = 0; i < draft.feeRollup().size(); i++) {
            FeeRollupLine fee = draft.feeRollup().get(i);
            feeRows.add(new MapSqlParameterSource()
                    .addValue("invoiceId", id.value())
                    .addValue("lineNo", i + 1)
                    .addValue("name", fee.name())
                    .addValue("amount", fee.amount()));
        }
        batch("""
                INSERT INTO invoice_fee_rollup (invoice_id, line_no, name, amount)
                VALUES (:invoiceId, :lineNo, :name, :amount)
                """, feeRows);
    }

    private void batch(String sql, List<SqlParameterSource> rows) {
        if (!rows.isEmpty()) {
            jdbc.batchUpdate(sql, rows.toArray(SqlParameterSource[]::new));
        }
    }

    private String writeJson(JsonNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise invoice snapshot", e);
        }
    }

    private JsonNode readJson(String json) {
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored invoice snapshot is not valid JSON", e);
        }
    }
}
