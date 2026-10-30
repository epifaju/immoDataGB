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
  let response: Response;
  try {
    response = await fetch(`${apiUrl}/api/v1/health`, {
      headers: {Accept: 'application/json'},
      cache: 'no-store',
    });
  } catch {
    throw new Error('NETWORK');
  }
  try {
    const body: unknown = await response.json();
    if (!isHealthBody(body)) {
      throw new Error('NOT_JSON');
    }
    return {status: response.status, body};
  } catch (error) {
    if (error instanceof Error && error.message === 'NOT_JSON') {
      throw error;
    }
    throw new Error('NOT_JSON');
  }
}
