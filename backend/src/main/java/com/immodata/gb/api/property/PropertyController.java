package com.immodata.gb.api.property;

import com.immodata.gb.api.ApiResponse;
import com.immodata.gb.api.PageResponse;
import com.immodata.gb.api.error.ApiException;
import com.immodata.gb.application.property.PropertyDetail;
import com.immodata.gb.application.property.PropertyMapItem;
import com.immodata.gb.application.property.PropertyQuery;
import com.immodata.gb.application.property.PropertyQueryService;
import com.immodata.gb.application.property.SourceItem;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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
        return ResponseEntity.ok().body(new ApiResponse<>(true, page, null, Map.of()));
    }

    @GetMapping("/api/v1/properties/{id}")
    public ResponseEntity<ApiResponse<PropertyDetail>> get(@PathVariable("id") String id) {
        UUID uuid = PropertyRequestParser.parseId(id);
        PropertyDetail detail = propertyQueryService
                .getDetail(uuid)
                .orElseThrow(() -> new ApiException("PROPERTY_NOT_FOUND", HttpStatus.NOT_FOUND));
        return ResponseEntity.ok().body(new ApiResponse<>(true, detail, null, Map.of()));
    }

    @GetMapping("/api/v1/properties/{id}/sources")
    public ResponseEntity<ApiResponse<List<SourceItem>>> sources(@PathVariable("id") String id) {
        UUID uuid = PropertyRequestParser.parseId(id);
        List<SourceItem> sources = propertyQueryService
                .listSources(uuid)
                .orElseThrow(() -> new ApiException("PROPERTY_NOT_FOUND", HttpStatus.NOT_FOUND));
        return ResponseEntity.ok().body(new ApiResponse<>(true, sources, null, Map.of()));
    }
}
