CREATE TABLE document (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organisation_id UUID NOT NULL REFERENCES organisation (id),
  property_id UUID NOT NULL REFERENCES property (id),
  document_type VARCHAR(40) NOT NULL,
  original_filename VARCHAR(500) NOT NULL,
  content_type VARCHAR(200) NOT NULL,
  size_bytes BIGINT NOT NULL,
  storage_bucket VARCHAR(100) NOT NULL,
  storage_key VARCHAR(1000) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by UUID NULL,
  updated_by UUID NULL,
  deleted_at TIMESTAMPTZ NULL,
  CONSTRAINT document_type_check CHECK (
    document_type IN (
      'titre_foncier',
      'attestation_possession',
      'contrat_vente',
      'contrat_location',
      'plan_parcelle',
      'photo_terrain',
      'piece_identite_masquee',
      'acte_notarie',
      'document_administratif',
      'autre'
    )
  ),
  CONSTRAINT document_size_bytes_check CHECK (size_bytes > 0 AND size_bytes <= 52428800),
  CONSTRAINT document_original_filename_check CHECK (length(btrim(original_filename)) > 0),
  CONSTRAINT document_storage_key_key UNIQUE (storage_key)
);

CREATE INDEX document_property_id_created_at_id_active_idx
  ON document (property_id, created_at DESC, id)
  WHERE deleted_at IS NULL;

CREATE INDEX document_organisation_id_active_idx
  ON document (organisation_id)
  WHERE deleted_at IS NULL;
