package com.immodata.gb.infrastructure.batch.imports;

import com.immodata.gb.application.imports.ImportCsvValidator;
import com.immodata.gb.application.imports.ImportRowException;
import com.immodata.gb.domain.imports.ImportCsvRow;
import com.immodata.gb.infrastructure.persistence.imports.ImportRepository;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.batch.item.ItemProcessor;

/**
 * Valide la ligne et écarte les doublons de {@code reference} (déjà actifs en base ou répétés dans le
 * fichier). Une ligne rejetée est enregistrée dans {@code import_row_error} et filtrée (retour {@code null}).
 */
public class ImportItemProcessor implements ItemProcessor<ImportRawRow, ImportCsvRow> {

    static final String DUPLICATE_CODE = "IMPORT_DUPLICATE_REFERENCE";

    private final UUID jobId;
    private final ImportCsvValidator validator;
    private final ImportRepository repository;
    private final ImportRunStats stats;
    private final Set<String> seenReferences = new HashSet<>();

    public ImportItemProcessor(
            UUID jobId, ImportCsvValidator validator, ImportRepository repository, ImportRunStats stats) {
        this.jobId = jobId;
        this.validator = validator;
        this.repository = repository;
        this.stats = stats;
    }

    @Override
    public ImportCsvRow process(ImportRawRow raw) {
        try {
            ImportCsvRow row = validator.parseAndValidate(raw.rowNumber(), raw.cols());
            if (repository.existsActiveReference(row.reference()) || !seenReferences.add(row.reference())) {
                throw new ImportRowException(DUPLICATE_CODE, "Referência já existente: " + row.reference());
            }
            return row;
        } catch (ImportRowException e) {
            repository.insertRowError(jobId, raw.rowNumber(), e.getCode(), e.getMessage(), raw.rawLine());
            stats.incrementError();
            return null;
        }
    }
}
