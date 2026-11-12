package com.immodata.gb.infrastructure.persistence.property;

import com.immodata.gb.application.property.SourceItem;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SourceRepository {
    private static final RowMapper<SourceItem> ROW_MAPPER = (rs, rowNum) -> new SourceItem(
            rs.getObject("id", UUID.class),
            rs.getString("source_type"),
            rs.getString("label"),
            rs.getObject("observed_at", LocalDate.class),
            rs.getBoolean("is_verified"),
            rs.getInt("weight"));

    private final JdbcClient jdbcClient;

    public SourceRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<SourceItem> listByPropertyId(UUID propertyId) {
        return jdbcClient
                .sql(
                        """
                        SELECT s.id, s.source_type, s.label, s.observed_at, s.is_verified, s.weight
                        FROM source s
                        WHERE s.property_id = :id
                        ORDER BY s.created_at ASC, s.id
                        """)
                .param("id", propertyId)
                .query(ROW_MAPPER)
                .list();
    }
}
