package com.immodata.gb.infrastructure.batch.imports;

import java.util.concurrent.atomic.AtomicInteger;

/** Compteurs d'un lancement d'import, partagés entre processor, writer et listener. */
public class ImportRunStats {

    private final AtomicInteger successRows = new AtomicInteger();
    private final AtomicInteger errorRows = new AtomicInteger();

    public void incrementSuccess() {
        successRows.incrementAndGet();
    }

    public void incrementError() {
        errorRows.incrementAndGet();
    }

    public int successRows() {
        return successRows.get();
    }

    public int errorRows() {
        return errorRows.get();
    }
}
