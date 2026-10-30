CREATE TABLE property (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organisation_id UUID NOT NULL REFERENCES organisation (id),
  reference VARCHAR(50) NOT NULL,
  title VARCHAR(500) NOT NULL,
  property_type VARCHAR(50) NOT NULL,
  confidence_score SMALLINT NOT NULL,
  confidence_level VARCHAR(10) NOT NULL,
  confidence_summary TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by UUID NULL,
  updated_by UUID NULL,
  deleted_at TIMESTAMPTZ NULL,
  CONSTRAINT property_type_check CHECK (
    property_type IN ('house', 'apartment', 'land', 'commercial')
  ),
  CONSTRAINT property_confidence_check CHECK (
    (confidence_level = 'low' AND confidence_score BETWEEN 0 AND 33)
    OR (confidence_level = 'medium' AND confidence_score BETWEEN 34 AND 66)
    OR (confidence_level = 'high' AND confidence_score BETWEEN 67 AND 100)
  )
);

CREATE UNIQUE INDEX property_reference_active_uidx
  ON property (reference)
  WHERE deleted_at IS NULL;

CREATE INDEX property_organisation_id_active_idx
  ON property (organisation_id)
  WHERE deleted_at IS NULL;

CREATE INDEX property_property_type_active_idx
  ON property (property_type)
  WHERE deleted_at IS NULL;

CREATE INDEX property_confidence_level_active_idx
  ON property (confidence_level)
  WHERE deleted_at IS NULL;

CREATE INDEX property_created_at_id_active_idx
  ON property (created_at DESC, id)
  WHERE deleted_at IS NULL;
