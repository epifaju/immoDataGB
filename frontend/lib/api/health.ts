import {fetchJson} from './http';
import type {ApiResponse, HealthResult, HealthView} from './types';

function isHealthBody(value: unknown): value is ApiResponse<HealthView | null> {
  if (value === null || typeof value !== 'object') {
    return false;
  }
  const body = value as Partial<ApiResponse<HealthView | null>>;
  const messageOk = body.message === null || typeof body.message === 'string';
  const metaOk = body.meta !== null && typeof body.meta === 'object';
  return typeof body.success === 'boolean' && messageOk && metaOk && 'data' in body;
}

export async function fetchHealth(apiUrl: string): Promise<HealthResult> {
  const {status, body} = await fetchJson<unknown>(`${apiUrl}/api/v1/health`, {
    headers: {Accept: 'application/json'},
    cache: 'no-store',
  });
  if (!isHealthBody(body)) {
    throw new Error('NOT_JSON');
  }
  return {status, body};
}
