-- Seed Bissau (profil dev) — deterministic
-- Org: 00000000-0000-0000-0000-00000000000a
-- Property UUIDs: 00000000-0000-0000-0001-0000000000NN (NN=01..24)
-- Geometry UUIDs: 00000000-0000-0000-0002-0000000000NN

INSERT INTO organisation (id, name) VALUES
  ('00000000-0000-0000-0000-00000000000a', 'ImmoData Bissau Pilote')
ON CONFLICT (id) DO NOTHING;

INSERT INTO property (
  id, organisation_id, reference, title, property_type,
  confidence_score, confidence_level, confidence_summary, created_at, updated_at
) VALUES
  ('00000000-0000-0000-0001-000000000001', '00000000-0000-0000-0000-00000000000a', 'BIS-001', 'Moradia em Bissau (BIS-001)', 'house', 12, 'low', 'Fonte declarativa única; verificação pendente.', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '1 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '1 minutes'),
  ('00000000-0000-0000-0001-000000000002', '00000000-0000-0000-0000-00000000000a', 'BIS-002', 'Apartamento em Bissau (BIS-002)', 'apartment', 12, 'low', 'Fonte declarativa única; verificação pendente.', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '2 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '2 minutes'),
  ('00000000-0000-0000-0001-000000000003', '00000000-0000-0000-0000-00000000000a', 'BIS-003', 'Terreno em Bissau (BIS-003)', 'land', 12, 'low', 'Fonte declarativa única; verificação pendente.', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '3 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '3 minutes'),
  ('00000000-0000-0000-0001-000000000004', '00000000-0000-0000-0000-00000000000a', 'BIS-004', 'Espaço comercial em Bissau (BIS-004)', 'commercial', 12, 'low', 'Fonte declarativa única; verificação pendente.', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '4 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '4 minutes'),
  ('00000000-0000-0000-0001-000000000005', '00000000-0000-0000-0000-00000000000a', 'BIS-005', 'Moradia em Bissau (BIS-005)', 'house', 41, 'medium', 'Dados parcialmente cruzados com observação de campo.', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '5 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '5 minutes'),
  ('00000000-0000-0000-0001-000000000006', '00000000-0000-0000-0000-00000000000a', 'BIS-006', 'Apartamento em Bissau (BIS-006)', 'apartment', 41, 'medium', 'Dados parcialmente cruzados com observação de campo.', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '6 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '6 minutes'),
  ('00000000-0000-0000-0001-000000000007', '00000000-0000-0000-0000-00000000000a', 'BIS-007', 'Terreno em Bissau (BIS-007)', 'land', 41, 'medium', 'Dados parcialmente cruzados com observação de campo.', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '7 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '7 minutes'),
  ('00000000-0000-0000-0001-000000000008', '00000000-0000-0000-0000-00000000000a', 'BIS-008', 'Espaço comercial em Bissau (BIS-008)', 'commercial', 41, 'medium', 'Dados parcialmente cruzados com observação de campo.', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '8 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '8 minutes'),
  ('00000000-0000-0000-0001-000000000009', '00000000-0000-0000-0000-00000000000a', 'BIS-009', 'Moradia em Bissau (BIS-009)', 'house', 72, 'high', 'Documentação e observação de campo confirmadas.', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '9 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '9 minutes'),
  ('00000000-0000-0000-0001-000000000010', '00000000-0000-0000-0000-00000000000a', 'BIS-010', 'Apartamento em Bissau (BIS-010)', 'apartment', 72, 'high', 'Documentação e observação de campo confirmadas.', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '10 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '10 minutes'),
  ('00000000-0000-0000-0001-000000000011', '00000000-0000-0000-0000-00000000000a', 'BIS-011', 'Terreno em Bissau (BIS-011)', 'land', 72, 'high', 'Documentação e observação de campo confirmadas.', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '11 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '11 minutes'),
  ('00000000-0000-0000-0001-000000000012', '00000000-0000-0000-0000-00000000000a', 'BIS-012', 'Espaço comercial em Bissau (BIS-012)', 'commercial', 72, 'high', 'Documentação e observação de campo confirmadas.', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '12 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '12 minutes'),
  ('00000000-0000-0000-0001-000000000013', '00000000-0000-0000-0000-00000000000a', 'BIS-013', 'Moradia em Bissau (BIS-013)', 'house', 28, 'low', 'Fonte declarativa única; verificação pendente.', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '13 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '13 minutes'),
  ('00000000-0000-0000-0001-000000000014', '00000000-0000-0000-0000-00000000000a', 'BIS-014', 'Apartamento em Bissau (BIS-014)', 'apartment', 28, 'low', 'Fonte declarativa única; verificação pendente.', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '14 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '14 minutes'),
  ('00000000-0000-0000-0001-000000000015', '00000000-0000-0000-0000-00000000000a', 'BIS-015', 'Terreno em Bissau (BIS-015)', 'land', 28, 'low', 'Fonte declarativa única; verificação pendente.', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '15 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '15 minutes'),
  ('00000000-0000-0000-0001-000000000016', '00000000-0000-0000-0000-00000000000a', 'BIS-016', 'Espaço comercial em Bissau (BIS-016)', 'commercial', 28, 'low', 'Fonte declarativa única; verificação pendente.', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '16 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '16 minutes'),
  ('00000000-0000-0000-0001-000000000017', '00000000-0000-0000-0000-00000000000a', 'BIS-017', 'Moradia em Bissau (BIS-017)', 'house', 58, 'medium', 'Dados parcialmente cruzados com observação de campo.', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '17 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '17 minutes'),
  ('00000000-0000-0000-0001-000000000018', '00000000-0000-0000-0000-00000000000a', 'BIS-018', 'Apartamento em Bissau (BIS-018)', 'apartment', 58, 'medium', 'Dados parcialmente cruzados com observação de campo.', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '18 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '18 minutes'),
  ('00000000-0000-0000-0001-000000000019', '00000000-0000-0000-0000-00000000000a', 'BIS-019', 'Terreno em Bissau (BIS-019)', 'land', 58, 'medium', 'Dados parcialmente cruzados com observação de campo.', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '19 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '19 minutes'),
  ('00000000-0000-0000-0001-000000000020', '00000000-0000-0000-0000-00000000000a', 'BIS-020', 'Espaço comercial em Bissau (BIS-020)', 'commercial', 58, 'medium', 'Dados parcialmente cruzados com observação de campo.', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '20 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '20 minutes'),
  ('00000000-0000-0000-0001-000000000021', '00000000-0000-0000-0000-00000000000a', 'BIS-021', 'Moradia em Bissau (BIS-021)', 'house', 91, 'high', 'Documentação e observação de campo confirmadas.', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '21 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '21 minutes'),
  ('00000000-0000-0000-0001-000000000022', '00000000-0000-0000-0000-00000000000a', 'BIS-022', 'Apartamento em Bissau (BIS-022)', 'apartment', 91, 'high', 'Documentação e observação de campo confirmadas.', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '22 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '22 minutes'),
  ('00000000-0000-0000-0001-000000000023', '00000000-0000-0000-0000-00000000000a', 'BIS-023', 'Terreno em Bissau (BIS-023)', 'land', 91, 'high', 'Documentação e observação de campo confirmadas.', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '23 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '23 minutes'),
  ('00000000-0000-0000-0001-000000000024', '00000000-0000-0000-0000-00000000000a', 'BIS-024', 'Espaço comercial em Bissau (BIS-024)', 'commercial', 91, 'high', 'Documentação e observação de campo confirmadas.', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '24 minutes', TIMESTAMP '2026-10-01 09:00:00+00' + INTERVAL '24 minutes')
ON CONFLICT (id) DO NOTHING;

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
