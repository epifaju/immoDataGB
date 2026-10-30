'use client';

import {useTranslations} from 'next-intl';
import type {PropertyMapItem} from '@/lib/api/types';
import {ConfidenceBadge} from './ConfidenceBadge';

export function PropertyPopup({item}: {item: PropertyMapItem}) {
  const t = useTranslations('map');
  return (
    <div className="max-w-xs space-y-1 p-1">
      <div className="font-medium">{item.title}</div>
      <div className="text-sm text-slate-600">{item.reference}</div>
      <div className="text-sm">{t(`type.${item.propertyType}`)}</div>
      <ConfidenceBadge level={item.confidenceLevel} score={item.confidenceScore} />
      <p className="text-sm text-slate-700">{item.confidenceSummary}</p>
    </div>
  );
}
