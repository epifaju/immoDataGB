CREATE TABLE source (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  property_id UUID NOT NULL REFERENCES property (id),
  source_type VARCHAR(30) NOT NULL,
  label VARCHAR(200) NULL,
  observed_at DATE NULL,
  is_verified BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT source_source_type_check CHECK (
    source_type IN (
      'agency_listing',
      'field_observation',
      'scanned_doc',
      'institutional',
      'third_party_user',
      'partner',
      'open_layer'
    )
  ),
  CONSTRAINT source_label_check CHECK (label IS NULL OR btrim(label) <> '')
);

CREATE INDEX source_property_created_id_idx
  ON source (property_id, created_at, id);
