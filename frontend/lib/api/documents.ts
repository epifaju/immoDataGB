import {fetchJson} from './http';
import type {
  ApiResponse,
  DocumentDeleteResult,
  DocumentItem,
  DocumentType,
  DocumentUploadResult,
  PropertyDocumentsResult,
  SignedUrlResponse,
  SignedUrlResult,
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

export async function fetchPropertyDocuments(
  apiUrl: string,
  propertyId: string,
  signal?: AbortSignal,
): Promise<PropertyDocumentsResult> {
  const {status, body} = await fetchJson<unknown>(
    `${apiUrl}/api/v1/properties/${propertyId}/documents`,
    {
      headers: {Accept: 'application/json'},
      cache: 'no-store',
      signal,
    },
  );
  if (!isEnvelope(body)) {
    throw new Error('NOT_JSON');
  }
  return {status, body: body as ApiResponse<DocumentItem[] | null>};
}

export async function uploadPropertyDocument(
  apiUrl: string,
  propertyId: string,
  file: File,
  documentType: DocumentType,
): Promise<DocumentUploadResult> {
  const form = new FormData();
  form.append('file', file);
  form.append('documentType', documentType);
  const {status, body} = await fetchJson<unknown>(
    `${apiUrl}/api/v1/properties/${propertyId}/documents`,
    {
      method: 'POST',
      headers: {Accept: 'application/json'},
      body: form,
      cache: 'no-store',
    },
  );
  if (!isEnvelope(body)) {
    throw new Error('NOT_JSON');
  }
  return {status, body: body as ApiResponse<DocumentItem | null>};
}

export async function requestDocumentSignedUrl(
  apiUrl: string,
  documentId: string,
): Promise<SignedUrlResponse> {
  const {status, body} = await fetchJson<unknown>(
    `${apiUrl}/api/v1/documents/${documentId}/signed-url`,
    {
      method: 'POST',
      headers: {Accept: 'application/json'},
      cache: 'no-store',
    },
  );
  if (!isEnvelope(body)) {
    throw new Error('NOT_JSON');
  }
  return {status, body: body as ApiResponse<SignedUrlResult | null>};
}

export async function deleteDocument(
  apiUrl: string,
  documentId: string,
): Promise<DocumentDeleteResult> {
  const {status, body} = await fetchJson<unknown>(`${apiUrl}/api/v1/documents/${documentId}`, {
    method: 'DELETE',
    headers: {Accept: 'application/json'},
    cache: 'no-store',
  });
  if (!isEnvelope(body)) {
    throw new Error('NOT_JSON');
  }
  return {status, body: body as ApiResponse<null>};
}
