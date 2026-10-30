'use client';

import {useTranslations} from 'next-intl';
import type {PropertyMapItem} from '@/lib/api/types';

const LEVEL_CLASS: Record<PropertyMapItem['confidenceLevel'], string> = {
  low: 'bg-red-100 text-red-800',
  medium: 'bg-orange-100 text-orange-800',
  high: 'bg-green-100 text-green-800',
};

export function ConfidenceBadge({
  level,
  score,
}: {
  level: PropertyMapItem['confidenceLevel'];
  score: number;
}) {
  const t = useTranslations('map');
  return (
    <span className={`inline-flex items-center gap-1 rounded px-2 py-0.5 text-sm ${LEVEL_CLASS[level]}`}>
      <span>{t(`confidence.${level}`)}</span>
      <span>{`${score}/100`}</span>
    </span>
  );
}
