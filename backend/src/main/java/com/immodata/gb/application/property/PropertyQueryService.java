package com.immodata.gb.application.property;

import com.immodata.gb.api.PageResponse;
import com.immodata.gb.api.error.ApiException;
import com.immodata.gb.infrastructure.persistence.property.PropertySpatialRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class PropertyQueryService {
    private final PropertySpatialRepository repository;

    public PropertyQueryService(PropertySpatialRepository repository) {
        this.repository = repository;
    }

    public PageResponse<PropertyMapItem> list(PropertyQuery query) {
        return repository.search(query);
    }

    public PropertyMapItem get(UUID id) {
        return repository
                .findById(id)
                .orElseThrow(() -> new ApiException("PROPERTY_NOT_FOUND", HttpStatus.NOT_FOUND));
    }
}
