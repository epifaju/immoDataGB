package com.immodata.gb.domain.confidence;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record ConfidenceInput(String docStatus, List<SourceFact> sources, LocalDate today) {

    public ConfidenceInput {
        Objects.requireNonNull(docStatus, "docStatus");
        Objects.requireNonNull(today, "today");
        sources = List.copyOf(sources);
    }

    /** Unités de fonte = somme des poids des sources. */
    public int units() {
        return sources.stream().mapToInt(SourceFact::weight).sum();
    }

    public int verifiedCount() {
        return (int) sources.stream().filter(SourceFact::verified).count();
    }
}