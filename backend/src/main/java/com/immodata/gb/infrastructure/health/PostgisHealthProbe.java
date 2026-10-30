package com.immodata.gb.infrastructure.health;

import com.immodata.gb.api.health.HealthView;
import org.springframework.stereotype.Component;

@Component
public class PostgisHealthProbe {
    public HealthView probe() {
        throw new UnsupportedOperationException("JDBC probe is added with Flyway");
    }
}
