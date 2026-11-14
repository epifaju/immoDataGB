import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {render, screen, waitFor} from '@testing-library/react';
import {NextIntlClientProvider} from 'next-intl';
import {afterEach, describe, expect, test, vi} from 'vitest';
import ptCommon from '@/messages/pt/common.json';
import ptErrors from '@/messages/pt/errors.json';
import ptImports from '@/messages/pt/imports.json';
import {ImportJobPanel} from './ImportJobPanel';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {'Content-Type': 'application/json'},
  });
}

const baseJob = {
  jobId: 'j1',
  originalFilename: 'sample.csv',
  totalRows: 3,
  successRows: 1,
  errorRows: 2,
  errorMessage: null,
  createdAt: '2026-11-14T10:00:00Z',
};

function envelope(data: unknown) {
  return {success: true, data, message: null, meta: {}};
}

function renderPanel() {
  const client = new QueryClient({defaultOptions: {queries: {retry: false}}});
  render(
    <NextIntlClientProvider
      locale="pt"
      messages={{common: ptCommon, errors: ptErrors, imports: ptImports}}
    >
      <QueryClientProvider client={client}>
        <ImportJobPanel apiUrl="http://api" jobId="j1" pollIntervalMs={20} />
      </QueryClientProvider>
    </NextIntlClientProvider>,
  );
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('ImportJobPanel', () => {
  test('shows counters and translated row errors from the report once completed', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        if (String(url).endsWith('/report')) {
          return jsonResponse(
            envelope({
              ...baseJob,
              status: 'completed',
              errors: [
                {
                  rowNumber: 2,
                  code: 'IMPORT_INVALID_PROPERTY_TYPE',
                  message: 'Tipo inválido',
                  rawExcerpt: 'X-1,Casa,villa,,11.8,-15.6',
                },
                {
                  rowNumber: 3,
                  code: 'IMPORT_DUPLICATE_REFERENCE',
                  message: 'Duplicada',
                  rawExcerpt: null,
                },
              ],
            }),
          );
        }
        return jsonResponse(envelope({...baseJob, status: 'completed'}));
      }),
    );
    renderPanel();
    expect(await screen.findByText('Tipo de bem inválido.')).toBeTruthy();
    expect(screen.getByText('Referência já existente.')).toBeTruthy();
    expect(screen.getByText('sample.csv')).toBeTruthy();
    expect(screen.getByTestId('import-status').textContent).toBe('Concluída');
    expect(screen.getByRole('columnheader', {name: 'Linha'})).toBeTruthy();
    expect(screen.getAllByRole('row').length).toBe(3);
  });

  test('polls status until terminal and fetches the report only then', async () => {
    let statusCalls = 0;
    const fetchMock = vi.fn(async (url: string) => {
      if (String(url).endsWith('/report')) {
        return jsonResponse(envelope({...baseJob, status: 'completed', errors: []}));
      }
      statusCalls += 1;
      return jsonResponse(
        envelope({...baseJob, status: statusCalls < 3 ? 'processing' : 'completed'}),
      );
    });
    vi.stubGlobal('fetch', fetchMock);
    renderPanel();
    expect(await screen.findByText('A importação está em curso…')).toBeTruthy();
    expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/report'))).toBe(false);
    expect(await screen.findByText('Nenhum erro de linha.')).toBeTruthy();
    expect(statusCalls).toBeGreaterThanOrEqual(3);
    const callsAtTerminal = statusCalls;
    await new Promise((resolve) => setTimeout(resolve, 80));
    expect(statusCalls).toBe(callsAtTerminal);
  });

  test('shows the failure message for a failed job', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        const failed = {...baseJob, status: 'failed', errorMessage: 'boom'};
        return jsonResponse(
          envelope(String(url).endsWith('/report') ? {...failed, errors: []} : failed),
        );
      }),
    );
    renderPanel();
    await waitFor(() =>
      expect(screen.getByRole('alert').textContent).toBe('A importação falhou. boom'),
    );
  });

  test('shows a translated error when the job is not found', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        jsonResponse({success: false, data: null, message: 'IMPORT_JOB_NOT_FOUND', meta: {}}, 404),
      ),
    );
    renderPanel();
    await waitFor(() =>
      expect(screen.getByRole('alert').textContent).toBe('Importação não encontrada.'),
    );
  });
});
