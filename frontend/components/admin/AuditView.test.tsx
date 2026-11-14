import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {NextIntlClientProvider} from 'next-intl';
import {afterEach, beforeEach, describe, expect, test, vi} from 'vitest';
import ptAdmin from '@/messages/pt/admin.json';
import ptErrors from '@/messages/pt/errors.json';
import {AuditView} from './AuditView';

const event = {
  id: '22222222-2222-2222-2222-222222222222',
  entityType: 'property',
  entityId: '11111111-1111-1111-1111-111111111111',
  action: 'validate',
  oldValue: '{"validationStatus":"pending_validation"}',
  newValue: '{"validationStatus":"validated"}',
  summary: 'Bem validado (FLD-20261114-abcd1234).',
  createdAt: '2026-11-14T12:00:00Z',
};

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {'Content-Type': 'application/json'},
  });
}

function envelope(data: unknown) {
  return {success: true, data, message: null, meta: {}};
}

function renderAudit() {
  const client = new QueryClient({
    defaultOptions: {queries: {retry: false}, mutations: {retry: false}},
  });
  return render(
    <NextIntlClientProvider locale="pt" messages={{admin: ptAdmin, errors: ptErrors}}>
      <QueryClientProvider client={client}>
        <AuditView />
      </QueryClientProvider>
    </NextIntlClientProvider>,
  );
}

function requestUrls(fetchMock: ReturnType<typeof vi.fn>): string[] {
  return fetchMock.mock.calls.map((call) => String(call[0]));
}

beforeEach(() => {
  vi.stubEnv('NEXT_PUBLIC_API_URL', 'http://localhost:8080');
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
  vi.restoreAllMocks();
});

describe('AuditView', () => {
  test('loads the log and sends filters only after submit', async () => {
    const fetchMock = vi.fn(async () =>
      jsonResponse(envelope({page: 0, size: 20, totalElements: 1, totalPages: 1, items: [event]})),
    );
    vi.stubGlobal('fetch', fetchMock);
    renderAudit();

    expect(await screen.findByText(event.summary)).toBeTruthy();
    expect(screen.getByRole('cell', {name: ptAdmin.audit.actions.validate})).toBeTruthy();
    const initial = requestUrls(fetchMock).map((url) => new URL(url));
    expect(initial.some((url) => url.pathname === '/api/v1/admin/audit')).toBe(true);
    expect(initial.some((url) => url.searchParams.has('entityType'))).toBe(false);
    expect(initial[0]?.searchParams.get('page')).toBe('0');
    expect(initial[0]?.searchParams.get('size')).toBe('20');

    fireEvent.change(screen.getByLabelText(ptAdmin.audit.filters.entityType), {target: {value: 'property'}});
    fireEvent.change(screen.getByLabelText(ptAdmin.audit.filters.action), {target: {value: 'validate'}});
    fireEvent.change(screen.getByLabelText(ptAdmin.audit.filters.from), {
      target: {value: '2026-11-01T00:00:00Z'},
    });
    fireEvent.change(screen.getByLabelText(ptAdmin.audit.filters.to), {
      target: {value: '2026-11-14T23:59:59Z'},
    });
    expect(requestUrls(fetchMock).some((url) => new URL(url).searchParams.has('entityType'))).toBe(false);

    fireEvent.click(screen.getByRole('button', {name: ptAdmin.audit.filters.apply}));

    await waitFor(() => {
      const matched = requestUrls(fetchMock).some((url) => {
        const parsed = new URL(url);
        const params = parsed.searchParams;
        return (
          parsed.pathname === '/api/v1/admin/audit' &&
          params.get('entityType') === 'property' &&
          params.get('action') === 'validate' &&
          params.get('from') === '2026-11-01T00:00:00Z' &&
          params.get('to') === '2026-11-14T23:59:59Z' &&
          params.get('page') === '0' &&
          params.get('size') === '20'
        );
      });
      expect(matched).toBe(true);
    });
  });
});
