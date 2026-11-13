import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {render, screen, waitFor} from '@testing-library/react';
import {NextIntlClientProvider} from 'next-intl';
import {afterEach, beforeEach, describe, expect, test, vi} from 'vitest';
import ptCommon from '@/messages/pt/common.json';
import ptErrors from '@/messages/pt/errors.json';
import ptMap from '@/messages/pt/map.json';
import ptProperty from '@/messages/pt/property.json';
import {PropertyDetailView} from './PropertyDetailView';

const searchParamsState = {value: new URLSearchParams()};

vi.mock('next/navigation', () => ({
  usePathname: () => '/properties/1',
  useRouter: () => ({push: vi.fn(), replace: vi.fn(), prefetch: vi.fn()}),
  useSearchParams: () => searchParamsState.value,
}));

const messages = {
  common: ptCommon,
  errors: ptErrors,
  map: ptMap,
  property: ptProperty,
};

const detailOk = {
  success: true,
  data: {
    id: '00000000-0000-0000-0001-000000000005',
    organisationId: 'org',
    reference: 'BIS-005',
    title: 'Apartamento Centro',
    propertyType: 'apartment',
    confidenceScore: 70,
    confidenceLevel: 'high',
    confidenceSummary: 'Várias fontes',
    longitude: -15.6,
    latitude: 11.86,
    docStatus: 'partial',
    addressText: null,
  },
  message: null,
  meta: {},
};

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {'Content-Type': 'application/json'},
  });
}

function renderView(id = '00000000-0000-0000-0001-000000000005') {
  const client = new QueryClient({
    defaultOptions: {queries: {retry: false}},
  });
  return render(
    <NextIntlClientProvider locale="pt" messages={messages}>
      <QueryClientProvider client={client}>
        <PropertyDetailView id={id} />
      </QueryClientProvider>
    </NextIntlClientProvider>,
  );
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  searchParamsState.value = new URLSearchParams();
});

beforeEach(() => {
  searchParamsState.value = new URLSearchParams();
});

describe('PropertyDetailView', () => {
  test('shows loading skeleton while detail pending', () => {
    vi.stubGlobal('fetch', () => new Promise(() => {}));
    const {container} = renderView();
    expect(container.querySelector('.animate-pulse')).toBeTruthy();
  });

  test('shows notFound for 404 PROPERTY_NOT_FOUND', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        jsonResponse(
          {success: false, data: null, message: 'PROPERTY_NOT_FOUND', meta: {}},
          404,
        ),
      ),
    );
    renderView();
    expect(await screen.findByText('Imóvel não encontrado.')).toBeTruthy();
    expect(screen.getByRole('link', {name: 'Voltar ao mapa'}).getAttribute('href')).toBe(
      '/pt/map',
    );
  });

  test('shows notFound for PROPERTY_ID_INVALID', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        jsonResponse(
          {success: false, data: null, message: 'PROPERTY_ID_INVALID', meta: {}},
          400,
        ),
      ),
    );
    renderView('not-a-uuid');
    expect(await screen.findByText('Imóvel não encontrado.')).toBeTruthy();
  });

  test('hides blank address and shows empty sources', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        if (String(url).endsWith('/sources')) {
          return jsonResponse({success: true, data: [], message: null, meta: {}});
        }
        return jsonResponse({
          ...detailOk,
          data: {...detailOk.data, addressText: '   '},
        });
      }),
    );
    renderView();
    expect(await screen.findByText('Apartamento Centro')).toBeTruthy();
    expect(screen.queryByText(/Endereço/)).toBeNull();
    expect(await screen.findByText('Nenhuma fonte registada.')).toBeTruthy();
  });

  test('keeps detail visible when sources fail locally', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        if (String(url).endsWith('/sources')) {
          return jsonResponse(
            {success: false, data: null, message: 'INTERNAL_ERROR', meta: {}},
            500,
          );
        }
        return jsonResponse(detailOk);
      }),
    );
    renderView();
    expect(await screen.findByText('Apartamento Centro')).toBeTruthy();
    expect(await screen.findByText('Ocorreu um erro interno.')).toBeTruthy();
  });

  test('back link restores map query when present', async () => {
    searchParamsState.value = new URLSearchParams(
      'bbox=-15.70000,11.80000,-15.50000,11.95000&type=house&confidence=high&page=2',
    );
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        if (String(url).endsWith('/sources')) {
          return jsonResponse({success: true, data: [], message: null, meta: {}});
        }
        return jsonResponse(detailOk);
      }),
    );
    renderView();
    await screen.findByText('Apartamento Centro');
    const href = screen.getByRole('link', {name: 'Voltar ao mapa'}).getAttribute('href');
    expect(href).toContain('/pt/map?');
    expect(href).toContain('bbox=-15.70000');
    expect(href).toContain('type=house');
    expect(href).toContain('confidence=high');
    expect(href).toContain('page=2');
  });

  test('ignores invalid map params on back link', async () => {
    searchParamsState.value = new URLSearchParams(
      'bbox=-15.70000,11.80000,-15.50000,11.95000&type=spaceship&page=-3',
    );
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        if (String(url).endsWith('/sources')) {
          return jsonResponse({success: true, data: [], message: null, meta: {}});
        }
        return jsonResponse(detailOk);
      }),
    );
    renderView();
    await screen.findByText('Apartamento Centro');
    const href = screen.getByRole('link', {name: 'Voltar ao mapa'}).getAttribute('href');
    expect(href).toContain('bbox=-15.70000');
    expect(href).not.toContain('type=');
    expect(href).not.toContain('page=');
  });

  test('shows documents section with upload only when mutations enabled', async () => {
    const documentsOk = {
      success: true,
      data: [
        {
          id: 'd1',
          propertyId: detailOk.data.id,
          documentType: 'contrat_vente',
          originalFilename: 'contrato.pdf',
          contentType: 'application/pdf',
          sizeBytes: 1024,
          createdAt: '2026-03-04T10:00:00Z',
        },
      ],
      message: null,
      meta: {},
    };
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        if (String(url).endsWith('/documents')) {
          return jsonResponse(documentsOk);
        }
        if (String(url).endsWith('/sources')) {
          return jsonResponse({success: true, data: [], message: null, meta: {}});
        }
        return jsonResponse(detailOk);
      }),
    );
    vi.stubEnv('NEXT_PUBLIC_DOCUMENTS_MUTATIONS_ENABLED', 'true');
    const {unmount} = renderView();
    expect(await screen.findByText('contrato.pdf')).toBeTruthy();
    expect(screen.getByRole('heading', {name: 'Documentos'})).toBeTruthy();
    expect(screen.getByRole('button', {name: 'Carregar'})).toBeTruthy();
    unmount();

    vi.stubEnv('NEXT_PUBLIC_DOCUMENTS_MUTATIONS_ENABLED', 'false');
    renderView();
    expect(await screen.findByText('contrato.pdf')).toBeTruthy();
    expect(screen.queryByRole('button', {name: 'Carregar'})).toBeNull();
    vi.unstubAllEnvs();
  });

  test('does not fetch sources until detail succeeds', async () => {
    const fetchMock = vi.fn(async () =>
      jsonResponse(
        {success: false, data: null, message: 'PROPERTY_NOT_FOUND', meta: {}},
        404,
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    renderView();
    await screen.findByText('Imóvel não encontrado.');
    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    expect(fetchMock.mock.calls.every((call) => !String(call[0]).endsWith('/sources'))).toBe(
      true,
    );
  });
});
