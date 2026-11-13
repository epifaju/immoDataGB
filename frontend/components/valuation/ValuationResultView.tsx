'use client';

import {useLocale, useTranslations} from 'next-intl';
import {ConfidenceBadge} from '@/components/map/ConfidenceBadge';
import type {EstimateResult} from '@/lib/api/types';
import {formatXof} from '@/lib/format/currency';
import {formatIsoDate} from '@/lib/format/date';

const MIN_COMPARABLES_WITHOUT_WARNING = 5;

export function ValuationResultView({result}: {result: EstimateResult}) {
  const t = useTranslations('valuation');
  const tMap = useTranslations('map');
  const locale = useLocale();
  const xof = (amount: number) => formatXof(amount, locale);
  const number = new Intl.NumberFormat(locale, {maximumFractionDigits: 2});

  const warning =
    result.warning ??
    (result.comparablesCount < MIN_COMPARABLES_WITHOUT_WARNING
      ? t('warning.fallback', {count: result.comparablesCount})
      : null);

  return (
    <section className="space-y-4" aria-label={t('result.title')}>
      <h2 className="text-lg font-semibold">{t('result.title')}</h2>

      {warning ? (
        <div role="alert" className="rounded border border-orange-300 bg-orange-50 p-3 text-orange-900">
          <p className="font-medium">{t('warning.title')}</p>
          <p>{warning}</p>
        </div>
      ) : null}

      <div className="rounded border border-slate-200 p-4">
        <p className="text-sm text-slate-600">{t('result.range')}</p>
        <p data-testid="valuation-range" className="text-3xl font-bold">
          <span data-testid="range-low">{xof(result.rangeLow)}</span>
          {' – '}
          <span data-testid="range-high">{xof(result.rangeHigh)}</span>
        </p>
        <p className="mt-2 text-sm text-slate-600">
          {t('result.median')}: <span data-testid="median-total">{xof(result.medianTotal)}</span>
        </p>
        <p className="text-sm text-slate-600">
          {t('result.perSqmRange')}: {xof(result.rangeLowPricePerSqm)} –{' '}
          {xof(result.rangeHighPricePerSqm)}
        </p>
        <p className="text-sm text-slate-600">
          {t('result.medianPerSqm')}: {xof(result.medianPricePerSqm)}
        </p>
        <p className="text-sm text-slate-600">
          {t('result.count', {count: result.comparablesCount, raw: result.comparablesRawCount})}
        </p>
        <div className="mt-2">
          <ConfidenceBadge level={result.confidenceLevel} score={result.confidenceScore} />
        </div>
        <p className="mt-2 text-xs text-slate-500">{t('result.currencyNote')}</p>
      </div>

      <div>
        <h3 className="font-medium">{t('comparables.title')}</h3>
        <table className="w-full text-left text-sm">
          <thead>
            <tr>
              <th>{t('comparables.type')}</th>
              <th>{t('comparables.price')}</th>
              <th>{t('comparables.area')}</th>
              <th>{t('comparables.pricePerSqm')}</th>
              <th>{t('comparables.observedAt')}</th>
              <th>{t('comparables.distance')}</th>
            </tr>
          </thead>
          <tbody>
            {result.comparables.map((item) => (
              <tr key={item.id}>
                <td>{tMap(`type.${item.propertyType}`)}</td>
                <td>{xof(item.priceXof)}</td>
                <td>{number.format(item.areaSqm)}</td>
                <td>{xof(item.pricePerSqm)}</td>
                <td>{formatIsoDate(item.observedAt, locale)}</td>
                <td>{number.format(item.distanceMeters)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
