package com.immodata.gb.application.property;

import com.immodata.gb.domain.property.ConfidenceLevel;
import com.immodata.gb.domain.property.PropertyType;

public record PropertyQuery(
        BoundingBox bbox,
        PropertyType type,
        ConfidenceLevel confidence,
        int page,
        int size) {}
