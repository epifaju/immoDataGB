package com.immodata.gb.domain.confidence;

/** Match si unités (somme des poids) dans [minUnits, maxUnits] ; maxUnits null = sans plafond. */
public record SourceCountCondition(int minUnits, Integer maxUnits) implements ConfidenceCondition {

    public SourceCountCondition {
        if (minUnits < 0) {
            throw new IllegalArgumentException("minUnits must be >= 0");
        }
        if (maxUnits != null && maxUnits < minUnits) {
            throw new IllegalArgumentException("maxUnits must be null or >= minUnits");
        }
    }
}