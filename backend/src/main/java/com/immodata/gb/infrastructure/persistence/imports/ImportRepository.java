package com.immodata.gb.infrastructure.persistence.imports;

import com.immodata.gb.api.PageResponse;
import com.immodata.gb.api.error.ApiException;
import com.immodata.gb.application.imports.ImportJobStatus;
import com.immodata.gb.application.imports.ImportReport;
import com.immodata.gb.application.imports.ImportRowErrorItem;
import com.immodata.gb.domain.imports.ImportCsvRow;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ImportRepository {

    static final int INITIAL_CONFIDENCE_SCORE = 12;
    static final String INITIAL_CONFIDENCE_LEVEL = "low";
    static final String INITIAL_CONFIDENCE_SUMMARY = "Fonte declarativa única; verificação pendente.";
    static final String INITIAL_DOC_STATUS = "none";
    static final String SOURCE_TYPE_CSV_IMPORT = "csv_import";
    static final int RAW_EXCERPT_MAX = 500;

    private static final RowMapper<ImportJobStatus> STATUS_MAPPER = (rs, rowNum) -> new ImportJobStatus(
            rs.getObject("id", UUID.class),
            rs.getString("status"),
            rs.getString("original_filename"),
            rs.getInt("total_rows"),
            rs.getInt("success_rows"),
            rs.getInt("error_rows"),
            rs.getString("error_message"),
            toInstant(rs.getObject("created_at", OffsetDateTime.class)));

    private static final RowMapper<ImportRowErrorItem> ERROR_MAPPER = (rs, rowNum) -> new ImportRowErrorItem(
            rs.getInt("row_number"), rs.getString("code"), rs.getString("message"), rs.getString("raw_excerpt"));

    private static final String STATUS_COLUMNS =
            "id, status, original_filename, total_rows, success_rows, error_rows, error_message, created_at";

    private final JdbcClient jdbcClient;

    public ImportRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    public UUID insertJob(UUID orgId, String originalFilename, String storedPath) {
        return jdbcClient
                .sql(
                        """
                        INSERT INTO import_job (organisation_id, status, original_filename, stored_path)
                        VALUES (:orgId, 'queued', :originalFilename, :storedPath)
                        RETURNING id
                        """)
                .param("orgId", orgId)
                .param("originalFilename", originalFilename)
                .param("storedPath", storedPath)
                .query(UUID.class)
                .single();
    }

    public void markProcessing(UUID jobId) {
        jdbcClient
                .sql(
                        """
                        UPDATE import_job
                        SET status = 'processing', updated_at = now()
                        WHERE id = :jobId AND deleted_at IS NULL
                        """)
                .param("jobId", jobId)
                .update();
    }

    public void markFinished(UUID jobId, String status, int total, int success, int errors, String errorMessage) {
        jdbcClient
                .sql(
                        """
                        UPDATE import_job
                        SET status = :status,
                            total_rows = :total,
                            success_rows = :success,
                            error_rows = :errors,
                            error_message = :errorMessage,
                            updated_at = now()
                        WHERE id = :jobId AND deleted_at IS NULL
                        """)
                .param("jobId", jobId)
                .param("status", status)
                .param("total", total)
                .param("success", success)
                .param("errors", errors)
                .param("errorMessage", errorMessage)
                .update();
    }

    public void insertRowError(UUID jobId, int rowNumber, String code, String message, String rawExcerpt) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO import_row_error (job_id, row_number, code, message, raw_excerpt)
                        VALUES (:jobId, :rowNumber, :code, :message, :rawExcerpt)
                        """)
                .param("jobId", jobId)
                .param("rowNumber", rowNumber)
                .param("code", code)
                .param("message", message)
                .param("rawExcerpt", truncate(rawExcerpt, RAW_EXCERPT_MAX))
                .update();
    }

    public boolean existsActiveReference(String reference) {
        return jdbcClient
                .sql(
                        """
                        SELECT EXISTS (
                          SELECT 1 FROM property WHERE reference = :reference AND deleted_at IS NULL
                        )
                        """)
                .param("reference", reference)
                .query(Boolean.class)
                .single();
    }

    /** Insère property + geometry + source (csv_import) de façon atomique. Confiance initiale 12/low. */
    @Transactional
    public void insertPropertyBundle(UUID orgId, ImportCsvRow row) {
        UUID propertyId = UUID.randomUUID();
        jdbcClient
                .sql(
                        """
                        INSERT INTO property (
                          id, organisation_id, reference, title, property_type,
                          confidence_score, confidence_level, confidence_summary,
                          doc_status, address_text
                        ) VALUES (
                          :id, :orgId, :reference, :title, :propertyType,
                          :score, :level, :summary,
                          :docStatus, :addressText
                        )
                        """)
                .param("id", propertyId)
                .param("orgId", orgId)
                .param("reference", row.reference())
                .param("title", row.title())
                .param("propertyType", row.propertyType())
                .param("score", INITIAL_CONFIDENCE_SCORE)
                .param("level", INITIAL_CONFIDENCE_LEVEL)
                .param("summary", INITIAL_CONFIDENCE_SUMMARY)
                .param("docStatus", INITIAL_DOC_STATUS)
                .param("addressText", row.addressText())
                .update();

        jdbcClient
                .sql(
                        """
                        INSERT INTO property_geometry (property_id, geom, geom_type, precision_level)
                        VALUES (:propertyId, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326), 'point', 'gps')
                        """)
                .param("propertyId", propertyId)
                .param("lng", row.longitude())
                .param("lat", row.latitude())
                .update();

        jdbcClient
                .sql(
                        """
                        INSERT INTO source (property_id, source_type, label)
                        VALUES (:propertyId, :sourceType, NULL)
                        """)
                .param("propertyId", propertyId)
                .param("sourceType", SOURCE_TYPE_CSV_IMPORT)
                .update();
    }

    public Optional<ImportJobStatus> findStatus(UUID jobId) {
        return jdbcClient
                .sql("SELECT " + STATUS_COLUMNS + " FROM import_job WHERE id = :jobId AND deleted_at IS NULL")
                .param("jobId", jobId)
                .query(STATUS_MAPPER)
                .optional();
    }

    /** @throws ApiException {@code IMPORT_JOB_NOT_FOUND} (404) si le job est inconnu ou supprimé logiquement. */
    public ImportReport findReport(UUID jobId) {
        ImportJobStatus job = findStatus(jobId)
                .orElseThrow(() -> new ApiException("IMPORT_JOB_NOT_FOUND", HttpStatus.NOT_FOUND));
        List<ImportRowErrorItem> errors = jdbcClient
                .sql(
                        """
                        SELECT row_number, code, message, raw_excerpt
                        FROM import_row_error
                        WHERE job_id = :jobId
                        ORDER BY row_number, created_at, id
                        """)
                .param("jobId", jobId)
                .query(ERROR_MAPPER)
                .list();
        return ImportReport.of(job, errors);
    }

    public List<ImportJobStatus> findRecent(int limit) {
        return jdbcClient
                .sql("SELECT " + STATUS_COLUMNS
                        + " FROM import_job WHERE deleted_at IS NULL ORDER BY created_at DESC, id LIMIT :limit")
                .param("limit", limit)
                .query(STATUS_MAPPER)
                .list();
    }

    public PageResponse<ImportJobStatus> findPage(int page, int size) {
        long total = jdbcClient
                .sql("SELECT count(*) FROM import_job WHERE deleted_at IS NULL")
                .query(Long.class)
                .single();
        List<ImportJobStatus> items = jdbcClient
                .sql("SELECT " + STATUS_COLUMNS
                        + " FROM import_job WHERE deleted_at IS NULL"
                        + " ORDER BY created_at DESC, id LIMIT :size OFFSET :offset")
                .param("size", size)
                .param("offset", (long) page * size)
                .query(STATUS_MAPPER)
                .list();
        return PageResponse.of(page, size, total, items);
    }
}
