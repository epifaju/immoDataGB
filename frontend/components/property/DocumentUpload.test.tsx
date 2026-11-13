import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {NextIntlClientProvider} from 'next-intl';
import {afterEach, describe, expect, test, vi} from 'vitest';
import ptCommon from '@/messages/pt/common.json';
import ptErrors from '@/messages/pt/errors.json';
import ptProperty from '@/messages/pt/property.json';
import {DocumentUpload} from './DocumentUpload';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {'Content-Type': 'application/json'},
  });
}

function renderUpload() {
  const client = new QueryClient({defaultOptions: {queries: {retry: false}}});
  const invalidate = vi.spyOn(client, 'invalidateQueries');
  render(
    <NextIntlClientProvider
      locale="pt"
      messages={{common: ptCommon, errors: ptErrors, property: ptProperty}}
    >
      <QueryClientProvider client={client}>
        <DocumentUpload propertyId="p1" apiUrl="http://api" />
      </QueryClientProvider>
    </NextIntlClientProvider>,
  );
  return {invalidate};
}

function pickFile(name = 'titulo.pdf') {
  const file = new File(['x'], name, {type: 'application/pdf'});
  fireEvent.change(screen.getByLabelText('Ficheiro'), {target: {files: [file]}});
  return file;
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('DocumentUpload', () => {
  test('lists the 10 document types', () => {
    renderUpload();
    expect(screen.getByLabelText('Tipo de documento').querySelectorAll('option').length).toBe(10);
  });

  test('requires a file before upload', () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    renderUpload();
    fireEvent.click(screen.getByRole('button', {name: 'Carregar'}));
    expect(screen.getByRole('alert').textContent).toBe('Selecione um ficheiro.');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  test('rejects files over 50 MiB client-side without calling the API', () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    renderUpload();
    const big = new File(['x'], 'grande.pdf', {type: 'application/pdf'});
    Object.defineProperty(big, 'size', {value: 52_428_801});
    fireEvent.change(screen.getByLabelText('Ficheiro'), {target: {files: [big]}});
    fireEvent.click(screen.getByRole('button', {name: 'Carregar'}));
    expect(screen.getByRole('alert').textContent).toBe(ptErrors.DOCUMENT_FILE_TOO_LARGE);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  test('posts FormData with file and documentType then invalidates', async () => {
    const fetchMock = vi.fn(async () =>
      jsonResponse(
        {success: true, data: {id: 'd1'}, message: null, meta: {}},
        201,
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    const {invalidate} = renderUpload();
    const file = pickFile();
    fireEvent.change(screen.getByLabelText('Tipo de documento'), {
      target: {value: 'contrat_vente'},
    });
    fireEvent.click(screen.getByRole('button', {name: 'Carregar'}));
    expect(await screen.findByText('Documento carregado.')).toBeTruthy();
    const call = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(call[0]).toBe('http://api/api/v1/properties/p1/documents');
    expect(call[1].method).toBe('POST');
    const form = call[1].body as FormData;
    expect((form.get('file') as File).name).toBe(file.name);
    expect(form.get('documentType')).toBe('contrat_vente');
    expect(invalidate).toHaveBeenCalledWith({queryKey: ['property-documents', 'p1']});
  });

  test('shows translated backend error', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        jsonResponse(
          {success: false, data: null, message: 'DOCUMENT_FILE_TYPE_REJECTED', meta: {}},
          400,
        ),
      ),
    );
    renderUpload();
    pickFile();
    fireEvent.click(screen.getByRole('button', {name: 'Carregar'}));
    await waitFor(() =>
      expect(screen.getByRole('alert').textContent).toBe('Tipo de ficheiro não permitido.'),
    );
  });
});
