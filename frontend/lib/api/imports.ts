import {fetchJson} from './http';
import type {
  ApiResponse,
  ImportJobStatus,
  ImportListResponse,
  ImportReport,
  ImportReportResponse,
  ImportStartResponse,
  ImportStartResult,
  ImportStatusResponse,
  PageResponse,
} from './types';

export const MAX_IMPORT_SIZE_BYTES = 5_242_880;

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

export async function startImport(apiUrl: string, file: File): Promise<ImportStartResponse> {
  const form = new FormData();
  form.append('file', file);
  const {status, body} = await fetchJson<unknown>(`${apiUrl}/api/v1/imports`, {
    method: 'POST',
    headers: {Accept: 'application/json'},
    body: form,
    cache: 'no-store',
  });
  if (!isEnvelope(body)) {
    throw new Error('NOT_JSON');
  }
  return {status, body: body as ApiResponse<ImportStartResult | null>};
}

export async function getImportStatus(
  apiUrl: string,
  jobId: string,
  signal?: AbortSignal,
): Promise<ImportStatusResponse> {
  const {status, body} = await getEnvelope(`${apiUrl}/api/v1/imports/${jobId}/status`, signal);
  return {status, body: body as ApiResponse<ImportJobStatus | null>};
}

export async function getImportReport(
  apiUrl: string,
  jobId: string,
  signal?: AbortSignal,
): Promise<ImportReportResponse> {
  const {status, body} = await getEnvelope(`${apiUrl}/api/v1/imports/${jobId}/report`, signal);
  return {status, body: body as ApiResponse<ImportReport | null>};
}

export async function listImports(
  apiUrl: string,
  page = 0,
  size = 10,
  signal?: AbortSignal,
): Promise<ImportListResponse> {
  const {status, body} = await getEnvelope(
    `${apiUrl}/api/v1/imports?page=${page}&size=${size}`,
    signal,
  );
  return {status, body: body as ApiResponse<PageResponse<ImportJobStatus> | null>};
}
