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
import enValuation from './en/valuation.json';
import frValuation from './fr/valuation.json';
import ptValuation from './pt/valuation.json';

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
