import {fetchJson} from './http';
import type {
  ApiResponse,
  AuditListResult,
  AuditQuery,
  RejectPropertyResult,
  ValidatePropertyResult,
  ValidationQueueQuery,
  ValidationQueueResult,
} from './types';

export const ADMIN_PAGE_SIZE = 20;
export const VALIDATION_QUEUE_QUERY_KEY = ['validation-queue'] as const;
export const AUDIT_QUERY_KEY = ['audit'] as const;

function isEnvelope(value: unknown): value is ApiResponse<unknown> {
  if (value === null || typeof value !== 'object') {
    return false;
  }
  const body = value as Partial<ApiResponse<unknown>>;
  const messageOk = body.message === null || typeof body.message === 'string';
  const metaOk = body.meta !== null && typeof body.meta === 'object';
  return typeof body.success === 'boolean' && messageOk && metaOk && 'data' in body;
}

async function getEnvelope(
  url: string,
  signal?: AbortSignal,
): Promise<{status: number; body: ApiResponse<unknown>}> {
  const {status, body} = await fetchJson<unknown>(url, {
    headers: {Accept: 'application/json'},
    cache: 'no-store',
    signal,
  });
  if (!isEnvelope(body)) {
    throw new Error('NOT_JSON');
  }
  return {status, body};
}

async function postEnvelope(url: string): Promise<{status: number; body: ApiResponse<unknown>}> {
  const {status, body} = await fetchJson<unknown>(url, {
    method: 'POST',
    headers: {Accept: 'application/json'},
    cache: 'no-store',
  });
  if (!isEnvelope(body)) {
    throw new Error('NOT_JSON');
  }
  return {status, body};
}

function appendIfPresent(params: URLSearchParams, key: string, value: string | undefined) {
  const trimmed = value?.trim();
  if (trimmed) {
    params.set(key, trimmed);
  }
}

export async function fetchValidationQueue(
  apiUrl: string,
  query: ValidationQueueQuery,
  signal?: AbortSignal,
): Promise<ValidationQueueResult> {
  const params = new URLSearchParams({
    page: String(query.page),
    size: String(query.size),
  });
  const {status, body} = await getEnvelope(
    `${apiUrl}/api/v1/admin/validation-queue?${params.toString()}`,
    signal,
  );
  return {status, body: body as ValidationQueueResult['body']};
}

export async function validateProperty(apiUrl: string, id: string): Promise<ValidatePropertyResult> {
  const {status, body} = await postEnvelope(`${apiUrl}/api/v1/admin/properties/${id}/validate`);
  return {status, body: body as ValidatePropertyResult['body']};
}

export async function rejectProperty(apiUrl: string, id: string): Promise<RejectPropertyResult> {
  const {status, body} = await postEnvelope(`${apiUrl}/api/v1/admin/properties/${id}/reject`);
  return {status, body: body as RejectPropertyResult['body']};
}

export async function fetchAudit(
  apiUrl: string,
  query: AuditQuery,
  signal?: AbortSignal,
): Promise<AuditListResult> {
  const params = new URLSearchParams({
    page: String(query.page),
    size: String(query.size),
  });
  appendIfPresent(params, 'entityType', query.entityType);
  appendIfPresent(params, 'action', query.action);
  appendIfPresent(params, 'from', query.from);
  appendIfPresent(params, 'to', query.to);
  const {status, body} = await getEnvelope(`${apiUrl}/api/v1/admin/audit?${params.toString()}`, signal);
  return {status, body: body as AuditListResult['body']};
}
