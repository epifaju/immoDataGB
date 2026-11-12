ALTER TABLE source
  ADD COLUMN weight SMALLINT NOT NULL DEFAULT 1;

ALTER TABLE source
  ADD CONSTRAINT source_weight_check CHECK (weight BETWEEN 1 AND 100);

CREATE TABLE confidence_rule (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organisation_id UUID NOT NULL REFERENCES organisation (id),
  rule_name VARCHAR(100) NOT NULL,
  rule_type VARCHAR(50) NOT NULL,
  condition_json JSONB NOT NULL,
  score_delta SMALLINT NOT NULL,
  is_active BOOLEAN NOT NULL DEFAULT true,
  priority SMALLINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT confidence_rule_organisation_id_rule_name_key UNIQUE (organisation_id, rule_name),
  CONSTRAINT confidence_rule_rule_type_check CHECK (
    rule_type IN (
      'source_count',
      'source_type_bonus',
      'verified_source_bonus',
      'doc_status_bonus',
      'age_penalty',
      'inconsistency_penalty'
    )
  ),
  CONSTRAINT confidence_rule_condition_check CHECK (
    jsonb_typeof(condition_json) = 'object' AND (
      (rule_type = 'source_count'          AND jsonb_exists(condition_json, 'minUnits') AND jsonb_exists(condition_json, 'maxUnits')) OR
      (rule_type = 'source_type_bonus'     AND jsonb_exists(condition_json, 'sourceType')) OR
      (rule_type = 'verified_source_bonus' AND jsonb_exists(condition_json, 'minVerified')) OR
      (rule_type = 'doc_status_bonus'      AND jsonb_exists(condition_json, 'docStatus')) OR
      (rule_type = 'age_penalty'           AND jsonb_exists(condition_json, 'olderThanMonths')) OR
      (rule_type = 'inconsistency_penalty')
    )
  ),
  CONSTRAINT confidence_rule_score_delta_check CHECK (score_delta BETWEEN -100 AND 100),
  CONSTRAINT confidence_rule_reserved_check CHECK (
    rule_type <> 'inconsistency_penalty' OR is_active = false
  )
);

CREATE INDEX confidence_rule_organisation_active_priority_id_idx
  ON confidence_rule (organisation_id, is_active, priority, id);
