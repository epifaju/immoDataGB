package com.immodata.gb.domain.confidence;

import java.util.Objects;

public record DocStatusBonusCondition(String docStatus) implements ConfidenceCondition {

    public DocStatusBonusCondition {
        Objects.requireNonNull(docStatus, "docStatus");
    }
}