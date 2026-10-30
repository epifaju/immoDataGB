-- Seed Bissau (profil dev) — deterministic, Bloc 1 + Bloc 2
-- Org: 00000000-0000-0000-0000-00000000000a
-- Property UUIDs: 00000000-0000-0000-0001-0000000000NN (NN=01..24)
-- Geometry UUIDs: 00000000-0000-0000-0002-0000000000NN
-- Source UUIDs:   00000000-0000-0000-0003-00000000NNMM (NN=bien, MM=rang)
-- Bairros (index (n-1)%6): Ajuda, Antula, Bairro Militar, Mindará, Plack II, Santa Luzia
-- Adresses illustratives, sans lien avec les coordonnées des points.

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
  id, property_id, source_type, label, observed_at, is_verified, created_at
) VALUES
  ('00000000-0000-0000-0003-000000000101', '00000000-0000-0000-0001-000000000001', 'institutional', 'Registo institucional 1.1', DATE '2026-08-01' + INTERVAL '2 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '1 minutes' + INTERVAL '1 minutes'),
  ('00000000-0000-0000-0003-000000000102', '00000000-0000-0000-0001-000000000001', 'field_observation', 'Observação de campo 1.2', DATE '2026-08-01' + INTERVAL '3 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '1 minutes' + INTERVAL '2 minutes'),
  ('00000000-0000-0000-0003-000000000201', '00000000-0000-0000-0001-000000000002', 'field_observation', 'Observação de campo 2.1', DATE '2026-08-01' + INTERVAL '3 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '2 minutes' + INTERVAL '1 minutes'),
  ('00000000-0000-0000-0003-000000000202', '00000000-0000-0000-0001-000000000002', 'agency_listing', 'Agência Exemplo 2.2', DATE '2026-08-01' + INTERVAL '4 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '2 minutes' + INTERVAL '2 minutes'),
  ('00000000-0000-0000-0003-000000000203', '00000000-0000-0000-0001-000000000002', 'institutional', NULL, NULL, false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '2 minutes' + INTERVAL '3 minutes'),
  ('00000000-0000-0000-0003-000000000301', '00000000-0000-0000-0001-000000000003', 'agency_listing', 'Agência Exemplo 3.1', DATE '2026-08-01' + INTERVAL '4 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '3 minutes' + INTERVAL '1 minutes'),
  ('00000000-0000-0000-0003-000000000401', '00000000-0000-0000-0001-000000000004', 'institutional', 'Registo institucional 4.1', DATE '2026-08-01' + INTERVAL '5 days', true, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '4 minutes' + INTERVAL '1 minutes'),
  ('00000000-0000-0000-0003-000000000402', '00000000-0000-0000-0001-000000000004', 'field_observation', 'Observação de campo 4.2', DATE '2026-08-01' + INTERVAL '6 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '4 minutes' + INTERVAL '2 minutes'),
  ('00000000-0000-0000-0003-000000000501', '00000000-0000-0000-0001-000000000005', 'field_observation', 'Observação de campo 5.1', DATE '2026-08-01' + INTERVAL '6 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '5 minutes' + INTERVAL '1 minutes'),
  ('00000000-0000-0000-0003-000000000502', '00000000-0000-0000-0001-000000000005', 'agency_listing', 'Agência Exemplo 5.2', DATE '2026-08-01' + INTERVAL '7 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '5 minutes' + INTERVAL '2 minutes'),
  ('00000000-0000-0000-0003-000000000503', '00000000-0000-0000-0001-000000000005', 'institutional', NULL, DATE '2026-08-01' + INTERVAL '8 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '5 minutes' + INTERVAL '3 minutes'),
  ('00000000-0000-0000-0003-000000000601', '00000000-0000-0000-0001-000000000006', 'agency_listing', 'Agência Exemplo 6.1', DATE '2026-08-01' + INTERVAL '7 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '6 minutes' + INTERVAL '1 minutes'),
  ('00000000-0000-0000-0003-000000000701', '00000000-0000-0000-0001-000000000007', 'institutional', 'Registo institucional 7.1', DATE '2026-08-01' + INTERVAL '8 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '7 minutes' + INTERVAL '1 minutes'),
  ('00000000-0000-0000-0003-000000000702', '00000000-0000-0000-0001-000000000007', 'field_observation', 'Observação de campo 7.2', DATE '2026-08-01' + INTERVAL '9 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '7 minutes' + INTERVAL '2 minutes'),
  ('00000000-0000-0000-0003-000000000801', '00000000-0000-0000-0001-000000000008', 'field_observation', 'Observação de campo 8.1', DATE '2026-08-01' + INTERVAL '9 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '8 minutes' + INTERVAL '1 minutes'),
  ('00000000-0000-0000-0003-000000000802', '00000000-0000-0000-0001-000000000008', 'agency_listing', 'Agência Exemplo 8.2', DATE '2026-08-01' + INTERVAL '10 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '8 minutes' + INTERVAL '2 minutes'),
  ('00000000-0000-0000-0003-000000000803', '00000000-0000-0000-0001-000000000008', 'institutional', NULL, NULL, false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '8 minutes' + INTERVAL '3 minutes'),
  ('00000000-0000-0000-0003-000000000901', '00000000-0000-0000-0001-000000000009', 'agency_listing', 'Agência Exemplo 9.1', DATE '2026-08-01' + INTERVAL '10 days', true, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '9 minutes' + INTERVAL '1 minutes'),
  ('00000000-0000-0000-0003-000000001001', '00000000-0000-0000-0001-000000000010', 'institutional', 'Registo institucional 10.1', DATE '2026-08-01' + INTERVAL '11 days', true, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '10 minutes' + INTERVAL '1 minutes'),
  ('00000000-0000-0000-0003-000000001002', '00000000-0000-0000-0001-000000000010', 'field_observation', 'Observação de campo 10.2', DATE '2026-08-01' + INTERVAL '12 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '10 minutes' + INTERVAL '2 minutes'),
  ('00000000-0000-0000-0003-000000001101', '00000000-0000-0000-0001-000000000011', 'field_observation', 'Observação de campo 11.1', DATE '2026-08-01' + INTERVAL '12 days', true, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '11 minutes' + INTERVAL '1 minutes'),
  ('00000000-0000-0000-0003-000000001102', '00000000-0000-0000-0001-000000000011', 'agency_listing', 'Agência Exemplo 11.2', DATE '2026-08-01' + INTERVAL '13 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '11 minutes' + INTERVAL '2 minutes'),
  ('00000000-0000-0000-0003-000000001103', '00000000-0000-0000-0001-000000000011', 'institutional', NULL, DATE '2026-08-01' + INTERVAL '14 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '11 minutes' + INTERVAL '3 minutes'),
  ('00000000-0000-0000-0003-000000001201', '00000000-0000-0000-0001-000000000012', 'agency_listing', 'Agência Exemplo 12.1', DATE '2026-08-01' + INTERVAL '13 days', true, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '12 minutes' + INTERVAL '1 minutes'),
  ('00000000-0000-0000-0003-000000001301', '00000000-0000-0000-0001-000000000013', 'institutional', 'Registo institucional 13.1', DATE '2026-08-01' + INTERVAL '14 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '13 minutes' + INTERVAL '1 minutes'),
  ('00000000-0000-0000-0003-000000001302', '00000000-0000-0000-0001-000000000013', 'field_observation', 'Observação de campo 13.2', DATE '2026-08-01' + INTERVAL '15 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '13 minutes' + INTERVAL '2 minutes'),
  ('00000000-0000-0000-0003-000000001401', '00000000-0000-0000-0001-000000000014', 'field_observation', 'Observação de campo 14.1', DATE '2026-08-01' + INTERVAL '15 days', true, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '14 minutes' + INTERVAL '1 minutes'),
  ('00000000-0000-0000-0003-000000001402', '00000000-0000-0000-0001-000000000014', 'agency_listing', 'Agência Exemplo 14.2', DATE '2026-08-01' + INTERVAL '16 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '14 minutes' + INTERVAL '2 minutes'),
  ('00000000-0000-0000-0003-000000001403', '00000000-0000-0000-0001-000000000014', 'institutional', NULL, NULL, false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '14 minutes' + INTERVAL '3 minutes'),
  ('00000000-0000-0000-0003-000000001501', '00000000-0000-0000-0001-000000000015', 'agency_listing', 'Agência Exemplo 15.1', DATE '2026-08-01' + INTERVAL '16 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '15 minutes' + INTERVAL '1 minutes'),
  ('00000000-0000-0000-0003-000000001601', '00000000-0000-0000-0001-000000000016', 'institutional', 'Registo institucional 16.1', DATE '2026-08-01' + INTERVAL '17 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '16 minutes' + INTERVAL '1 minutes'),
  ('00000000-0000-0000-0003-000000001602', '00000000-0000-0000-0001-000000000016', 'field_observation', 'Observação de campo 16.2', DATE '2026-08-01' + INTERVAL '18 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '16 minutes' + INTERVAL '2 minutes'),
  ('00000000-0000-0000-0003-000000001701', '00000000-0000-0000-0001-000000000017', 'field_observation', 'Observação de campo 17.1', DATE '2026-08-01' + INTERVAL '18 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '17 minutes' + INTERVAL '1 minutes'),
  ('00000000-0000-0000-0003-000000001702', '00000000-0000-0000-0001-000000000017', 'agency_listing', 'Agência Exemplo 17.2', DATE '2026-08-01' + INTERVAL '19 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '17 minutes' + INTERVAL '2 minutes'),
  ('00000000-0000-0000-0003-000000001703', '00000000-0000-0000-0001-000000000017', 'institutional', NULL, DATE '2026-08-01' + INTERVAL '20 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '17 minutes' + INTERVAL '3 minutes'),
  ('00000000-0000-0000-0003-000000001801', '00000000-0000-0000-0001-000000000018', 'agency_listing', 'Agência Exemplo 18.1', DATE '2026-08-01' + INTERVAL '19 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '18 minutes' + INTERVAL '1 minutes'),
  ('00000000-0000-0000-0003-000000001901', '00000000-0000-0000-0001-000000000019', 'institutional', 'Registo institucional 19.1', DATE '2026-08-01' + INTERVAL '20 days', true, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '19 minutes' + INTERVAL '1 minutes'),
  ('00000000-0000-0000-0003-000000001902', '00000000-0000-0000-0001-000000000019', 'field_observation', 'Observação de campo 19.2', DATE '2026-08-01' + INTERVAL '21 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '19 minutes' + INTERVAL '2 minutes'),
  ('00000000-0000-0000-0003-000000002001', '00000000-0000-0000-0001-000000000020', 'field_observation', 'Observação de campo 20.1', DATE '2026-08-01' + INTERVAL '21 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '20 minutes' + INTERVAL '1 minutes'),
  ('00000000-0000-0000-0003-000000002002', '00000000-0000-0000-0001-000000000020', 'agency_listing', 'Agência Exemplo 20.2', DATE '2026-08-01' + INTERVAL '22 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '20 minutes' + INTERVAL '2 minutes'),
  ('00000000-0000-0000-0003-000000002003', '00000000-0000-0000-0001-000000000020', 'institutional', NULL, NULL, false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '20 minutes' + INTERVAL '3 minutes'),
  ('00000000-0000-0000-0003-000000002101', '00000000-0000-0000-0001-000000000021', 'agency_listing', 'Agência Exemplo 21.1', DATE '2026-08-01' + INTERVAL '22 days', true, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '21 minutes' + INTERVAL '1 minutes'),
  ('00000000-0000-0000-0003-000000002201', '00000000-0000-0000-0001-000000000022', 'institutional', 'Registo institucional 22.1', DATE '2026-08-01' + INTERVAL '23 days', true, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '22 minutes' + INTERVAL '1 minutes'),
  ('00000000-0000-0000-0003-000000002202', '00000000-0000-0000-0001-000000000022', 'field_observation', 'Observação de campo 22.2', DATE '2026-08-01' + INTERVAL '24 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '22 minutes' + INTERVAL '2 minutes'),
  ('00000000-0000-0000-0003-000000002301', '00000000-0000-0000-0001-000000000023', 'field_observation', 'Observação de campo 23.1', DATE '2026-08-01' + INTERVAL '24 days', true, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '23 minutes' + INTERVAL '1 minutes'),
  ('00000000-0000-0000-0003-000000002302', '00000000-0000-0000-0001-000000000023', 'agency_listing', 'Agência Exemplo 23.2', DATE '2026-08-01' + INTERVAL '25 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '23 minutes' + INTERVAL '2 minutes'),
  ('00000000-0000-0000-0003-000000002303', '00000000-0000-0000-0001-000000000023', 'institutional', NULL, DATE '2026-08-01' + INTERVAL '26 days', false, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '23 minutes' + INTERVAL '3 minutes'),
  ('00000000-0000-0000-0003-000000002401', '00000000-0000-0000-0001-000000000024', 'agency_listing', 'Agência Exemplo 24.1', DATE '2026-08-01' + INTERVAL '25 days', true, TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '24 minutes' + INTERVAL '1 minutes');
