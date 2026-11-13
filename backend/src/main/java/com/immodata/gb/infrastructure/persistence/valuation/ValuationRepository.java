package com.immodata.gb.infrastructure.persistence.valuation;

import com.immodata.gb.domain.valuation.ComparableFact;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ValuationRepository {

    private static final RowMapper<ComparableFact> MAPPER = (rs, rowNum) -> new ComparableFact(
            rs.getObject("id", UUID.class),
            rs.getString("property_type"),
            rs.getLong("price_xof"),
            rs.getBigDecimal("area_sqm"),
            rs.getObject("observed_at", LocalDate.class),
            rs.getDouble("distance_m"));

    private final JdbcClient jdbcClient;

    public ValuationRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<ComparableFact> findCandidates(
            String propertyType, double lat, double lng, int radiusMeters, LocalDate since) {
        return jdbcClient
                .sql(
                        """
                        SELECT c.id, c.property_type, c.price_xof, c.area_sqm, c.observed_at,
                               ST_Distance(c.geom::geography, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) AS distance_m
                        FROM comparable_observation c
                        WHERE c.deleted_at IS NULL
                          AND c.property_type = :propertyType
                          AND c.observed_at >= :since
                          AND ST_DWithin(
                                c.geom::geography,
                                ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
                                :radiusMeters
                              )
                        ORDER BY distance_m, c.id
                        """)
                .param("propertyType", propertyType)
                .param("lat", lat)
                .param("lng", lng)
                .param("radiusMeters", radiusMeters)
                .param("since", since)
                .query(MAPPER)
                .list();
    }
}