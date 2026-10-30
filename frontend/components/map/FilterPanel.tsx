'use client';

import {useTranslations} from 'next-intl';
import {
  CONFIDENCE_LEVELS,
  PROPERTY_TYPES,
  type ConfidenceLevelParam,
  type PropertyTypeParam,
} from '@/lib/map/constants';

export function FilterPanel({
  type,
  confidence,
  onTypeChange,
  onConfidenceChange,
}: {
  type?: PropertyTypeParam;
  confidence?: ConfidenceLevelParam;
  onTypeChange: (value: PropertyTypeParam | undefined) => void;
  onConfidenceChange: (value: ConfidenceLevelParam | undefined) => void;
}) {
  const t = useTranslations('map');
  return (
    <div className="flex flex-wrap gap-3">
      <label className="flex flex-col text-sm">
        <span>{t('filters.type')}</span>
        <select
          value={type ?? ''}
          onChange={(event) =>
            onTypeChange(
              event.target.value === ''
                ? undefined
                : (event.target.value as PropertyTypeParam),
            )
          }
        >
          <option value="">{t('filters.all')}</option>
          {PROPERTY_TYPES.map((value) => (
            <option key={value} value={value}>
              {t(`type.${value}`)}
            </option>
          ))}
        </select>
      </label>
      <label className="flex flex-col text-sm">
        <span>{t('filters.confidence')}</span>
        <select
          value={confidence ?? ''}
          onChange={(event) =>
            onConfidenceChange(
              event.target.value === ''
                ? undefined
                : (event.target.value as ConfidenceLevelParam),
            )
          }
        >
          <option value="">{t('filters.all')}</option>
          {CONFIDENCE_LEVELS.map((value) => (
            <option key={value} value={value}>
              {t(`confidence.${value}`)}
            </option>
          ))}
        </select>
      </label>
    </div>
  );
}
