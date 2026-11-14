import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {NextIntlClientProvider} from 'next-intl';
import {afterEach, describe, expect, test, vi} from 'vitest';
import ptCommon from '@/messages/pt/common.json';
import ptErrors from '@/messages/pt/errors.json';
import ptImports from '@/messages/pt/imports.json';
import {ImportsView} from './ImportsView';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {'Content-Type': 'application/json'},
  });
}

const job = {
  jobId: 'j1',
  status: 'completed',
  originalFilename: 'sample.csv',
  totalRows: 3,
  successRows: 3,
  errorRows: 0,
  errorMessage: null,
  createdAt: '2026-11-14T10:00:00Z',
};

function envelope(data: unknown) {
  return {success: true, data, message: null, meta: {}};
}

function renderView() {
  const client = new QueryClient({defaultOptions: {queries: {retry: false}}});
  return render(
    <NextIntlClientProvider
      locale="pt"
      messages={{common: ptCommon, errors: ptErrors, imports: ptImports}}
    >
      <QueryClientProvider client={client}>
        <ImportsView />
      </QueryClientProvider>
    </NextIntlClientProvider>,
  );
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
  vi.restoreAllMocks();
});

describe('ImportsView', () => {
  test('shows the upload form only when the UI flag is true', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse(envelope({page: 0, size: 10, totalElements: 0, totalPages: 0, items: []}))),
    );
    vi.stubEnv('NEXT_PUBLIC_IMPORTS_MUTATIONS_ENABLED', 'true');
    const {unmount} = renderView();
    expect(screen.getByRole('button', {name: 'Importar'})).toBeTruthy();
    expect(screen.getByLabelText('Ficheiro CSV')).toBeTruthy();
    unmount();

    vi.stubEnv('NEXT_PUBLIC_IMPORTS_MUTATIONS_ENABLED', 'false');
    renderView();
    expect(screen.queryByRole('button', {name: 'Importar'})).toBeNull();
    expect(screen.queryByLabelText('Ficheiro CSV')).toBeNull();
  });

  test('requires a file and does not call the API without one', () => {
    const fetchMock = vi.fn(async (_url: string, _init?: RequestInit) =>
      jsonResponse(envelope({page: 0, size: 10, totalElements: 0, totalPages: 0, items: []})),
    );
    vi.stubGlobal('fetch', fetchMock);
    vi.stubEnv('NEXT_PUBLIC_IMPORTS_MUTATIONS_ENABLED', 'true');
    renderView();
    fireEvent.click(screen.getByRole('button', {name: 'Importar'}));
    expect(screen.getByRole('alert').textContent).toBe('Selecione um ficheiro.');
    expect(fetchMock.mock.calls.some(([, init]) => (init as RequestInit | undefined)?.method === 'POST')).toBe(false);
  });

  test('uploads the CSV as multipart then shows the job panel', async () => {
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      const target = String(url);
      if (init?.method === 'POST') {
        return jsonResponse(envelope({jobId: 'j1', status: 'queued'}), 202);
      }
      if (target.endsWith('/report')) {
        return jsonResponse(envelope({...job, errors: []}));
      }
      if (target.endsWith('/status')) {
        return jsonResponse(envelope(job));
      }
      return jsonResponse(envelope({page: 0, size: 10, totalElements: 0, totalPages: 0, items: []}));
    });
    vi.stubGlobal('fetch', fetchMock);
    vi.stubEnv('NEXT_PUBLIC_IMPORTS_MUTATIONS_ENABLED', 'true');
    renderView();
    const file = new File(['reference,title'], 'sample.csv', {type: 'text/csv'});
    fireEvent.change(screen.getByLabelText('Ficheiro CSV'), {target: {files: [file]}});
    fireEvent.click(screen.getByRole('button', {name: 'Importar'}));
    expect(await screen.findByText('Nenhum erro de linha.')).toBeTruthy();
    const post = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST');
    expect(post?.[0]).toBe('http://localhost:8080/api/v1/imports');
    expect(((post?.[1] as RequestInit).body as FormData).get('file')).toBeInstanceOf(File);
  });

  test('shows translated backend error on upload', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (_url: string, init?: RequestInit) =>
        init?.method === 'POST'
          ? jsonResponse({success: false, data: null, message: 'IMPORT_INVALID_HEADER', meta: {}}, 400)
          : jsonResponse(envelope({page: 0, size: 10, totalElements: 0, totalPages: 0, items: []})),
      ),
    );
    vi.stubEnv('NEXT_PUBLIC_IMPORTS_MUTATIONS_ENABLED', 'true');
    renderView();
    fireEvent.change(screen.getByLabelText('Ficheiro CSV'), {
      target: {files: [new File(['x'], 'bad.csv', {type: 'text/csv'})]},
    });
    fireEvent.click(screen.getByRole('button', {name: 'Importar'}));
    await waitFor(() =>
      expect(screen.getByRole('alert').textContent).toBe('Cabeçalho CSV inválido.'),
    );
  });
});
