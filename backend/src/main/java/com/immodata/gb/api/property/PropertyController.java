package com.immodata.gb.api.property;

import com.immodata.gb.api.ApiResponse;
import com.immodata.gb.api.PageResponse;
import com.immodata.gb.application.property.PropertyMapItem;
import com.immodata.gb.application.property.PropertyQuery;
import com.immodata.gb.application.property.PropertyQueryService;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PropertyController {
    private final PropertyQueryService propertyQueryService;

    public PropertyController(PropertyQueryService propertyQueryService) {
        this.propertyQueryService = propertyQueryService;
    }

    @GetMapping("/api/v1/properties")
    public ResponseEntity<ApiResponse<PageResponse<PropertyMapItem>>> list(
            @RequestParam MultiValueMap<String, String> params) {
        PropertyQuery query = PropertyRequestParser.parseList(params);
        PageResponse<PropertyMapItem> page = propertyQueryService.list(query);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(true, page, null, Map.of()));
    }

    @GetMapping("/api/v1/properties/{id}")
    public ResponseEntity<ApiResponse<PropertyMapItem>> get(@PathVariable("id") String id) {
        UUID uuid = PropertyRequestParser.parseId(id);
        PropertyMapItem item = propertyQueryService.get(uuid);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(true, item, null, Map.of()));
    }
}
