package com.immodata.gb.infrastructure.persistence.property;

import com.immodata.gb.api.PageResponse;
import com.immodata.gb.application.property.PropertyDetail;
import com.immodata.gb.application.property.PropertyMapItem;
import com.immodata.gb.application.property.PropertyQuery;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PropertySpatialRepository {
    private static final String BASE_SELECT =
            """
            SELECT p.id, p.organisation_id, p.reference, p.title, p.property_type,
                   p.confidence_score, p.confidence_level, p.confidence_summary,
                   ST_X(g.geom) AS longitude, ST_Y(g.geom) AS latitude
            """;

    private static final String DETAIL_SELECT =
            BASE_SELECT
                    + """
                   , p.doc_status, p.address_text
            """;

    private static final RowMapper<PropertyMapItem> MAP_ITEM_MAPPER = (rs, rowNum) -> new PropertyMapItem(
            rs.getObject("id", UUID.class),
            rs.getObject("organisation_id", UUID.class),
            rs.getString("reference"),
            rs.getString("title"),
            rs.getString("property_type"),
            rs.getInt("confidence_score"),
            rs.getString("confidence_level"),
            rs.getString("confidence_summary"),
            rs.getDouble("longitude"),
            rs.getDouble("latitude"));

    private static final RowMapper<PropertyDetail> DETAIL_MAPPER = (rs, rowNum) -> new PropertyDetail(
            rs.getObject("id", UUID.class),
            rs.getObject("organisation_id", UUID.class),
            rs.getString("reference"),
            rs.getString("title"),
            rs.getString("property_type"),
            rs.getInt("confidence_score"),
            rs.getString("confidence_level"),
            rs.getString("confidence_summary"),
            rs.getDouble("longitude"),
            rs.getDouble("latitude"),
            rs.getString("doc_status"),
            rs.getString("address_text"));

    private final JdbcClient jdbcClient;

    public PropertySpatialRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public PageResponse<PropertyMapItem> search(PropertyQuery query) {
        StringBuilder where = new StringBuilder(
                """
                FROM property p
                JOIN property_geometry g ON g.property_id = p.id
                WHERE p.deleted_at IS NULL
                  AND p.validation_status = 'validated'
                  AND g.geom && ST_MakeEnvelope(:w, :s, :e, :n, 4326)
                """);
        if (query.type() != null) {
            where.append(" AND p.property_type = :type");
        }
        if (query.confidence() != null) {
            where.append(" AND p.confidence_level = :confidence");
        }

        var countSpec = jdbcClient
                .sql("SELECT count(*) " + where)
                .param("w", query.bbox().west())
                .param("s", query.bbox().south())
                .param("e", query.bbox().east())
                .param("n", query.bbox().north());
        if (query.type() != null) {
            countSpec = countSpec.param("type", query.type().getSqlValue());
        }
        if (query.confidence() != null) {
            countSpec = countSpec.param("confidence", query.confidence().getSqlValue());
        }
        long total = countSpec.query(Long.class).single();

        String select = BASE_SELECT
                + where
                + """
                 ORDER BY p.created_at DESC, p.id
                 LIMIT :size OFFSET :offset
                """;

        var listSpec = jdbcClient
                .sql(select)
                .param("w", query.bbox().west())
                .param("s", query.bbox().south())
                .param("e", query.bbox().east())
                .param("n", query.bbox().north())
                .param("size", query.size())
                .param("offset", (long) query.page() * query.size());
        if (query.type() != null) {
            listSpec = listSpec.param("type", query.type().getSqlValue());
        }
        if (query.confidence() != null) {
            listSpec = listSpec.param("confidence", query.confidence().getSqlValue());
        }
        List<PropertyMapItem> items = new ArrayList<>(listSpec.query(MAP_ITEM_MAPPER).list());
        return PageResponse.of(query.page(), query.size(), total, items);
    }

    public Optional<PropertyDetail> findDetailById(UUID id) {
        return jdbcClient
                .sql(
                        DETAIL_SELECT
                                + """
                        FROM property p
                        JOIN property_geometry g ON g.property_id = p.id
                        WHERE p.deleted_at IS NULL
                          AND p.id = :id
                        """)
                .param("id", id)
                .query(DETAIL_MAPPER)
                .optional();
    }

    public boolean existsActiveById(UUID id) {
        Boolean exists = jdbcClient
                .sql(
                        """
                        SELECT EXISTS (
                          SELECT 1 FROM property p
                          JOIN property_geometry g ON g.property_id = p.id
                          WHERE p.id = :id AND p.deleted_at IS NULL
                        )
                        """)
                .param("id", id)
                .query(Boolean.class)
                .single();
        return Boolean.TRUE.equals(exists);
    }
}
