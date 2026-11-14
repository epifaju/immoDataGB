CREATE TABLE import_job (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organisation_id UUID NOT NULL REFERENCES organisation (id),
  status VARCHAR(20) NOT NULL,
  original_filename VARCHAR(255) NOT NULL,
  stored_path TEXT NOT NULL,
  total_rows INT NOT NULL DEFAULT 0,
  success_rows INT NOT NULL DEFAULT 0,
  error_rows INT NOT NULL DEFAULT 0,
  error_message TEXT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by UUID NULL,
  updated_by UUID NULL,
  deleted_at TIMESTAMPTZ NULL,
  CONSTRAINT import_job_status_check CHECK (
    status IN ('queued', 'processing', 'completed', 'failed')
  )
);

CREATE INDEX import_job_active_created_idx
  ON import_job (created_at DESC, id)
  WHERE deleted_at IS NULL;

CREATE TABLE import_row_error (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  job_id UUID NOT NULL REFERENCES import_job (id) ON DELETE CASCADE,
  row_number INT NOT NULL,
  code VARCHAR(80) NOT NULL,
  message TEXT NOT NULL,
  raw_excerpt VARCHAR(500) NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX import_row_error_job_row_idx
  ON import_row_error (job_id, row_number);

ALTER TABLE source DROP CONSTRAINT source_source_type_check;

ALTER TABLE source ADD CONSTRAINT source_source_type_check CHECK (
  source_type IN (
    'agency_listing',
    'field_observation',
    'scanned_doc',
    'institutional',
    'third_party_user',
    'partner',
    'open_layer',
    'csv_import'
  )
);
