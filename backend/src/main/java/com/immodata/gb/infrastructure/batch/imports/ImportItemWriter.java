package com.immodata.gb.infrastructure.batch.imports;

import com.immodata.gb.domain.imports.ImportCsvRow;
import com.immodata.gb.infrastructure.persistence.imports.ImportRepository;
import java.util.UUID;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.dao.DuplicateKeyException;

/**
 * Insère property + geometry + source par ligne (atomique côté repository). Une violation d'unicité sur
 * {@code reference} (course entre deux imports) devient une erreur de ligne, pas un échec du job.
 */
public class ImportItemWriter implements ItemWriter<ImportCsvRow> {

    private final UUID jobId;
    private final UUID organisationId;
    private final ImportRepository repository;
    private final ImportRunStats stats;

    public ImportItemWriter(UUID jobId, UUID organisationId, ImportRepository repository, ImportRunStats stats) {
        this.jobId = jobId;
        this.organisationId = organisationId;
        this.repository = repository;
        this.stats = stats;
    }

    @Override
    public void write(Chunk<? extends ImportCsvRow> chunk) {
        for (ImportCsvRow row : chunk) {
            try {
                repository.insertPropertyBundle(organisationId, row);
                stats.incrementSuccess();
            } catch (DuplicateKeyException e) {
                repository.insertRowError(
                        jobId,
                        row.rowNumber(),
                        ImportItemProcessor.DUPLICATE_CODE,
                        "Referência já existente: " + row.reference(),
                        row.reference() + "," + row.title());
                stats.incrementError();
            }
        }
    }
}
