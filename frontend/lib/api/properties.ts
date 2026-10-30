import {fetchJson} from './http';
import type {
  ApiResponse,
  PageResponse,
  PropertiesResult,
  PropertyDetail,
  PropertyDetailResult,
  PropertyMapItem,
  PropertySourcesResult,
  SourceItem,
} from './types';

function isEnvelope(value: unknown): value is ApiResponse<unknown> {
  if (value === null || typeof value !== 'object') {
    return false;
  }
  const body = value as Partial<ApiResponse<unknown>>;
  const messageOk = body.message === null || typeof body.message === 'string';
  const metaOk = body.meta !== null && typeof body.meta === 'object';
  return typeof body.success === 'boolean' && messageOk && metaOk && 'data' in body;
}

export type PropertiesQuery = {
  bbox: string;
  type?: string;
  confidence?: string;
  page: number;
};

function isPropertiesBody(
  value: unknown,
): value is ApiResponse<PageResponse<PropertyMapItem> | null> {
  if (value === null || typeof value !== 'object') {
    return false;
  }
  const body = value as Partial<ApiResponse<PageResponse<PropertyMapItem> | null>>;
  const messageOk = body.message === null || typeof body.message === 'string';
  const metaOk = body.meta !== null && typeof body.meta === 'object';
  return typeof body.success === 'boolean' && messageOk && metaOk && 'data' in body;
}

export async function fetchProperties(
  apiUrl: string,
  query: PropertiesQuery,
  signal?: AbortSignal,
): Promise<PropertiesResult> {
  const params = new URLSearchParams();
  params.set('bbox', query.bbox);
  params.set('page', String(query.page));
  if (query.type) {
    params.set('type', query.type);
  }
  if (query.confidence) {
    params.set('confidence', query.confidence);
  }
  const {status, body} = await fetchJson<unknown>(
    `${apiUrl}/api/v1/properties?${params.toString()}`,
    {
      headers: {Accept: 'application/json'},
      cache: 'no-store',
      signal,
    },
  );
  if (!isPropertiesBody(body)) {
    throw new Error('NOT_JSON');
  }
  return {status, body};
}

export async function fetchPropertyDetail(
  apiUrl: string,
  id: string,
  signal?: AbortSignal,
): Promise<PropertyDetailResult> {
  const {status, body} = await fetchJson<unknown>(`${apiUrl}/api/v1/properties/${id}`, {
    headers: {Accept: 'application/json'},
    cache: 'no-store',
    signal,
  });
  if (!isEnvelope(body)) {
    throw new Error('NOT_JSON');
  }
  return {status, body: body as ApiResponse<PropertyDetail | null>};
}

export async function fetchPropertySources(
  apiUrl: string,
  id: string,
  signal?: AbortSignal,
): Promise<PropertySourcesResult> {
  const {status, body} = await fetchJson<unknown>(`${apiUrl}/api/v1/properties/${id}/sources`, {
    headers: {Accept: 'application/json'},
    cache: 'no-store',
    signal,
  });
  if (!isEnvelope(body)) {
    throw new Error('NOT_JSON');
  }
  return {status, body: body as ApiResponse<SourceItem[] | null>};
}
