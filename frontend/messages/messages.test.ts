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
