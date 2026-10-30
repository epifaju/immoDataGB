'use client';

import {useTranslations} from 'next-intl';
import type {PageResponse, PropertyMapItem} from '@/lib/api/types';
import {ConfidenceBadge} from './ConfidenceBadge';

export function PropertyList({
  page,
  selectedId,
  onSelect,
  onPageChange,
}: {
  page: PageResponse<PropertyMapItem> | null;
  selectedId: string | null;
  onSelect: (item: PropertyMapItem) => void;
  onPageChange: (page: number) => void;
}) {
  const t = useTranslations('map');
  if (!page || page.totalElements === 0) {
    return (
      <section>
        <h2>{t('list.title')}</h2>
        <p>{t('list.empty')}</p>
      </section>
    );
  }

  const from = page.page * page.size + 1;
  const to = Math.min((page.page + 1) * page.size, page.totalElements);
  const canPrev = page.page > 0;
  const canNext = page.page + 1 < page.totalPages;

  return (
    <section>
      <h2>{t('list.title')}</h2>
      <p>{t('list.range', {from, to, total: page.totalElements})}</p>
      <ul className="space-y-2">
        {page.items.map((item) => (
          <li key={item.id}>
            <button
              type="button"
              className={`w-full rounded border p-2 text-left ${
                selectedId === item.id ? 'border-slate-800' : 'border-slate-200'
              }`}
              onClick={() => onSelect(item)}
            >
              <div className="font-medium">{item.title}</div>
              <div className="text-sm text-slate-600">{item.reference}</div>
              <ConfidenceBadge level={item.confidenceLevel} score={item.confidenceScore} />
              <p className="mt-1 line-clamp-2 text-sm text-slate-700">{item.confidenceSummary}</p>
            </button>
          </li>
        ))}
      </ul>
      <div className="mt-3 flex gap-2">
        <button type="button" disabled={!canPrev} onClick={() => onPageChange(page.page - 1)}>
          {t('list.previous')}
        </button>
        <button type="button" disabled={!canNext} onClick={() => onPageChange(page.page + 1)}>
          {t('list.next')}
        </button>
      </div>
    </section>
  );
}
