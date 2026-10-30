'use client';

import {useTranslations} from 'next-intl';
import type {PropertyMapItem} from '@/lib/api/types';
import {Link} from '@/i18n/navigation';
import {ConfidenceBadge} from './ConfidenceBadge';

export function PropertyPopupContent({
  item,
  mapQuery,
}: {
  item: PropertyMapItem;
  mapQuery: string;
}) {
  const t = useTranslations('map');
  const href = mapQuery ? `/properties/${item.id}?${mapQuery}` : `/properties/${item.id}`;
  return (
    <div className="max-w-xs space-y-1 p-1">
      <div className="font-medium">{item.title}</div>
      <div className="text-sm text-slate-600">{item.reference}</div>
      <div className="text-sm">{t(`type.${item.propertyType}`)}</div>
      <ConfidenceBadge level={item.confidenceLevel} score={item.confidenceScore} />
      <p className="text-sm text-slate-700">{item.confidenceSummary}</p>
      <Link href={href} className="text-sm font-medium text-blue-700 underline">
        {t('popup.viewDetails')}
      </Link>
    </div>
  );
}
