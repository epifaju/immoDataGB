import {afterEach, describe, expect, test, vi} from 'vitest';
import {fetchProperties, fetchPropertyDetail, fetchPropertySources} from './properties';

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

describe('fetchPropertyDetail', () => {
  test('returns known 4xx body as-is', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        status: 404,
        json: async () => ({
          success: false,
          data: null,
          message: 'PROPERTY_NOT_FOUND',
          meta: {},
        }),
      }),
    );
    const result = await fetchPropertyDetail(
      'http://localhost:8080',
      '00000000-0000-0000-0001-000000000099',
    );
    expect(result.status).toBe(404);
    expect(result.body.message).toBe('PROPERTY_NOT_FOUND');
  });

  test('throws NETWORK on fetch failure', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')));
    await expect(
      fetchPropertyDetail('http://localhost:8080', '00000000-0000-0000-0001-000000000001'),
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
      fetchPropertyDetail('http://localhost:8080', '00000000-0000-0000-0001-000000000001'),
    ).rejects.toThrow('NOT_JSON');
  });
});

describe('fetchPropertySources', () => {
  test('returns known 4xx body as-is', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        status: 400,
        json: async () => ({
          success: false,
          data: null,
          message: 'PROPERTY_ID_INVALID',
          meta: {},
        }),
      }),
    );
    const result = await fetchPropertySources('http://localhost:8080', 'bad');
    expect(result.status).toBe(400);
    expect(result.body.message).toBe('PROPERTY_ID_INVALID');
  });

  test('throws NETWORK on fetch failure', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')));
    await expect(
      fetchPropertySources('http://localhost:8080', '00000000-0000-0000-0001-000000000001'),
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
      fetchPropertySources('http://localhost:8080', '00000000-0000-0000-0001-000000000001'),
    ).rejects.toThrow('NOT_JSON');
  });
});
