CREATE TABLE property_geometry (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  property_id UUID NOT NULL UNIQUE REFERENCES property (id),
  geom geometry(Point, 4326) NOT NULL,
  geom_type VARCHAR(20) NOT NULL,
  precision_level VARCHAR(20) NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT property_geometry_geom_type_check CHECK (geom_type = 'point'),
  CONSTRAINT property_geometry_precision_level_check CHECK (
    precision_level IS NULL OR precision_level IN ('gps', 'address', 'zone')
  )
);

CREATE INDEX property_geometry_geom_gist_idx
  ON property_geometry
  USING GIST (geom);
