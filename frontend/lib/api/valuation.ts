import {fetchJson} from './http';
import type {ApiResponse, EstimateRequest, EstimateResponse, EstimateResult} from './types';

function isEnvelope(value: unknown): value is ApiResponse<unknown> {
  if (value === null || typeof value !== 'object') {
    return false;
  }
  const body = value as Partial<ApiResponse<unknown>>;
  const messageOk = body.message === null || typeof body.message === 'string';
  const metaOk = body.meta !== null && typeof body.meta === 'object';
  return typeof body.success === 'boolean' && messageOk && metaOk && 'data' in body;
}

export async function estimateValuation(
  apiUrl: string,
  request: EstimateRequest,
): Promise<EstimateResponse> {
  const {status, body} = await fetchJson<unknown>(`${apiUrl}/api/v1/valuations/estimate`, {
    method: 'POST',
    headers: {Accept: 'application/json', 'Content-Type': 'application/json'},
    body: JSON.stringify(request),
    cache: 'no-store',
  });
  if (!isEnvelope(body)) {
    throw new Error('NOT_JSON');
  }
  return {status, body: body as ApiResponse<EstimateResult | null>};
}
