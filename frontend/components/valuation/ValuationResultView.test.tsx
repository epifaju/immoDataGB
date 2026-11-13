import {render, screen} from '@testing-library/react';
import {NextIntlClientProvider} from 'next-intl';
import {describe, expect, test} from 'vitest';
import ptMap from '@/messages/pt/map.json';
import ptValuation from '@/messages/pt/valuation.json';
import type {EstimateResult} from '@/lib/api/types';
import {formatXof} from '@/lib/format/currency';
import {ValuationResultView} from './ValuationResultView';

const BASE: EstimateResult = {
  currency: 'XOF',
  medianTotal: 12_000_000,
  rangeLow: 10_000_000,
  rangeHigh: 14_000_000,
  medianPricePerSqm: 150_000,
  rangeLowPricePerSqm: 125_000,
  rangeHighPricePerSqm: 175_000,
  comparablesCount: 7,
  comparablesRawCount: 9,
  confidenceScore: 100,
  confidenceLevel: 'high',
  warning: null,
  comparables: [
    {
      id: 'c1',
      propertyType: 'apartment',
      priceXof: 11_000_000,
      areaSqm: 80,
      pricePerSqm: 137_500,
      observedAt: '2026-06-01',
      distanceMeters: 420.5,
    },
  ],
};

function renderResult(result: EstimateResult) {
  return render(
    <NextIntlClientProvider locale="pt" messages={{map: ptMap, valuation: ptValuation}}>
      <ValuationResultView result={result} />
    </NextIntlClientProvider>,
  );
}

describe('ValuationResultView', () => {
  test('shows the dominant range low–high with the median as secondary', () => {
    renderResult(BASE);
    const range = screen.getByTestId('valuation-range');
    expect(screen.getByTestId('range-low').textContent).toBe(formatXof(10_000_000, 'pt'));
    expect(screen.getByTestId('range-high').textContent).toBe(formatXof(14_000_000, 'pt'));
    expect(range.textContent).toContain(' – ');
    expect(range.className).toContain('text-3xl');
    expect(screen.getByTestId('median-total').textContent).toBe(formatXof(12_000_000, 'pt'));
    expect(screen.getByTestId('median-total').closest('p')?.className).toContain('text-sm');
    expect(screen.getByText('Alta')).toBeTruthy();
    expect(screen.getByText('100/100')).toBeTruthy();
    expect(screen.getByText('Apartamento')).toBeTruthy();
    expect(screen.queryByRole('alert')).toBeNull();
  });

  test('uses the localized warning when fewer than 5 comparables, ignoring API PT text', () => {
    renderResult({
      ...BASE,
      comparablesCount: 3,
      warning: 'Amostra insuficiente (n=3). Texto PT da API.',
    });
    expect(screen.getByRole('alert').textContent).toContain(
      'Amostra insuficiente (n=3). A estimativa é indicativa; recomenda-se prudência.',
    );
    expect(screen.getByTestId('range-low')).toBeTruthy();
    expect(screen.getByTestId('range-high')).toBeTruthy();
  });

  test('falls back to the i18n warning when fewer than 5 comparables and no API text', () => {
    renderResult({...BASE, comparablesCount: 2, warning: null});
    expect(screen.getByRole('alert').textContent).toContain(
      'Amostra insuficiente (n=2). A estimativa é indicativa; recomenda-se prudência.',
    );
  });
});

