import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {NextIntlClientProvider} from 'next-intl';
import {afterEach, describe, expect, test, vi} from 'vitest';
import ptCommon from '@/messages/pt/common.json';
import ptErrors from '@/messages/pt/errors.json';
import ptMap from '@/messages/pt/map.json';
import ptValuation from '@/messages/pt/valuation.json';
import {ValuationForm} from './ValuationForm';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {'Content-Type': 'application/json'},
  });
}

function renderForm() {
  const client = new QueryClient({defaultOptions: {queries: {retry: false}}});
  render(
    <NextIntlClientProvider
      locale="pt"
      messages={{common: ptCommon, errors: ptErrors, map: ptMap, valuation: ptValuation}}
    >
      <QueryClientProvider client={client}>
        <ValuationForm apiUrl="http://api" />
      </QueryClientProvider>
    </NextIntlClientProvider>,
  );
}

const RESULT = {
  currency: 'XOF',
  medianTotal: 12_000_000,
  rangeLow: 10_000_000,
  rangeHigh: 14_000_000,
  medianPricePerSqm: 150_000,
  rangeLowPricePerSqm: 125_000,
  rangeHighPricePerSqm: 175_000,
  comparablesCount: 3,
  comparablesRawCount: 3,
  confidenceScore: 45,
  confidenceLevel: 'medium',
  warning: 'Amostra insuficiente (n=3). A estimativa é indicativa; recomenda-se prudência.',
  comparables: [],
};

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('ValuationForm', () => {
  test('renders the spec defaults', () => {
    renderForm();
    expect((screen.getByLabelText('Latitude') as HTMLInputElement).value).toBe('11.863');
    expect((screen.getByLabelText('Longitude') as HTMLInputElement).value).toBe('-15.598');
    expect((screen.getByLabelText('Raio (m)') as HTMLInputElement).value).toBe('3000');
    expect((screen.getByLabelText('Período (meses)') as HTMLInputElement).value).toBe('24');
    expect((screen.getByLabelText('Área (m²)') as HTMLInputElement).value).toBe('80');
    expect((screen.getByLabelText('Tipo de imóvel') as HTMLSelectElement).value).toBe('apartment');
  });

  test('rejects invalid values client-side without calling the API', () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    renderForm();
    fireEvent.change(screen.getByLabelText('Área (m²)'), {target: {value: '0'}});
    fireEvent.change(screen.getByLabelText('Raio (m)'), {target: {value: '50'}});
    fireEvent.click(screen.getByRole('button', {name: 'Estimar'}));
    const alerts = screen.getAllByRole('alert').map((node) => node.textContent);
    expect(alerts).toContain(ptValuation.validation.areaSqm);
    expect(alerts).toContain(ptValuation.validation.radiusMeters);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  test('posts the estimate request and shows range and warning', async () => {
    const fetchMock = vi.fn(async () =>
      jsonResponse({success: true, data: RESULT, message: null, meta: {}}),
    );
    vi.stubGlobal('fetch', fetchMock);
    renderForm();
    fireEvent.click(screen.getByRole('button', {name: 'Estimar'}));
    expect(await screen.findByTestId('valuation-range')).toBeTruthy();
    const call = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(call[0]).toBe('http://api/api/v1/valuations/estimate');
    expect(call[1].method).toBe('POST');
    expect(JSON.parse(call[1].body as string)).toEqual({
      propertyType: 'apartment',
      areaSqm: 80,
      latitude: 11.863,
      longitude: -15.598,
      radiusMeters: 3000,
      periodMonths: 24,
    });
    expect(screen.getByTestId('range-low')).toBeTruthy();
    expect(screen.getByTestId('range-high')).toBeTruthy();
    expect(screen.getByRole('alert').textContent).toContain('Amostra insuficiente (n=3)');
  });

  test('shows translated backend error and no result', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        jsonResponse(
          {success: false, data: null, message: 'VALUATION_NO_COMPARABLES', meta: {}},
          404,
        ),
      ),
    );
    renderForm();
    fireEvent.click(screen.getByRole('button', {name: 'Estimar'}));
    await waitFor(() =>
      expect(screen.getByRole('alert').textContent).toBe(ptErrors.VALUATION_NO_COMPARABLES),
    );
    expect(screen.queryByTestId('valuation-range')).toBeNull();
  });
});
