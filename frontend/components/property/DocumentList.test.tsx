import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {NextIntlClientProvider} from 'next-intl';
import {afterEach, describe, expect, test, vi} from 'vitest';
import type {DocumentItem} from '@/lib/api/types';
import ptCommon from '@/messages/pt/common.json';
import ptErrors from '@/messages/pt/errors.json';
import ptProperty from '@/messages/pt/property.json';
import {DocumentList} from './DocumentList';

const doc: DocumentItem = {
  id: 'd1',
  propertyId: 'p1',
  documentType: 'titre_foncier',
  originalFilename: 'titulo.pdf',
  contentType: 'application/pdf',
  sizeBytes: 2048,
  createdAt: '2026-03-04T10:00:00Z',
};

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {'Content-Type': 'application/json'},
  });
}

function renderList(documents: DocumentItem[], mutationsEnabled: boolean) {
  const client = new QueryClient({defaultOptions: {queries: {retry: false}}});
  const invalidate = vi.spyOn(client, 'invalidateQueries');
  render(
    <NextIntlClientProvider
      locale="pt"
      messages={{common: ptCommon, errors: ptErrors, property: ptProperty}}
    >
      <QueryClientProvider client={client}>
        <DocumentList
          documents={documents}
          propertyId="p1"
          apiUrl="http://api"
          mutationsEnabled={mutationsEnabled}
        />
      </QueryClientProvider>
    </NextIntlClientProvider>,
  );
  return {invalidate};
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('DocumentList', () => {
  test('shows empty state', () => {
    renderList([], true);
    expect(screen.getByText('Nenhum documento registado.')).toBeTruthy();
  });

  test('shows filename and translated type, falls back to raw type', () => {
    renderList(
      [doc, {...doc, id: 'd2', originalFilename: 'outro.pdf', documentType: 'custom'}],
      false,
    );
    expect(screen.getByText('titulo.pdf')).toBeTruthy();
    expect(screen.getByText('Título fundiário')).toBeTruthy();
    expect(screen.getByText('custom')).toBeTruthy();
    expect(screen.getAllByText('Tamanho: 2 KB').length).toBe(2);
  });

  test('hides actions when mutations disabled', () => {
    renderList([doc], false);
    expect(screen.queryByRole('button', {name: 'Descarregar'})).toBeNull();
    expect(screen.queryByRole('button', {name: 'Eliminar'})).toBeNull();
  });

  test('download requests signed url then opens it safely', async () => {
    const fetchMock = vi.fn(async (_url: string) =>
      jsonResponse({
        success: true,
        data: {url: 'https://minio/signed', expiresAt: '2026-03-04T10:15:00Z'},
        message: null,
        meta: {},
      }),
    );
    vi.stubGlobal('fetch', fetchMock);
    const open = vi.spyOn(window, 'open').mockReturnValue(null);
    renderList([doc], true);
    fireEvent.click(screen.getByRole('button', {name: 'Descarregar'}));
    await waitFor(() =>
      expect(open).toHaveBeenCalledWith('https://minio/signed', '_blank', 'noopener,noreferrer'),
    );
    expect(fetchMock.mock.calls[0]?.[0]).toBe('http://api/api/v1/documents/d1/signed-url');
  });

  test('download shows translated error', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        jsonResponse(
          {success: false, data: null, message: 'DOCUMENT_STORAGE_UNAVAILABLE', meta: {}},
          503,
        ),
      ),
    );
    const open = vi.spyOn(window, 'open').mockReturnValue(null);
    renderList([doc], true);
    fireEvent.click(screen.getByRole('button', {name: 'Descarregar'}));
    expect(
      await screen.findByText('O armazenamento de documentos está indisponível.'),
    ).toBeTruthy();
    expect(open).not.toHaveBeenCalled();
  });

  test('delete does nothing when confirm is cancelled', () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    renderList([doc], true);
    fireEvent.click(screen.getByRole('button', {name: 'Eliminar'}));
    expect(window.confirm).toHaveBeenCalledWith('Eliminar este documento?');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  test('delete confirmed calls DELETE and invalidates documents query', async () => {
    const fetchMock = vi.fn(async () =>
      jsonResponse({success: true, data: null, message: null, meta: {}}),
    );
    vi.stubGlobal('fetch', fetchMock);
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const {invalidate} = renderList([doc], true);
    fireEvent.click(screen.getByRole('button', {name: 'Eliminar'}));
    await waitFor(() =>
      expect(invalidate).toHaveBeenCalledWith({queryKey: ['property-documents', 'p1']}),
    );
    const call = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(call[0]).toBe('http://api/api/v1/documents/d1');
    expect(call[1].method).toBe('DELETE');
  });
});
