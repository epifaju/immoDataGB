import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {NextIntlClientProvider} from 'next-intl';
import {afterEach, beforeEach, describe, expect, test, vi} from 'vitest';
import ptAdmin from '@/messages/pt/admin.json';
import ptErrors from '@/messages/pt/errors.json';
import ptMap from '@/messages/pt/map.json';
import {ValidationQueueView} from './ValidationQueueView';

const item = {
  id: '11111111-1111-1111-1111-111111111111',
  reference: 'FLD-20261114-abcd1234',
  title: 'Casa piloto',
  propertyType: 'house',
  createdAt: '2026-11-14T12:00:00Z',
  latitude: 11.863,
  longitude: -15.598,
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

function queuePage() {
  return {page: 0, size: 20, totalElements: 1, totalPages: 1, items: [item]};
}

function renderQueue() {
  const client = new QueryClient({
    defaultOptions: {queries: {retry: false}, mutations: {retry: false}},
  });
  const view = render(
    <NextIntlClientProvider locale="pt" messages={{admin: ptAdmin, errors: ptErrors, map: ptMap}}>
      <QueryClientProvider client={client}>
        <ValidationQueueView />
      </QueryClientProvider>
    </NextIntlClientProvider>,
  );
  return {client, ...view};
}

beforeEach(() => {
  vi.stubEnv('NEXT_PUBLIC_API_URL', 'http://localhost:8080');
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
  vi.restoreAllMocks();
});

describe('ValidationQueueView', () => {
  test('shows validate and reject only when the mutations flag is true', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse(envelope(queuePage()))),
    );
    vi.stubEnv('NEXT_PUBLIC_ADMIN_MUTATIONS_ENABLED', 'true');
    const {unmount} = renderQueue();
    expect(
      await screen.findByRole('button', {name: `${ptAdmin.queue.actions.validate} ${item.reference}`}),
    ).toBeTruthy();
    expect(
      screen.getByRole('button', {name: `${ptAdmin.queue.actions.reject} ${item.reference}`}),
    ).toBeTruthy();
    unmount();

    vi.stubEnv('NEXT_PUBLIC_ADMIN_MUTATIONS_ENABLED', 'false');
    renderQueue();
    expect(await screen.findByText(item.reference)).toBeTruthy();
    expect(
      screen.queryByRole('button', {name: `${ptAdmin.queue.actions.validate} ${item.reference}`}),
    ).toBeNull();
    expect(
      screen.queryByRole('button', {name: `${ptAdmin.queue.actions.reject} ${item.reference}`}),
    ).toBeNull();
  });

  test('validates a property and invalidates the queue and the audit log', async () => {
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (init?.method === 'POST') {
        return jsonResponse(envelope(item));
      }
      return jsonResponse(envelope(queuePage()));
    });
    vi.stubGlobal('fetch', fetchMock);
    vi.stubEnv('NEXT_PUBLIC_ADMIN_MUTATIONS_ENABLED', 'true');
    const {client} = renderQueue();
    const invalidate = vi.spyOn(client, 'invalidateQueries');

    fireEvent.click(
      await screen.findByRole('button', {name: `${ptAdmin.queue.actions.validate} ${item.reference}`}),
    );

    await waitFor(() => expect(screen.getByRole('status').textContent).toBe(ptAdmin.queue.actions.validated));
    expect(invalidate).toHaveBeenCalledWith({queryKey: ['validation-queue']});
    expect(invalidate).toHaveBeenCalledWith({queryKey: ['audit']});
    const post = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST');
    expect(post?.[0]).toBe(`http://localhost:8080/api/v1/admin/properties/${item.id}/validate`);
  });

  test('rejects a property through the reject endpoint', async () => {
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (init?.method === 'POST') {
        return jsonResponse(envelope({id: item.id, deleted: true}));
      }
      return jsonResponse(envelope(queuePage()));
    });
    vi.stubGlobal('fetch', fetchMock);
    vi.stubEnv('NEXT_PUBLIC_ADMIN_MUTATIONS_ENABLED', 'true');
    renderQueue();

    fireEvent.click(
      await screen.findByRole('button', {name: `${ptAdmin.queue.actions.reject} ${item.reference}`}),
    );

    await waitFor(() => expect(screen.getByRole('status').textContent).toBe(ptAdmin.queue.actions.rejected));
    const post = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST');
    expect(post?.[0]).toBe(`http://localhost:8080/api/v1/admin/properties/${item.id}/reject`);
  });
});
