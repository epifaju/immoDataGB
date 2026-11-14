ALTER TABLE property
  ADD COLUMN validation_status VARCHAR(30) NOT NULL DEFAULT 'validated';

ALTER TABLE property
  ADD CONSTRAINT property_validation_status_check CHECK (
    validation_status IN ('pending_validation', 'validated')
  );

CREATE INDEX property_validation_status_active_idx
  ON property (validation_status)
  WHERE deleted_at IS NULL;
