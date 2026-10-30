'use client';

import {useLocale, useTranslations} from 'next-intl';
import type {SourceItem} from '@/lib/api/types';
import {SOURCE_TYPES} from '@/lib/api/types';
import {formatIsoDate} from '@/lib/format/date';

function translateType(
  t: ReturnType<typeof useTranslations<'property'>>,
  sourceType: string,
): string {
  if ((SOURCE_TYPES as readonly string[]).includes(sourceType)) {
    return t(`sourceType.${sourceType as (typeof SOURCE_TYPES)[number]}`);
  }
  return sourceType;
}

export function SourceList({sources}: {sources: SourceItem[]}) {
  const t = useTranslations('property');
  const locale = useLocale();
  if (sources.length === 0) {
    return <p>{t('sources.empty')}</p>;
  }
  return (
    <ul className="space-y-3">
      {sources.map((source) => {
        const typeLabel = translateType(t, source.sourceType);
        const title = source.label?.trim() ? source.label : typeLabel;
        return (
          <li key={source.id} className="rounded border border-slate-200 p-2">
            <div className="font-medium">{title}</div>
            {source.label?.trim() ? (
              <div className="text-sm text-slate-600">{typeLabel}</div>
            ) : null}
            {source.observedAt ? (
              <div className="text-sm text-slate-600">
                {t('sources.observedAt', {date: formatIsoDate(source.observedAt, locale)})}
              </div>
            ) : null}
            <span
              className={`mt-1 inline-block rounded px-2 py-0.5 text-xs ${
                source.isVerified
                  ? 'bg-cyan-100 text-cyan-800'
                  : 'bg-slate-100 text-slate-700'
              }`}
            >
              {source.isVerified ? t('sources.verified') : t('sources.unverified')}
            </span>
          </li>
        );
      })}
    </ul>
  );
}
