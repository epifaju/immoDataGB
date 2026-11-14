CREATE TABLE audit_event (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  entity_type VARCHAR(50) NOT NULL,
  entity_id UUID NOT NULL,
  action VARCHAR(50) NOT NULL,
  performed_by UUID NULL,
  ip_address INET NULL,
  old_value JSONB NULL,
  new_value JSONB NULL,
  summary TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT audit_event_action_check CHECK (
    action IN ('validate', 'reject')
  )
);

CREATE INDEX audit_event_entity_idx
  ON audit_event (entity_type, entity_id);

CREATE INDEX audit_event_created_idx
  ON audit_event (created_at DESC);
