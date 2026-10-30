import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {render, screen} from '@testing-library/react';
import {NextIntlClientProvider} from 'next-intl';
import {afterEach, expect, test, vi} from 'vitest';
import ptCommon from '../../messages/pt/common.json';
import ptErrors from '../../messages/pt/errors.json';
import {HealthStatus} from './HealthStatus';

const messages = {common: ptCommon, errors: ptErrors};

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

function renderStatus() {
  const client = new QueryClient({
    defaultOptions: {queries: {retry: false}},
  });
  return render(
    <NextIntlClientProvider locale="pt" messages={messages}>
      <QueryClientProvider client={client}>
        <HealthStatus />
      </QueryClientProvider>
    </NextIntlClientProvider>,
  );
}

test('shows the loading text while the request is pending', () => {
  vi.stubGlobal('fetch', () => new Promise(() => {}));
  renderStatus();
  expect(screen.getByText('A carregar…')).toBeTruthy();
});

test('shows the PostGIS version returned by the API', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => new Response(JSON.stringify({
      success: true,
      data: {api: 'up', database: 'up', postgis: '3.4 USE_GEOS=1'},
      message: null,
      meta: {},
    }), {status: 200, headers: {'Content-Type': 'application/json'}})),
  );
  renderStatus();
  expect(await screen.findByText('3.4 USE_GEOS=1')).toBeTruthy();
  expect(screen.getByText('Estado da plataforma')).toBeTruthy();
  expect(screen.getAllByText('operacional').length).toBeGreaterThan(0);
});

test('shows an unknown status value as returned', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => new Response(JSON.stringify({
      success: true,
      data: {api: 'weird', database: 'up', postgis: '3.4'},
      message: null,
      meta: {},
    }), {status: 200, headers: {'Content-Type': 'application/json'}})),
  );
  renderStatus();
  expect(await screen.findByText('weird')).toBeTruthy();
});

test('shows the database-unavailable sentence and a dash', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => new Response(JSON.stringify({
      success: false,
      data: {api: 'up', database: 'down', postgis: null},
      message: 'HEALTH_DATABASE_UNAVAILABLE',
      meta: {},
    }), {status: 503, headers: {'Content-Type': 'application/json'}})),
  );
  renderStatus();
  expect(await screen.findByText('A base de dados PostGIS não está acessível.')).toBeTruthy();
  expect(screen.getByText('indisponível')).toBeTruthy();
  expect(screen.getByText('—')).toBeTruthy();
});

test('shows the internal error sentence', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => new Response(JSON.stringify({
      success: false,
      data: null,
      message: 'INTERNAL_ERROR',
      meta: {},
    }), {status: 500, headers: {'Content-Type': 'application/json'}})),
  );
  renderStatus();
  expect(await screen.findByText('Ocorreu um erro interno.')).toBeTruthy();
  expect(screen.queryByText('Estado da plataforma')).toBeNull();
});

test('shows the generic error when the network fails', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => {
    throw new Error('offline');
  }));
  renderStatus();
  expect(await screen.findByText('Não foi possível contactar a API.')).toBeTruthy();
});

test('shows the generic error when the body is not JSON', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => new Response('not-json', {status: 200, headers: {'Content-Type': 'text/plain'}})),
  );
  renderStatus();
  expect(await screen.findByText('Não foi possível contactar a API.')).toBeTruthy();
});
