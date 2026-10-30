import {describe, expect, test} from 'vitest';
import {formatIsoDate} from './date';

describe('formatIsoDate', () => {
  test('formats calendar date in UTC without day shift in negative TZ', () => {
    const previous = process.env.TZ;
    process.env.TZ = 'America/Sao_Paulo';
    try {
      expect(formatIsoDate('2026-03-04', 'pt')).toMatch(/4.*março.*2026/i);
      expect(formatIsoDate('2026-03-04', 'en')).toMatch(/March 4,? 2026/i);
    } finally {
      if (previous === undefined) {
        delete process.env.TZ;
      } else {
        process.env.TZ = previous;
      }
    }
  });

  test('returns original string when date is incomplete', () => {
    expect(formatIsoDate('2026-03', 'pt')).toBe('2026-03');
  });
});
