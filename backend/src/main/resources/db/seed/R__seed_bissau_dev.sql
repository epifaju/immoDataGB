-- Seed Bissau (profil dev) — deterministic, Bloc 1 + Bloc 2
-- Org: 00000000-0000-0000-0000-00000000000a
-- Property UUIDs: 00000000-0000-0000-0001-0000000000NN (NN=01..24)
-- Geometry UUIDs: 00000000-0000-0000-0002-0000000000NN
-- Source UUIDs:   00000000-0000-0000-0003-00000000NNMM (NN=bien, MM=rang)
-- Source weight: institutional 3, field_observation 2, agency_listing 1
-- Confidence rule UUIDs: 00000000-0000-0000-0004-0000000000NN (NN=01..09)
-- Bairros (index (n-1)%6): Ajuda, Antula, Bairro Militar, Mindará, Plack II, Santa Luzia
-- Adresses illustratives, sans lien avec les coordonnées des points.
--
-- Bloc 5 — comparable_observation UUIDs: 00000000-0000-0000-0005-0000000000NN (NN=01..36)
--   01..09 house | 10..18 apartment | 19..27 land | 28..36 commercial (9 par type)
--   observed_at: 2024-11 .. 2026-10 (référence seed 2026-11-01). Apartments: 2025-02-11 .. 2026-10-05
--   (donc dans la fenêtre 24 mois jusqu'à ~2027-02).
--   Outliers pps (XOF/m²) volontaires: apt 17 (1 600 000) et apt 18 (90 000); house 09 (950 000);
--   land 27 (180 000); commercial 36 (2 500 000).
-- Cas de référence IT (centre 11.863, -15.598; type apartment; periodMonths=24; IQR R-7, n>=4 => exclusion):
--   A) radius 5000 m  : 9 candidats, 2 outliers exclus (17, 18) => comparablesCount=7, median pps 420 000
--      (80 m² => 33 600 000 XOF), warning null.
--   B) radius 3000 m  : 7 candidats (10-14, 17, 18), outliers exclus => comparablesCount=5, median pps 410 000,
--      warning null (seuil < 5 non atteint).
--   C) radius 500 m   : 3 candidats (10, 11, 12; pps 410k/420k/435k), pas d'exclusion (n<4) =>
--      comparablesCount=3, median pps 420 000, warning NON null (< 5 comparables).
--   D) radius 100 m   : 0 candidat => VALUATION_NO_COMPARABLES.

INSERT INTO organisation (id, name) VALUES
  ('00000000-0000-0000-0000-00000000000a', 'ImmoData Bissau Pilote')
ON CONFLICT (id) DO NOTHING;

INSERT INTO property (
  id, organisation_id, reference, title, property_type,
  confidence_score, confidence_level, confidence_summary,
  doc_status, address_text, created_at, updated_at
) VALUES
  ('00000000-0000-0000-0001-000000000001', '00000000-0000-0000-0000-00000000000a', 'BIS-001', 'Moradia em Bissau (BIS-001)', 'house', 12, 'low', 'Fonte declarativa única; verificação pendente.', 'none', 'Ajuda, Bissau', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '1 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '1 minutes'),
  ('00000000-0000-0000-0001-000000000002', '00000000-0000-0000-0000-00000000000a', 'BIS-002', 'Apartamento em Bissau (BIS-002)', 'apartment', 12, 'low', 'Fonte declarativa única; verificação pendente.', 'declared', 'Antula, Bissau', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '2 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '2 minutes'),
  ('00000000-0000-0000-0001-000000000003', '00000000-0000-0000-0000-00000000000a', 'BIS-003', 'Terreno em Bissau (BIS-003)', 'land', 12, 'low', 'Fonte declarativa única; verificação pendente.', 'partial', NULL, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '3 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '3 minutes'),
  ('00000000-0000-0000-0001-000000000004', '00000000-0000-0000-0000-00000000000a', 'BIS-004', 'Espaço comercial em Bissau (BIS-004)', 'commercial', 12, 'low', 'Fonte declarativa única; verificação pendente.', 'verified', 'Mindará, Bissau', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '4 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '4 minutes'),
  ('00000000-0000-0000-0001-000000000005', '00000000-0000-0000-0000-00000000000a', 'BIS-005', 'Moradia em Bissau (BIS-005)', 'house', 41, 'medium', 'Dados parcialmente cruzados com observação de campo.', 'litigious', 'Plack II, Bissau', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '5 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '5 minutes'),
  ('00000000-0000-0000-0001-000000000006', '00000000-0000-0000-0000-00000000000a', 'BIS-006', 'Apartamento em Bissau (BIS-006)', 'apartment', 41, 'medium', 'Dados parcialmente cruzados com observação de campo.', 'none', NULL, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '6 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '6 minutes'),
  ('00000000-0000-0000-0001-000000000007', '00000000-0000-0000-0000-00000000000a', 'BIS-007', 'Terreno em Bissau (BIS-007)', 'land', 41, 'medium', 'Dados parcialmente cruzados com observação de campo.', 'declared', 'Ajuda, Bissau', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '7 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '7 minutes'),
  ('00000000-0000-0000-0001-000000000008', '00000000-0000-0000-0000-00000000000a', 'BIS-008', 'Espaço comercial em Bissau (BIS-008)', 'commercial', 41, 'medium', 'Dados parcialmente cruzados com observação de campo.', 'partial', 'Antula, Bissau', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '8 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '8 minutes'),
  ('00000000-0000-0000-0001-000000000009', '00000000-0000-0000-0000-00000000000a', 'BIS-009', 'Moradia em Bissau (BIS-009)', 'house', 72, 'high', 'Documentação e observação de campo confirmadas.', 'verified', NULL, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '9 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '9 minutes'),
  ('00000000-0000-0000-0001-000000000010', '00000000-0000-0000-0000-00000000000a', 'BIS-010', 'Apartamento em Bissau (BIS-010)', 'apartment', 72, 'high', 'Documentação e observação de campo confirmadas.', 'litigious', 'Mindará, Bissau', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '10 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '10 minutes'),
  ('00000000-0000-0000-0001-000000000011', '00000000-0000-0000-0000-00000000000a', 'BIS-011', 'Terreno em Bissau (BIS-011)', 'land', 72, 'high', 'Documentação e observação de campo confirmadas.', 'none', 'Plack II, Bissau', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '11 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '11 minutes'),
  ('00000000-0000-0000-0001-000000000012', '00000000-0000-0000-0000-00000000000a', 'BIS-012', 'Espaço comercial em Bissau (BIS-012)', 'commercial', 72, 'high', 'Documentação e observação de campo confirmadas.', 'declared', NULL, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '12 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '12 minutes'),
  ('00000000-0000-0000-0001-000000000013', '00000000-0000-0000-0000-00000000000a', 'BIS-013', 'Moradia em Bissau (BIS-013)', 'house', 28, 'low', 'Fonte declarativa única; verificação pendente.', 'partial', 'Ajuda, Bissau', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '13 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '13 minutes'),
  ('00000000-0000-0000-0001-000000000014', '00000000-0000-0000-0000-00000000000a', 'BIS-014', 'Apartamento em Bissau (BIS-014)', 'apartment', 28, 'low', 'Fonte declarativa única; verificação pendente.', 'verified', 'Antula, Bissau', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '14 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '14 minutes'),
  ('00000000-0000-0000-0001-000000000015', '00000000-0000-0000-0000-00000000000a', 'BIS-015', 'Terreno em Bissau (BIS-015)', 'land', 28, 'low', 'Fonte declarativa única; verificação pendente.', 'litigious', NULL, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '15 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '15 minutes'),
  ('00000000-0000-0000-0001-000000000016', '00000000-0000-0000-0000-00000000000a', 'BIS-016', 'Espaço comercial em Bissau (BIS-016)', 'commercial', 28, 'low', 'Fonte declarativa única; verificação pendente.', 'none', 'Mindará, Bissau', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '16 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '16 minutes'),
  ('00000000-0000-0000-0001-000000000017', '00000000-0000-0000-0000-00000000000a', 'BIS-017', 'Moradia em Bissau (BIS-017)', 'house', 58, 'medium', 'Dados parcialmente cruzados com observação de campo.', 'declared', 'Plack II, Bissau', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '17 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '17 minutes'),
  ('00000000-0000-0000-0001-000000000018', '00000000-0000-0000-0000-00000000000a', 'BIS-018', 'Apartamento em Bissau (BIS-018)', 'apartment', 58, 'medium', 'Dados parcialmente cruzados com observação de campo.', 'partial', NULL, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '18 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '18 minutes'),
  ('00000000-0000-0000-0001-000000000019', '00000000-0000-0000-0000-00000000000a', 'BIS-019', 'Terreno em Bissau (BIS-019)', 'land', 58, 'medium', 'Dados parcialmente cruzados com observação de campo.', 'verified', 'Ajuda, Bissau', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '19 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '19 minutes'),
  ('00000000-0000-0000-0001-000000000020', '00000000-0000-0000-0000-00000000000a', 'BIS-020', 'Espaço comercial em Bissau (BIS-020)', 'commercial', 58, 'medium', 'Dados parcialmente cruzados com observação de campo.', 'litigious', 'Antula, Bissau', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '20 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '20 minutes'),
  ('00000000-0000-0000-0001-000000000021', '00000000-0000-0000-0000-00000000000a', 'BIS-021', 'Moradia em Bissau (BIS-021)', 'house', 91, 'high', 'Documentação e observação de campo confirmadas.', 'none', NULL, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '21 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '21 minutes'),
  ('00000000-0000-0000-0001-000000000022', '00000000-0000-0000-0000-00000000000a', 'BIS-022', 'Apartamento em Bissau (BIS-022)', 'apartment', 91, 'high', 'Documentação e observação de campo confirmadas.', 'declared', 'Mindará, Bissau', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '22 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '22 minutes'),
  ('00000000-0000-0000-0001-000000000023', '00000000-0000-0000-0000-00000000000a', 'BIS-023', 'Terreno em Bissau (BIS-023)', 'land', 91, 'high', 'Documentação e observação de campo confirmadas.', 'partial', 'Plack II, Bissau', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '23 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '23 minutes'),
  ('00000000-0000-0000-0001-000000000024', '00000000-0000-0000-0000-00000000000a', 'BIS-024', 'Espaço comercial em Bissau (BIS-024)', 'commercial', 91, 'high', 'Documentação e observação de campo confirmadas.', 'verified', NULL, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '24 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '24 minutes')
ON CONFLICT (id) DO UPDATE SET
  doc_status = EXCLUDED.doc_status,
  address_text = EXCLUDED.address_text,
  title = EXCLUDED.title,
  property_type = EXCLUDED.property_type,
  confidence_score = EXCLUDED.confidence_score,
  confidence_level = EXCLUDED.confidence_level,
  confidence_summary = EXCLUDED.confidence_summary,
  updated_at = EXCLUDED.updated_at;

INSERT INTO property_geometry (id, property_id, geom, geom_type, precision_level) VALUES
  ('00000000-0000-0000-0002-000000000001', '00000000-0000-0000-0001-000000000001', ST_SetSRID(ST_MakePoint(-15.68, 11.820), 4326), 'point', 'gps'),
  ('00000000-0000-0000-0002-000000000002', '00000000-0000-0000-0001-000000000002', ST_SetSRID(ST_MakePoint(-15.648, 11.82), 4326), 'point', 'address'),
  ('00000000-0000-0000-0002-000000000003', '00000000-0000-0000-0001-000000000003', ST_SetSRID(ST_MakePoint(-15.616, 11.82), 4326), 'point', 'gps'),
  ('00000000-0000-0000-0002-000000000004', '00000000-0000-0000-0001-000000000004', ST_SetSRID(ST_MakePoint(-15.584, 11.82), 4326), 'point', 'address'),
  ('00000000-0000-0000-0002-000000000005', '00000000-0000-0000-0001-000000000005', ST_SetSRID(ST_MakePoint(-15.552, 11.82), 4326), 'point', 'gps'),
  ('00000000-0000-0000-0002-000000000006', '00000000-0000-0000-0001-000000000006', ST_SetSRID(ST_MakePoint(-15.52, 11.82), 4326), 'point', 'address'),
  ('00000000-0000-0000-0002-000000000007', '00000000-0000-0000-0001-000000000007', ST_SetSRID(ST_MakePoint(-15.68, 11.856), 4326), 'point', 'gps'),
  ('00000000-0000-0000-0002-000000000008', '00000000-0000-0000-0001-000000000008', ST_SetSRID(ST_MakePoint(-15.648, 11.856), 4326), 'point', 'address'),
  ('00000000-0000-0000-0002-000000000009', '00000000-0000-0000-0001-000000000009', ST_SetSRID(ST_MakePoint(-15.616, 11.856), 4326), 'point', 'gps'),
  ('00000000-0000-0000-0002-000000000010', '00000000-0000-0000-0001-000000000010', ST_SetSRID(ST_MakePoint(-15.584, 11.856), 4326), 'point', 'address'),
  ('00000000-0000-0000-0002-000000000011', '00000000-0000-0000-0001-000000000011', ST_SetSRID(ST_MakePoint(-15.552, 11.856), 4326), 'point', 'gps'),
  ('00000000-0000-0000-0002-000000000012', '00000000-0000-0000-0001-000000000012', ST_SetSRID(ST_MakePoint(-15.52, 11.856), 4326), 'point', 'address'),
  ('00000000-0000-0000-0002-000000000013', '00000000-0000-0000-0001-000000000013', ST_SetSRID(ST_MakePoint(-15.68, 11.892), 4326), 'point', 'gps'),
  ('00000000-0000-0000-0002-000000000014', '00000000-0000-0000-0001-000000000014', ST_SetSRID(ST_MakePoint(-15.648, 11.892), 4326), 'point', 'address'),
  ('00000000-0000-0000-0002-000000000015', '00000000-0000-0000-0001-000000000015', ST_SetSRID(ST_MakePoint(-15.616, 11.892), 4326), 'point', 'gps'),
  ('00000000-0000-0000-0002-000000000016', '00000000-0000-0000-0001-000000000016', ST_SetSRID(ST_MakePoint(-15.584, 11.892), 4326), 'point', 'address'),
  ('00000000-0000-0000-0002-000000000017', '00000000-0000-0000-0001-000000000017', ST_SetSRID(ST_MakePoint(-15.552, 11.892), 4326), 'point', 'gps'),
  ('00000000-0000-0000-0002-000000000018', '00000000-0000-0000-0001-000000000018', ST_SetSRID(ST_MakePoint(-15.52, 11.892), 4326), 'point', 'address'),
  ('00000000-0000-0000-0002-000000000019', '00000000-0000-0000-0001-000000000019', ST_SetSRID(ST_MakePoint(-15.68, 11.928), 4326), 'point', 'gps'),
  ('00000000-0000-0000-0002-000000000020', '00000000-0000-0000-0001-000000000020', ST_SetSRID(ST_MakePoint(-15.648, 11.928), 4326), 'point', 'address'),
  ('00000000-0000-0000-0002-000000000021', '00000000-0000-0000-0001-000000000021', ST_SetSRID(ST_MakePoint(-15.616, 11.928), 4326), 'point', 'gps'),
  ('00000000-0000-0000-0002-000000000022', '00000000-0000-0000-0001-000000000022', ST_SetSRID(ST_MakePoint(-15.584, 11.928), 4326), 'point', 'address'),
  ('00000000-0000-0000-0002-000000000023', '00000000-0000-0000-0001-000000000023', ST_SetSRID(ST_MakePoint(-15.552, 11.928), 4326), 'point', 'gps'),
  ('00000000-0000-0000-0002-000000000024', '00000000-0000-0000-0001-000000000024', ST_SetSRID(ST_MakePoint(-15.52, 11.928), 4326), 'point', 'address')
ON CONFLICT (id) DO NOTHING;

DELETE FROM source
WHERE id BETWEEN '00000000-0000-0000-0003-000000000000' AND '00000000-0000-0000-0003-ffffffffffff';

INSERT INTO source (
  id, property_id, source_type, label, observed_at, is_verified, created_at, weight
) VALUES
  ('00000000-0000-0000-0003-000000000101', '00000000-0000-0000-0001-000000000001', 'institutional', 'Registo institucional 1.1', DATE '2026-08-01' + INTERVAL '2 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '1 minutes' + INTERVAL '1 minutes', 3),
  ('00000000-0000-0000-0003-000000000102', '00000000-0000-0000-0001-000000000001', 'field_observation', 'Observação de campo 1.2', DATE '2026-08-01' + INTERVAL '3 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '1 minutes' + INTERVAL '2 minutes', 2),
  ('00000000-0000-0000-0003-000000000201', '00000000-0000-0000-0001-000000000002', 'field_observation', 'Observação de campo 2.1', DATE '2026-08-01' + INTERVAL '3 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '2 minutes' + INTERVAL '1 minutes', 2),
  ('00000000-0000-0000-0003-000000000202', '00000000-0000-0000-0001-000000000002', 'agency_listing', 'Agência Exemplo 2.2', DATE '2026-08-01' + INTERVAL '4 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '2 minutes' + INTERVAL '2 minutes', 1),
  ('00000000-0000-0000-0003-000000000203', '00000000-0000-0000-0001-000000000002', 'institutional', NULL, NULL, false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '2 minutes' + INTERVAL '3 minutes', 3),
  ('00000000-0000-0000-0003-000000000301', '00000000-0000-0000-0001-000000000003', 'agency_listing', 'Agência Exemplo 3.1', DATE '2026-08-01' + INTERVAL '4 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '3 minutes' + INTERVAL '1 minutes', 1),
  ('00000000-0000-0000-0003-000000000401', '00000000-0000-0000-0001-000000000004', 'institutional', 'Registo institucional 4.1', DATE '2026-08-01' + INTERVAL '5 days', true, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '4 minutes' + INTERVAL '1 minutes', 3),
  ('00000000-0000-0000-0003-000000000402', '00000000-0000-0000-0001-000000000004', 'field_observation', 'Observação de campo 4.2', DATE '2026-08-01' + INTERVAL '6 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '4 minutes' + INTERVAL '2 minutes', 2),
  ('00000000-0000-0000-0003-000000000501', '00000000-0000-0000-0001-000000000005', 'field_observation', 'Observação de campo 5.1', DATE '2026-08-01' + INTERVAL '6 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '5 minutes' + INTERVAL '1 minutes', 2),
  ('00000000-0000-0000-0003-000000000502', '00000000-0000-0000-0001-000000000005', 'agency_listing', 'Agência Exemplo 5.2', DATE '2026-08-01' + INTERVAL '7 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '5 minutes' + INTERVAL '2 minutes', 1),
  ('00000000-0000-0000-0003-000000000503', '00000000-0000-0000-0001-000000000005', 'institutional', NULL, DATE '2026-08-01' + INTERVAL '8 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '5 minutes' + INTERVAL '3 minutes', 3),
  ('00000000-0000-0000-0003-000000000601', '00000000-0000-0000-0001-000000000006', 'agency_listing', 'Agência Exemplo 6.1', DATE '2026-08-01' + INTERVAL '7 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '6 minutes' + INTERVAL '1 minutes', 1),
  ('00000000-0000-0000-0003-000000000701', '00000000-0000-0000-0001-000000000007', 'institutional', 'Registo institucional 7.1', DATE '2026-08-01' + INTERVAL '8 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '7 minutes' + INTERVAL '1 minutes', 3),
  ('00000000-0000-0000-0003-000000000702', '00000000-0000-0000-0001-000000000007', 'field_observation', 'Observação de campo 7.2', DATE '2026-08-01' + INTERVAL '9 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '7 minutes' + INTERVAL '2 minutes', 2),
  ('00000000-0000-0000-0003-000000000801', '00000000-0000-0000-0001-000000000008', 'field_observation', 'Observação de campo 8.1', DATE '2026-08-01' + INTERVAL '9 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '8 minutes' + INTERVAL '1 minutes', 2),
  ('00000000-0000-0000-0003-000000000802', '00000000-0000-0000-0001-000000000008', 'agency_listing', 'Agência Exemplo 8.2', DATE '2026-08-01' + INTERVAL '10 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '8 minutes' + INTERVAL '2 minutes', 1),
  ('00000000-0000-0000-0003-000000000803', '00000000-0000-0000-0001-000000000008', 'institutional', NULL, NULL, false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '8 minutes' + INTERVAL '3 minutes', 3),
  ('00000000-0000-0000-0003-000000000901', '00000000-0000-0000-0001-000000000009', 'agency_listing', 'Agência Exemplo 9.1', DATE '2026-08-01' + INTERVAL '10 days', true, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '9 minutes' + INTERVAL '1 minutes', 1),
  ('00000000-0000-0000-0003-000000001001', '00000000-0000-0000-0001-000000000010', 'institutional', 'Registo institucional 10.1', DATE '2026-08-01' + INTERVAL '11 days', true, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '10 minutes' + INTERVAL '1 minutes', 3),
  ('00000000-0000-0000-0003-000000001002', '00000000-0000-0000-0001-000000000010', 'field_observation', 'Observação de campo 10.2', DATE '2026-08-01' + INTERVAL '12 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '10 minutes' + INTERVAL '2 minutes', 2),
  ('00000000-0000-0000-0003-000000001101', '00000000-0000-0000-0001-000000000011', 'field_observation', 'Observação de campo 11.1', DATE '2026-08-01' + INTERVAL '12 days', true, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '11 minutes' + INTERVAL '1 minutes', 2),
  ('00000000-0000-0000-0003-000000001102', '00000000-0000-0000-0001-000000000011', 'agency_listing', 'Agência Exemplo 11.2', DATE '2026-08-01' + INTERVAL '13 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '11 minutes' + INTERVAL '2 minutes', 1),
  ('00000000-0000-0000-0003-000000001103', '00000000-0000-0000-0001-000000000011', 'institutional', NULL, DATE '2026-08-01' + INTERVAL '14 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '11 minutes' + INTERVAL '3 minutes', 3),
  ('00000000-0000-0000-0003-000000001201', '00000000-0000-0000-0001-000000000012', 'agency_listing', 'Agência Exemplo 12.1', DATE '2026-08-01' + INTERVAL '13 days', true, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '12 minutes' + INTERVAL '1 minutes', 1),
  ('00000000-0000-0000-0003-000000001301', '00000000-0000-0000-0001-000000000013', 'institutional', 'Registo institucional 13.1', DATE '2026-08-01' + INTERVAL '14 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '13 minutes' + INTERVAL '1 minutes', 3),
  ('00000000-0000-0000-0003-000000001302', '00000000-0000-0000-0001-000000000013', 'field_observation', 'Observação de campo 13.2', DATE '2026-08-01' + INTERVAL '15 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '13 minutes' + INTERVAL '2 minutes', 2),
  ('00000000-0000-0000-0003-000000001401', '00000000-0000-0000-0001-000000000014', 'field_observation', 'Observação de campo 14.1', DATE '2026-08-01' + INTERVAL '15 days', true, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '14 minutes' + INTERVAL '1 minutes', 2),
  ('00000000-0000-0000-0003-000000001402', '00000000-0000-0000-0001-000000000014', 'agency_listing', 'Agência Exemplo 14.2', DATE '2026-08-01' + INTERVAL '16 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '14 minutes' + INTERVAL '2 minutes', 1),
  ('00000000-0000-0000-0003-000000001403', '00000000-0000-0000-0001-000000000014', 'institutional', NULL, NULL, false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '14 minutes' + INTERVAL '3 minutes', 3),
  ('00000000-0000-0000-0003-000000001501', '00000000-0000-0000-0001-000000000015', 'agency_listing', 'Agência Exemplo 15.1', DATE '2026-08-01' + INTERVAL '16 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '15 minutes' + INTERVAL '1 minutes', 1),
  ('00000000-0000-0000-0003-000000001601', '00000000-0000-0000-0001-000000000016', 'institutional', 'Registo institucional 16.1', DATE '2026-08-01' + INTERVAL '17 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '16 minutes' + INTERVAL '1 minutes', 3),
  ('00000000-0000-0000-0003-000000001602', '00000000-0000-0000-0001-000000000016', 'field_observation', 'Observação de campo 16.2', DATE '2026-08-01' + INTERVAL '18 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '16 minutes' + INTERVAL '2 minutes', 2),
  ('00000000-0000-0000-0003-000000001701', '00000000-0000-0000-0001-000000000017', 'field_observation', 'Observação de campo 17.1', DATE '2026-08-01' + INTERVAL '18 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '17 minutes' + INTERVAL '1 minutes', 2),
  ('00000000-0000-0000-0003-000000001702', '00000000-0000-0000-0001-000000000017', 'agency_listing', 'Agência Exemplo 17.2', DATE '2026-08-01' + INTERVAL '19 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '17 minutes' + INTERVAL '2 minutes', 1),
  ('00000000-0000-0000-0003-000000001703', '00000000-0000-0000-0001-000000000017', 'institutional', NULL, DATE '2026-08-01' + INTERVAL '20 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '17 minutes' + INTERVAL '3 minutes', 3),
  ('00000000-0000-0000-0003-000000001801', '00000000-0000-0000-0001-000000000018', 'agency_listing', 'Agência Exemplo 18.1', DATE '2026-08-01' + INTERVAL '19 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '18 minutes' + INTERVAL '1 minutes', 1),
  ('00000000-0000-0000-0003-000000001901', '00000000-0000-0000-0001-000000000019', 'institutional', 'Registo institucional 19.1', DATE '2026-08-01' + INTERVAL '20 days', true, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '19 minutes' + INTERVAL '1 minutes', 3),
  ('00000000-0000-0000-0003-000000001902', '00000000-0000-0000-0001-000000000019', 'field_observation', 'Observação de campo 19.2', DATE '2026-08-01' + INTERVAL '21 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '19 minutes' + INTERVAL '2 minutes', 2),
  ('00000000-0000-0000-0003-000000002001', '00000000-0000-0000-0001-000000000020', 'field_observation', 'Observação de campo 20.1', DATE '2026-08-01' + INTERVAL '21 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '20 minutes' + INTERVAL '1 minutes', 2),
  ('00000000-0000-0000-0003-000000002002', '00000000-0000-0000-0001-000000000020', 'agency_listing', 'Agência Exemplo 20.2', DATE '2026-08-01' + INTERVAL '22 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '20 minutes' + INTERVAL '2 minutes', 1),
  ('00000000-0000-0000-0003-000000002003', '00000000-0000-0000-0001-000000000020', 'institutional', NULL, NULL, false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '20 minutes' + INTERVAL '3 minutes', 3),
  ('00000000-0000-0000-0003-000000002101', '00000000-0000-0000-0001-000000000021', 'agency_listing', 'Agência Exemplo 21.1', DATE '2026-08-01' + INTERVAL '22 days', true, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '21 minutes' + INTERVAL '1 minutes', 1),
  ('00000000-0000-0000-0003-000000002201', '00000000-0000-0000-0001-000000000022', 'institutional', 'Registo institucional 22.1', DATE '2026-08-01' + INTERVAL '23 days', true, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '22 minutes' + INTERVAL '1 minutes', 3),
  ('00000000-0000-0000-0003-000000002202', '00000000-0000-0000-0001-000000000022', 'field_observation', 'Observação de campo 22.2', DATE '2026-08-01' + INTERVAL '24 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '22 minutes' + INTERVAL '2 minutes', 2),
  ('00000000-0000-0000-0003-000000002301', '00000000-0000-0000-0001-000000000023', 'field_observation', 'Observação de campo 23.1', DATE '2026-08-01' + INTERVAL '24 days', true, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '23 minutes' + INTERVAL '1 minutes', 2),
  ('00000000-0000-0000-0003-000000002302', '00000000-0000-0000-0001-000000000023', 'agency_listing', 'Agência Exemplo 23.2', DATE '2026-08-01' + INTERVAL '25 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '23 minutes' + INTERVAL '2 minutes', 1),
  ('00000000-0000-0000-0003-000000002303', '00000000-0000-0000-0001-000000000023', 'institutional', NULL, DATE '2026-08-01' + INTERVAL '26 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '23 minutes' + INTERVAL '3 minutes', 3),
  ('00000000-0000-0000-0003-000000002401', '00000000-0000-0000-0001-000000000024', 'agency_listing', 'Agência Exemplo 24.1', DATE '2026-08-01' + INTERVAL '25 days', true, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '24 minutes' + INTERVAL '1 minutes', 1);

DELETE FROM confidence_rule
WHERE id BETWEEN '00000000-0000-0000-0004-000000000000' AND '00000000-0000-0000-0004-ffffffffffff';

INSERT INTO confidence_rule (
  id, organisation_id, rule_name, rule_type, condition_json, score_delta, is_active, priority
) VALUES
  ('00000000-0000-0000-0004-000000000001', '00000000-0000-0000-0000-00000000000a', 'source_units_1', 'source_count', '{"minUnits":1,"maxUnits":1}'::jsonb, 20, true, 10),
  ('00000000-0000-0000-0004-000000000002', '00000000-0000-0000-0000-00000000000a', 'source_units_2_plus', 'source_count', '{"minUnits":2,"maxUnits":null}'::jsonb, 15, true, 20),
  ('00000000-0000-0000-0004-000000000003', '00000000-0000-0000-0000-00000000000a', 'field_observation_bonus', 'source_type_bonus', '{"sourceType":"field_observation"}'::jsonb, 20, true, 30),
  ('00000000-0000-0000-0004-000000000004', '00000000-0000-0000-0000-00000000000a', 'verified_source_bonus', 'verified_source_bonus', '{"minVerified":1}'::jsonb, 20, true, 35),
  ('00000000-0000-0000-0004-000000000005', '00000000-0000-0000-0000-00000000000a', 'doc_declared_bonus', 'doc_status_bonus', '{"docStatus":"declared"}'::jsonb, 10, true, 40),
  ('00000000-0000-0000-0004-000000000006', '00000000-0000-0000-0000-00000000000a', 'doc_partial_bonus', 'doc_status_bonus', '{"docStatus":"partial"}'::jsonb, 10, true, 50),
  ('00000000-0000-0000-0004-000000000007', '00000000-0000-0000-0000-00000000000a', 'doc_verified_bonus', 'doc_status_bonus', '{"docStatus":"verified"}'::jsonb, 25, true, 60),
  ('00000000-0000-0000-0004-000000000008', '00000000-0000-0000-0000-00000000000a', 'doc_litigious_penalty', 'doc_status_bonus', '{"docStatus":"litigious"}'::jsonb, -30, true, 70),
  ('00000000-0000-0000-0004-000000000009', '00000000-0000-0000-0000-00000000000a', 'stale_sources_penalty', 'age_penalty', '{"olderThanMonths":24}'::jsonb, -5, true, 80);

DELETE FROM comparable_observation
WHERE id BETWEEN '00000000-0000-0000-0005-000000000000' AND '00000000-0000-0000-0005-ffffffffffff';

INSERT INTO comparable_observation (
  id, organisation_id, property_type, price_xof, area_sqm, observed_at, geom
) VALUES
  -- house (pps 220k..310k, outlier 09 = 950k)
  ('00000000-0000-0000-0005-000000000001', '00000000-0000-0000-0000-00000000000a', 'house', 26400000, 120, DATE '2024-11-12', ST_SetSRID(ST_MakePoint(-15.6500, 11.8300), 4326)),
  ('00000000-0000-0000-0005-000000000002', '00000000-0000-0000-0000-00000000000a', 'house', 35250000, 150, DATE '2025-01-08', ST_SetSRID(ST_MakePoint(-15.6300, 11.8420), 4326)),
  ('00000000-0000-0000-0005-000000000003', '00000000-0000-0000-0000-00000000000a', 'house', 26950000, 110, DATE '2025-03-17', ST_SetSRID(ST_MakePoint(-15.6150, 11.8550), 4326)),
  ('00000000-0000-0000-0005-000000000004', '00000000-0000-0000-0000-00000000000a', 'house', 33800000, 130, DATE '2025-06-03', ST_SetSRID(ST_MakePoint(-15.6000, 11.8680), 4326)),
  ('00000000-0000-0000-0005-000000000005', '00000000-0000-0000-0000-00000000000a', 'house', 27000000, 100, DATE '2025-08-26', ST_SetSRID(ST_MakePoint(-15.5800, 11.8750), 4326)),
  ('00000000-0000-0000-0005-000000000006', '00000000-0000-0000-0000-00000000000a', 'house', 50400000, 180, DATE '2025-11-04', ST_SetSRID(ST_MakePoint(-15.5650, 11.8900), 4326)),
  ('00000000-0000-0000-0005-000000000007', '00000000-0000-0000-0000-00000000000a', 'house', 41300000, 140, DATE '2026-02-19', ST_SetSRID(ST_MakePoint(-15.5400, 11.9100), 4326)),
  ('00000000-0000-0000-0005-000000000008', '00000000-0000-0000-0000-00000000000a', 'house', 49600000, 160, DATE '2026-05-28', ST_SetSRID(ST_MakePoint(-15.5200, 11.9300), 4326)),
  ('00000000-0000-0000-0005-000000000009', '00000000-0000-0000-0000-00000000000a', 'house', 85500000, 90, DATE '2026-10-15', ST_SetSRID(ST_MakePoint(-15.5980, 11.8620), 4326)),
  -- apartment (pps 380k..465k, outliers 17 = 1 600k et 18 = 90k; 10-12 à < 500 m du centre)
  ('00000000-0000-0000-0005-000000000010', '00000000-0000-0000-0000-00000000000a', 'apartment', 32800000, 80, DATE '2026-09-18', ST_SetSRID(ST_MakePoint(-15.5995, 11.8645), 4326)),
  ('00000000-0000-0000-0005-000000000011', '00000000-0000-0000-0000-00000000000a', 'apartment', 31500000, 75, DATE '2026-07-02', ST_SetSRID(ST_MakePoint(-15.5958, 11.8618), 4326)),
  ('00000000-0000-0000-0005-000000000012', '00000000-0000-0000-0000-00000000000a', 'apartment', 41325000, 95, DATE '2026-03-14', ST_SetSRID(ST_MakePoint(-15.6005, 11.8655), 4326)),
  ('00000000-0000-0000-0005-000000000013', '00000000-0000-0000-0000-00000000000a', 'apartment', 26600000, 70, DATE '2026-10-05', ST_SetSRID(ST_MakePoint(-15.5900, 11.8710), 4326)),
  ('00000000-0000-0000-0005-000000000014', '00000000-0000-0000-0000-00000000000a', 'apartment', 25675000, 65, DATE '2025-11-20', ST_SetSRID(ST_MakePoint(-15.6100, 11.8550), 4326)),
  ('00000000-0000-0000-0005-000000000015', '00000000-0000-0000-0000-00000000000a', 'apartment', 49500000, 110, DATE '2025-08-09', ST_SetSRID(ST_MakePoint(-15.6200, 11.8800), 4326)),
  ('00000000-0000-0000-0005-000000000016', '00000000-0000-0000-0000-00000000000a', 'apartment', 39525000, 85, DATE '2025-04-22', ST_SetSRID(ST_MakePoint(-15.5750, 11.8400), 4326)),
  ('00000000-0000-0000-0005-000000000017', '00000000-0000-0000-0000-00000000000a', 'apartment', 96000000, 60, DATE '2026-01-30', ST_SetSRID(ST_MakePoint(-15.5850, 11.8750), 4326)),
  ('00000000-0000-0000-0005-000000000018', '00000000-0000-0000-0000-00000000000a', 'apartment', 9000000, 100, DATE '2025-02-11', ST_SetSRID(ST_MakePoint(-15.6150, 11.8500), 4326)),
  -- land (pps 12k..30k, outlier 27 = 180k)
  ('00000000-0000-0000-0005-000000000019', '00000000-0000-0000-0000-00000000000a', 'land', 9600000, 800, DATE '2024-11-20', ST_SetSRID(ST_MakePoint(-15.6800, 11.8100), 4326)),
  ('00000000-0000-0000-0005-000000000020', '00000000-0000-0000-0000-00000000000a', 'land', 8400000, 600, DATE '2025-02-05', ST_SetSRID(ST_MakePoint(-15.6600, 11.8200), 4326)),
  ('00000000-0000-0000-0005-000000000021', '00000000-0000-0000-0000-00000000000a', 'land', 16500000, 1000, DATE '2025-04-14', ST_SetSRID(ST_MakePoint(-15.6400, 11.8350), 4326)),
  ('00000000-0000-0000-0005-000000000022', '00000000-0000-0000-0000-00000000000a', 'land', 9000000, 500, DATE '2025-07-01', ST_SetSRID(ST_MakePoint(-15.6200, 11.8500), 4326)),
  ('00000000-0000-0000-0005-000000000023', '00000000-0000-0000-0000-00000000000a', 'land', 14700000, 700, DATE '2025-09-23', ST_SetSRID(ST_MakePoint(-15.5900, 11.8700), 4326)),
  ('00000000-0000-0000-0005-000000000024', '00000000-0000-0000-0000-00000000000a', 'land', 10800000, 450, DATE '2025-12-10', ST_SetSRID(ST_MakePoint(-15.5700, 11.8850), 4326)),
  ('00000000-0000-0000-0005-000000000025', '00000000-0000-0000-0000-00000000000a', 'land', 24750000, 900, DATE '2026-03-03', ST_SetSRID(ST_MakePoint(-15.5500, 11.9000), 4326)),
  ('00000000-0000-0000-0005-000000000026', '00000000-0000-0000-0000-00000000000a', 'land', 36000000, 1200, DATE '2026-07-15', ST_SetSRID(ST_MakePoint(-15.5100, 11.9400), 4326)),
  ('00000000-0000-0000-0005-000000000027', '00000000-0000-0000-0000-00000000000a', 'land', 90000000, 500, DATE '2026-10-02', ST_SetSRID(ST_MakePoint(-15.5990, 11.8640), 4326)),
  -- commercial (pps 450k..660k, outlier 36 = 2 500k)
  ('00000000-0000-0000-0005-000000000028', '00000000-0000-0000-0000-00000000000a', 'commercial', 27000000, 60, DATE '2024-12-03', ST_SetSRID(ST_MakePoint(-15.6700, 11.8250), 4326)),
  ('00000000-0000-0000-0005-000000000029', '00000000-0000-0000-0000-00000000000a', 'commercial', 38400000, 80, DATE '2025-03-28', ST_SetSRID(ST_MakePoint(-15.6350, 11.8450), 4326)),
  ('00000000-0000-0000-0005-000000000030', '00000000-0000-0000-0000-00000000000a', 'commercial', 51000000, 100, DATE '2025-05-19', ST_SetSRID(ST_MakePoint(-15.6050, 11.8600), 4326)),
  ('00000000-0000-0000-0005-000000000031', '00000000-0000-0000-0000-00000000000a', 'commercial', 24300000, 45, DATE '2025-08-14', ST_SetSRID(ST_MakePoint(-15.5970, 11.8630), 4326)),
  ('00000000-0000-0000-0005-000000000032', '00000000-0000-0000-0000-00000000000a', 'commercial', 67200000, 120, DATE '2025-10-29', ST_SetSRID(ST_MakePoint(-15.5930, 11.8670), 4326)),
  ('00000000-0000-0000-0005-000000000033', '00000000-0000-0000-0000-00000000000a', 'commercial', 44250000, 75, DATE '2026-01-12', ST_SetSRID(ST_MakePoint(-15.5850, 11.8780), 4326)),
  ('00000000-0000-0000-0005-000000000034', '00000000-0000-0000-0000-00000000000a', 'commercial', 93000000, 150, DATE '2026-04-21', ST_SetSRID(ST_MakePoint(-15.5600, 11.8950), 4326)),
  ('00000000-0000-0000-0005-000000000035', '00000000-0000-0000-0000-00000000000a', 'commercial', 36300000, 55, DATE '2026-08-30', ST_SetSRID(ST_MakePoint(-15.5300, 11.9200), 4326)),
  ('00000000-0000-0000-0005-000000000036', '00000000-0000-0000-0000-00000000000a', 'commercial', 125000000, 50, DATE '2026-10-24', ST_SetSRID(ST_MakePoint(-15.5985, 11.8625), 4326));
