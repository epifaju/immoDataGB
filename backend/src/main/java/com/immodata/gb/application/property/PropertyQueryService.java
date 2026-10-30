package com.immodata.gb.application.property;

import com.immodata.gb.api.PageResponse;
import com.immodata.gb.infrastructure.persistence.property.PropertySpatialRepository;
import com.immodata.gb.infrastructure.persistence.property.SourceRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PropertyQueryService {
    private final PropertySpatialRepository propertySpatialRepository;
    private final SourceRepository sourceRepository;

    public PropertyQueryService(
            PropertySpatialRepository propertySpatialRepository, SourceRepository sourceRepository) {
        this.propertySpatialRepository = propertySpatialRepository;
        this.sourceRepository = sourceRepository;
    }

    public PageResponse<PropertyMapItem> list(PropertyQuery query) {
        return propertySpatialRepository.search(query);
    }

    public Optional<PropertyDetail> getDetail(UUID id) {
        return propertySpatialRepository.findDetailById(id);
    }

    public Optional<List<SourceItem>> listSources(UUID id) {
        if (!propertySpatialRepository.existsActiveById(id)) {
            return Optional.empty();
        }
        return Optional.of(sourceRepository.listByPropertyId(id));
    }
}
