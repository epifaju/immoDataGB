import {expect, test} from 'vitest';
import enCommon from './en/common.json';
import frCommon from './fr/common.json';
import ptCommon from './pt/common.json';
import enErrors from './en/errors.json';
import frErrors from './fr/errors.json';
import ptErrors from './pt/errors.json';
import enMap from './en/map.json';
import frMap from './fr/map.json';
import ptMap from './pt/map.json';
import enProperty from './en/property.json';
import frProperty from './fr/property.json';
import ptProperty from './pt/property.json';
import enImports from './en/imports.json';
import frImports from './fr/imports.json';
import ptImports from './pt/imports.json';
import enValuation from './en/valuation.json';
import frValuation from './fr/valuation.json';
import ptValuation from './pt/valuation.json';
import enField from './en/field.json';
import frField from './fr/field.json';
import ptField from './pt/field.json';

function leafKeys(value: unknown, prefix = ''): string[] {
  if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
    return Object.entries(value).flatMap(([key, nested]) =>
      leafKeys(nested, prefix === '' ? key : `${prefix}.${key}`),
    );
  }
  return [prefix];
}

test('common locales share the same keys', () => {
  expect(leafKeys(frCommon).sort()).toEqual(leafKeys(ptCommon).sort());
  expect(leafKeys(enCommon).sort()).toEqual(leafKeys(ptCommon).sort());
});

test('error locales share the same keys', () => {
  expect(leafKeys(frErrors).sort()).toEqual(leafKeys(ptErrors).sort());
  expect(leafKeys(enErrors).sort()).toEqual(leafKeys(ptErrors).sort());
});

test('map locales share the same keys', () => {
  expect(leafKeys(frMap).sort()).toEqual(leafKeys(ptMap).sort());
  expect(leafKeys(enMap).sort()).toEqual(leafKeys(ptMap).sort());
});

test('document error codes exist in every locale', () => {
  for (const errors of [ptErrors, frErrors, enErrors]) {
    expect(Object.keys(errors).filter((key) => key.startsWith('DOCUMENT_')).length).toBe(7);
  }
});

test('valuation locales share the same keys', () => {
  expect(leafKeys(frValuation).sort()).toEqual(leafKeys(ptValuation).sort());
  expect(leafKeys(enValuation).sort()).toEqual(leafKeys(ptValuation).sort());
});

test('valuation error codes and nav label exist in every locale', () => {
  for (const errors of [ptErrors, frErrors, enErrors]) {
    expect(Object.keys(errors).filter((key) => key.startsWith('VALUATION_')).sort()).toEqual([
      'VALUATION_NO_COMPARABLES',
      'VALUATION_REQUEST_INVALID',
    ]);
  }
  expect(ptCommon.nav.valuation).toBe('Avaliação');
  expect(frCommon.nav.valuation).toBe('Estimation');
  expect(enCommon.nav.valuation).toBe('Valuation');
});

test('property locales share the same keys', () => {
  expect(leafKeys(frProperty).sort()).toEqual(leafKeys(ptProperty).sort());
  expect(leafKeys(enProperty).sort()).toEqual(leafKeys(ptProperty).sort());
});

test('imports locales share the same keys', () => {
  expect(leafKeys(frImports).sort()).toEqual(leafKeys(ptImports).sort());
  expect(leafKeys(enImports).sort()).toEqual(leafKeys(ptImports).sort());
});

test('import error codes and nav label exist in every locale', () => {
  for (const errors of [ptErrors, frErrors, enErrors]) {
    expect(Object.keys(errors).filter((key) => key.startsWith('IMPORT_')).sort()).toEqual([
      'IMPORT_DUPLICATE_REFERENCE',
      'IMPORT_FILE_REQUIRED',
      'IMPORT_FILE_TOO_LARGE',
      'IMPORT_INVALID_COORDINATE',
      'IMPORT_INVALID_HEADER',
      'IMPORT_INVALID_PROPERTY_TYPE',
      'IMPORT_INVALID_TYPE',
      'IMPORT_JOB_ID_INVALID',
      'IMPORT_JOB_NOT_FOUND',
      'IMPORT_MISSING_FIELD',
      'IMPORT_PAGE_INVALID',
    ]);
  }
  expect(ptCommon.nav.imports).toBe('Importações');
  expect(frCommon.nav.imports).toBe('Imports');
  expect(enCommon.nav.imports).toBe('Imports');
});

test('field locales share the same keys', () => {
  expect(leafKeys(frField).sort()).toEqual(leafKeys(ptField).sort());
  expect(leafKeys(enField).sort()).toEqual(leafKeys(ptField).sort());
});

test('field error codes and nav label exist in every locale', () => {
  for (const errors of [ptErrors, frErrors, enErrors]) {
    expect(errors).toHaveProperty('FIELD_REQUEST_INVALID');
    expect(errors).toHaveProperty('FIELD_ORGANISATION_MISSING');
  }
  expect(ptCommon.nav.field).toBe('Terreno');
  expect(frCommon.nav.field).toBe('Terrain');
  expect(enCommon.nav.field).toBe('Field');
});