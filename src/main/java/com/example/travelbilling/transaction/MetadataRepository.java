package com.example.travelbilling.transaction;

import com.example.travelbilling.common.Sql;
import com.example.travelbilling.transaction.metadata.FeeKind;
import com.example.travelbilling.transaction.metadata.FlightCoupon;
import com.example.travelbilling.transaction.metadata.FlightMetadata;
import com.example.travelbilling.transaction.metadata.HotelAddress;
import com.example.travelbilling.transaction.metadata.HotelExtra;
import com.example.travelbilling.transaction.metadata.HotelMetadata;
import com.example.travelbilling.transaction.metadata.NavanFeeMetadata;
import com.example.travelbilling.transaction.metadata.RailLeg;
import com.example.travelbilling.transaction.metadata.RailMetadata;
import com.example.travelbilling.transaction.metadata.TransactionMetadata;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Persists the type-specific part of a transaction into its per-type tables. */
@Repository
public class MetadataRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public MetadataRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(TransactionId id, TransactionMetadata metadata) {
        switch (metadata) {
            case FlightMetadata flight -> insertFlight(id.value(), flight);
            case HotelMetadata hotel -> insertHotel(id.value(), hotel);
            case RailMetadata rail -> insertRail(id.value(), rail);
            case NavanFeeMetadata fee -> insertNavanFee(id.value(), fee);
        }
    }

    public void delete(TransactionId id, TransactionType type) {
        Map<String, Object> params = Map.of("id", id.value());
        List<String> tables = switch (type) {
            case FLIGHT -> List.of("flight_coupons", "flight_details");
            case HOTEL -> List.of("hotel_extras", "hotel_stays");
            case RAIL -> List.of("rail_legs");
            case NAVAN_FEE -> List.of("navan_fees");
        };
        tables.forEach(table -> jdbc.update("DELETE FROM " + table + " WHERE transaction_id = :id", params));
    }

    public TransactionMetadata load(TransactionId id, TransactionType type) {
        return switch (type) {
            case FLIGHT -> loadFlight(id.value());
            case HOTEL -> loadHotel(id.value());
            case RAIL -> loadRail(id.value());
            case NAVAN_FEE -> loadNavanFee(id.value());
        };
    }

    private void insertFlight(long id, FlightMetadata flight) {
        jdbc.update("""
                INSERT INTO flight_details (transaction_id, origin, destination, fare)
                VALUES (:id, :origin, :destination, :fare)
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("origin", flight.origin())
                .addValue("destination", flight.destination())
                .addValue("fare", flight.fare()));

        List<SqlParameterSource> rows = new ArrayList<>();
        for (int i = 0; i < flight.coupons().size(); i++) {
            FlightCoupon coupon = flight.coupons().get(i);
            rows.add(new MapSqlParameterSource()
                    .addValue("id", id)
                    .addValue("lineNo", i + 1)
                    .addValue("flightNumber", coupon.flightNumber())
                    .addValue("carrier", coupon.carrier())
                    .addValue("from", coupon.from())
                    .addValue("to", coupon.to())
                    .addValue("departure", Sql.timestamp(coupon.departure()))
                    .addValue("arrival", Sql.timestamp(coupon.arrival())));
        }
        batch("""
                INSERT INTO flight_coupons (transaction_id, line_no, flight_number, carrier, from_location, to_location,
                                            departure_at, arrival_at)
                VALUES (:id, :lineNo, :flightNumber, :carrier, :from, :to, :departure, :arrival)
                """, rows);
    }

    private void insertHotel(long id, HotelMetadata hotel) {
        jdbc.update("""
                INSERT INTO hotel_stays (transaction_id, property_name, street, city, postal_code, country,
                                         check_in, check_out, nights, room_rate_per_night)
                VALUES (:id, :name, :street, :city, :postalCode, :country, :checkIn, :checkOut, :nights, :rate)
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("name", hotel.name())
                .addValue("street", hotel.address().street())
                .addValue("city", hotel.address().city())
                .addValue("postalCode", hotel.address().postalCode())
                .addValue("country", hotel.address().country())
                .addValue("checkIn", hotel.checkIn())
                .addValue("checkOut", hotel.checkOut())
                .addValue("nights", hotel.nights())
                .addValue("rate", hotel.roomRatePerNight()));

        List<SqlParameterSource> rows = new ArrayList<>();
        for (int i = 0; i < hotel.extras().size(); i++) {
            HotelExtra extra = hotel.extras().get(i);
            rows.add(new MapSqlParameterSource()
                    .addValue("id", id)
                    .addValue("lineNo", i + 1)
                    .addValue("name", extra.name())
                    .addValue("amount", extra.amount()));
        }
        batch("""
                INSERT INTO hotel_extras (transaction_id, line_no, name, amount)
                VALUES (:id, :lineNo, :name, :amount)
                """, rows);
    }

    private void insertRail(long id, RailMetadata rail) {
        List<SqlParameterSource> rows = new ArrayList<>();
        for (int i = 0; i < rail.legs().size(); i++) {
            RailLeg leg = rail.legs().get(i);
            rows.add(new MapSqlParameterSource()
                    .addValue("id", id)
                    .addValue("lineNo", i + 1)
                    .addValue("origin", leg.origin())
                    .addValue("destination", leg.destination())
                    .addValue("departure", Sql.timestamp(leg.departure()))
                    .addValue("arrival", Sql.timestamp(leg.arrival()))
                    .addValue("trainNumber", leg.trainNumber())
                    .addValue("travelClass", leg.travelClass()));
        }
        batch("""
                INSERT INTO rail_legs (transaction_id, line_no, origin, destination, departure_at, arrival_at,
                                       train_number, travel_class)
                VALUES (:id, :lineNo, :origin, :destination, :departure, :arrival, :trainNumber, :travelClass)
                """, rows);
    }

    private void insertNavanFee(long id, NavanFeeMetadata fee) {
        jdbc.update("""
                INSERT INTO navan_fees (transaction_id, fee_kind, description, related_external_id)
                VALUES (:id, :feeKind, :description, :relatedExternalId)
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("feeKind", fee.feeKind().wireName())
                .addValue("description", fee.description())
                .addValue("relatedExternalId", fee.relatedExternalId()));
    }

    private FlightMetadata loadFlight(long id) {
        Map<String, Object> params = Map.of("id", id);
        List<FlightCoupon> coupons = jdbc.query("""
                SELECT flight_number, carrier, from_location, to_location, departure_at, arrival_at
                FROM flight_coupons WHERE transaction_id = :id ORDER BY line_no
                """, params, (rs, n) -> new FlightCoupon(
                rs.getString("flight_number"),
                rs.getString("carrier"),
                rs.getString("from_location"),
                rs.getString("to_location"),
                Sql.instant(rs, "departure_at"),
                Sql.instant(rs, "arrival_at")));
        return jdbc.queryForObject(
                "SELECT origin, destination, fare FROM flight_details WHERE transaction_id = :id",
                params, (rs, n) -> new FlightMetadata(
                        rs.getString("origin"), rs.getString("destination"), rs.getBigDecimal("fare"), coupons));
    }

    private HotelMetadata loadHotel(long id) {
        Map<String, Object> params = Map.of("id", id);
        List<HotelExtra> extras = jdbc.query(
                "SELECT name, amount FROM hotel_extras WHERE transaction_id = :id ORDER BY line_no",
                params, (rs, n) -> new HotelExtra(rs.getString("name"), rs.getBigDecimal("amount")));
        return jdbc.queryForObject("""
                SELECT property_name, street, city, postal_code, country, check_in, check_out, nights, room_rate_per_night
                FROM hotel_stays WHERE transaction_id = :id
                """, params, (rs, n) -> new HotelMetadata(
                rs.getString("property_name"),
                new HotelAddress(rs.getString("street"), rs.getString("city"),
                        rs.getString("postal_code"), rs.getString("country")),
                rs.getObject("check_in", LocalDate.class),
                rs.getObject("check_out", LocalDate.class),
                rs.getInt("nights"),
                rs.getBigDecimal("room_rate_per_night"),
                extras));
    }

    private RailMetadata loadRail(long id) {
        List<RailLeg> legs = jdbc.query("""
                SELECT origin, destination, departure_at, arrival_at, train_number, travel_class
                FROM rail_legs WHERE transaction_id = :id ORDER BY line_no
                """, Map.of("id", id), (rs, n) -> new RailLeg(
                rs.getString("origin"),
                rs.getString("destination"),
                Sql.instant(rs, "departure_at"),
                Sql.instant(rs, "arrival_at"),
                rs.getString("train_number"),
                rs.getString("travel_class")));
        return new RailMetadata(legs);
    }

    private NavanFeeMetadata loadNavanFee(long id) {
        return jdbc.queryForObject(
                "SELECT fee_kind, description, related_external_id FROM navan_fees WHERE transaction_id = :id",
                Map.of("id", id), (rs, n) -> new NavanFeeMetadata(
                        FeeKind.fromWire(rs.getString("fee_kind")),
                        rs.getString("description"),
                        rs.getString("related_external_id")));
    }

    private void batch(String sql, List<SqlParameterSource> rows) {
        if (!rows.isEmpty()) {
            jdbc.batchUpdate(sql, rows.toArray(SqlParameterSource[]::new));
        }
    }
}
