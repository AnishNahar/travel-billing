package com.example.travelbilling.transaction;

import com.example.travelbilling.common.PageRequest;
import com.example.travelbilling.common.Sql;
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

/** Transaction header + tax/fee lines. Callers own the database transaction boundary. */
@Repository
public class TransactionRepository {

    private static final String SUMMARY_COLUMNS =
            "id, type, external_id, currency, occurred_at, total, created_at, updated_at";

    private static final RowMapper<TransactionSummary> SUMMARY_MAPPER = (rs, n) -> new TransactionSummary(
            new TransactionId(rs.getLong("id")),
            TransactionType.fromWire(rs.getString("type")).orElseThrow(),
            rs.getString("external_id"),
            rs.getString("currency"),
            Sql.instant(rs, "occurred_at"),
            rs.getBigDecimal("total"),
            Sql.instant(rs, "created_at"),
            Sql.instant(rs, "updated_at"));

    private final NamedParameterJdbcTemplate jdbc;
    private final MetadataRepository metadataRepository;

    public TransactionRepository(NamedParameterJdbcTemplate jdbc, MetadataRepository metadataRepository) {
        this.jdbc = jdbc;
        this.metadataRepository = metadataRepository;
    }

    /** Throws DuplicateKeyException when external_id is already taken (unique constraint). */
    public TransactionId insert(TransactionData data, Instant now) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO transactions (external_id, type, currency, occurred_at, total, created_at, updated_at)
                VALUES (:externalId, :type, :currency, :occurredAt, :total, :now, :now)
                """, new MapSqlParameterSource()
                .addValue("externalId", data.externalId())
                .addValue("type", data.type().wireName())
                .addValue("currency", data.currency())
                .addValue("occurredAt", Sql.timestamp(data.occurredAt()))
                .addValue("total", data.total())
                .addValue("now", Sql.timestamp(now)), keys, new String[] {"ID"});
        TransactionId id = new TransactionId(Objects.requireNonNull(keys.getKey()).longValue());
        insertContents(id, data);
        return id;
    }

    /** Replaces header money fields, tax/fee lines and metadata. type and external_id never change. */
    public void replace(TransactionId id, TransactionData data, Instant now) {
        jdbc.update("""
                UPDATE transactions
                SET currency = :currency, occurred_at = :occurredAt, total = :total,
                    version = version + 1, updated_at = :now
                WHERE id = :id
                """, new MapSqlParameterSource()
                .addValue("id", id.value())
                .addValue("currency", data.currency())
                .addValue("occurredAt", Sql.timestamp(data.occurredAt()))
                .addValue("total", data.total())
                .addValue("now", Sql.timestamp(now)));
        Map<String, Object> params = Map.of("id", id.value());
        jdbc.update("DELETE FROM transaction_tax_lines WHERE transaction_id = :id", params);
        jdbc.update("DELETE FROM transaction_fee_lines WHERE transaction_id = :id", params);
        metadataRepository.delete(id, data.type());
        insertContents(id, data);
    }

    public Optional<Transaction> findById(TransactionId id) {
        Map<String, Object> params = Map.of("id", id.value());
        List<TransactionSummary> headers = jdbc.query(
                "SELECT " + SUMMARY_COLUMNS + " FROM transactions WHERE id = :id", params, SUMMARY_MAPPER);
        if (headers.isEmpty()) {
            return Optional.empty();
        }
        TransactionSummary header = headers.getFirst();
        List<TaxLine> taxLines = jdbc.query(
                "SELECT name, rate, amount FROM transaction_tax_lines WHERE transaction_id = :id ORDER BY line_no",
                params, (rs, n) -> new TaxLine(
                        rs.getString("name"),
                        rs.getBigDecimal("rate") == null ? null : rs.getBigDecimal("rate").stripTrailingZeros(),
                        rs.getBigDecimal("amount")));
        List<FeeLine> feeLines = jdbc.query(
                "SELECT name, amount FROM transaction_fee_lines WHERE transaction_id = :id ORDER BY line_no",
                params, (rs, n) -> new FeeLine(rs.getString("name"), rs.getBigDecimal("amount")));
        return Optional.of(new Transaction(
                header.id(), header.type(), header.externalId(), header.currency(), header.occurredAt(),
                header.total(), taxLines, feeLines, metadataRepository.load(id, header.type()),
                header.createdAt(), header.updatedAt()));
    }

    public Optional<TransactionId> findIdByExternalId(String externalId) {
        return jdbc.query("SELECT id FROM transactions WHERE external_id = :externalId",
                        Map.of("externalId", externalId), (rs, n) -> new TransactionId(rs.getLong("id")))
                .stream().findFirst();
    }

    /** Takes a row lock until the surrounding DB transaction ends. Returns false if the row does not exist. */
    public boolean lockForUpdate(TransactionId id) {
        return !jdbc.queryForList("SELECT id FROM transactions WHERE id = :id FOR UPDATE",
                Map.of("id", id.value()), Long.class).isEmpty();
    }

    /** Index seek on invoice_lines.transaction_id. */
    public boolean isOnAnyInvoice(TransactionId id) {
        return !jdbc.queryForList("SELECT 1 FROM invoice_lines WHERE transaction_id = :id LIMIT 1",
                Map.of("id", id.value()), Integer.class).isEmpty();
    }

    /** Child rows go with it via ON DELETE CASCADE; the invoice_lines FK blocks deleting an invoiced row. */
    public void delete(TransactionId id) {
        jdbc.update("DELETE FROM transactions WHERE id = :id", Map.of("id", id.value()));
    }

    /** Newest first, keyset on the primary key: cost is O(limit) no matter how deep the page is. */
    public List<TransactionSummary> list(PageRequest page) {
        MapSqlParameterSource params = new MapSqlParameterSource("limit", page.limit());
        String where = "";
        if (page.beforeId() != null) {
            where = "WHERE id < :beforeId ";
            params.addValue("beforeId", page.beforeId());
        }
        return jdbc.query("SELECT " + SUMMARY_COLUMNS + " FROM transactions " + where + "ORDER BY id DESC LIMIT :limit",
                params, SUMMARY_MAPPER);
    }

    private void insertContents(TransactionId id, TransactionData data) {
        List<SqlParameterSource> taxRows = new ArrayList<>();
        for (int i = 0; i < data.taxLines().size(); i++) {
            TaxLine line = data.taxLines().get(i);
            taxRows.add(new MapSqlParameterSource()
                    .addValue("id", id.value())
                    .addValue("lineNo", i + 1)
                    .addValue("name", line.name())
                    .addValue("rate", line.rate())
                    .addValue("amount", line.amount()));
        }
        if (!taxRows.isEmpty()) {
            jdbc.batchUpdate("""
                    INSERT INTO transaction_tax_lines (transaction_id, line_no, name, rate, amount)
                    VALUES (:id, :lineNo, :name, :rate, :amount)
                    """, taxRows.toArray(SqlParameterSource[]::new));
        }

        List<SqlParameterSource> feeRows = new ArrayList<>();
        for (int i = 0; i < data.feeLines().size(); i++) {
            FeeLine line = data.feeLines().get(i);
            feeRows.add(new MapSqlParameterSource()
                    .addValue("id", id.value())
                    .addValue("lineNo", i + 1)
                    .addValue("name", line.name())
                    .addValue("amount", line.amount()));
        }
        if (!feeRows.isEmpty()) {
            jdbc.batchUpdate("""
                    INSERT INTO transaction_fee_lines (transaction_id, line_no, name, amount)
                    VALUES (:id, :lineNo, :name, :amount)
                    """, feeRows.toArray(SqlParameterSource[]::new));
        }

        metadataRepository.insert(id, data.metadata());
    }
}
