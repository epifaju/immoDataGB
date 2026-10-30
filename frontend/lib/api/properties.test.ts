import {afterEach, describe, expect, test, vi} from 'vitest';
import {fetchProperties} from './properties';

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('fetchProperties', () => {
  test('returns known error body as-is', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        status: 400,
        json: async () => ({
          success: false,
          data: null,
          message: 'PROPERTY_BBOX_INVALID',
          meta: {},
        }),
      }),
    );
    const result = await fetchProperties('http://localhost:8080', {
      bbox: 'bad',
      page: 0,
    });
    expect(result.status).toBe(400);
    expect(result.body.message).toBe('PROPERTY_BBOX_INVALID');
  });

  test('throws NETWORK on fetch failure', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')));
    await expect(
      fetchProperties('http://localhost:8080', {bbox: '-15.70,11.80,-15.50,11.95', page: 0}),
    ).rejects.toThrow('NETWORK');
  });

  test('throws NOT_JSON on non-json body', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        status: 500,
        json: async () => {
          throw new Error('nope');
        },
      }),
    );
    await expect(
      fetchProperties('http://localhost:8080', {bbox: '-15.70,11.80,-15.50,11.95', page: 0}),
    ).rejects.toThrow('NOT_JSON');
  });
});
