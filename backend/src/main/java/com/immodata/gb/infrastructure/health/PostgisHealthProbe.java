package com.immodata.gb.infrastructure.health;

import com.immodata.gb.api.health.HealthView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PostgisHealthProbe {
    private final JdbcTemplate jdbcTemplate;

    public PostgisHealthProbe(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public HealthView probe() {
        String version = jdbcTemplate.queryForObject("SELECT postgis_version()", String.class);
        return new HealthView("up", "up", version);
    }
}
