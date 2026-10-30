package com.immodata.gb.api.health;

import com.immodata.gb.api.ApiResponse;
import com.immodata.gb.infrastructure.health.PostgisHealthProbe;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private final PostgisHealthProbe probe;

    public HealthController(PostgisHealthProbe probe) {
        this.probe = probe;
    }

    @GetMapping("/api/v1/health")
    public ResponseEntity<ApiResponse<HealthView>> health() {
        HealthView view = probe.probe();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(true, view, null, Map.of()));
    }
}
