import {describe, expect, test} from 'vitest';
import {BISSAU_BBOX} from './constants';
import {parseMapSearchParams, serializeMapSearchParams, withFiltersReset} from './url-state';

describe('parseMapSearchParams', () => {
  test('defaults missing bbox to Bissau', () => {
    expect(parseMapSearchParams(new URLSearchParams()).bbox).toBe(BISSAU_BBOX);
  });

  test('rejects invalid bbox', () => {
    expect(parseMapSearchParams(new URLSearchParams('bbox=1,2,3')).bbox).toBe(BISSAU_BBOX);
    expect(parseMapSearchParams(new URLSearchParams('bbox=-15.50,11.80,-15.70,11.95')).bbox).toBe(
      BISSAU_BBOX,
    );
  });

  test('clamps out-of-range coordinates', () => {
    const state = parseMapSearchParams(new URLSearchParams('bbox=-200,11.80,-15.50,11.95'));
    expect(state.bbox.startsWith('-180.00000')).toBe(true);
  });

  test('ignores invalid filters and page', () => {
    const state = parseMapSearchParams(
      new URLSearchParams('bbox=-15.70,11.80,-15.50,11.95&type=villa&confidence=x&page=-2'),
    );
    expect(state.type).toBeUndefined();
    expect(state.confidence).toBeUndefined();
    expect(state.page).toBe(0);
  });
});

describe('serializeMapSearchParams', () => {
  test('rounds bbox and omits defaults', () => {
    const query = serializeMapSearchParams({
      bbox: '-15.7,11.8,-15.5,11.95',
      page: 0,
    });
    expect(query).toBe('bbox=-15.70000%2C11.80000%2C-15.50000%2C11.95000');
  });

  test('includes filters and page', () => {
    const query = serializeMapSearchParams({
      bbox: BISSAU_BBOX,
      type: 'house',
      confidence: 'high',
      page: 1,
    });
    expect(query).toContain('type=house');
    expect(query).toContain('confidence=high');
    expect(query).toContain('page=1');
  });
});

describe('withFiltersReset', () => {
  test('resets page to 0', () => {
    const next = withFiltersReset(
      {bbox: BISSAU_BBOX, type: 'house', page: 2},
      {type: 'land'},
    );
    expect(next.page).toBe(0);
    expect(next.type).toBe('land');
  });
});
