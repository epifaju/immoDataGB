ALTER TABLE property
  ADD COLUMN doc_status VARCHAR(30) NOT NULL DEFAULT 'none',
  ADD COLUMN address_text TEXT NULL;

ALTER TABLE property
  ADD CONSTRAINT property_doc_status_check CHECK (
    doc_status IN ('none', 'declared', 'partial', 'verified', 'litigious')
  );
