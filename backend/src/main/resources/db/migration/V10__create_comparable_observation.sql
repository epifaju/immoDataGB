CREATE TABLE comparable_observation (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organisation_id UUID NOT NULL REFERENCES organisation (id),
  property_type VARCHAR(50) NOT NULL,
  price_xof BIGINT NOT NULL,
  area_sqm NUMERIC(12,2) NOT NULL,
  observed_at DATE NOT NULL,
  geom geometry(Point, 4326) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at TIMESTAMPTZ NULL,
  CONSTRAINT comparable_observation_property_type_check CHECK (
    property_type IN ('house', 'apartment', 'land', 'commercial')
  ),
  CONSTRAINT comparable_observation_price_xof_check CHECK (price_xof > 0),
  CONSTRAINT comparable_observation_area_sqm_check CHECK (area_sqm > 0)
);

CREATE INDEX comparable_observation_geom_gist_idx
  ON comparable_observation
  USING GIST (geom);

CREATE INDEX comparable_observation_property_type_observed_at_active_idx
  ON comparable_observation (property_type, observed_at)
  WHERE deleted_at IS NULL;
