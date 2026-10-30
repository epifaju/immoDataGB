'use client';

import {keepPreviousData, useQuery} from '@tanstack/react-query';
import dynamic from 'next/dynamic';
import {useTranslations} from 'next-intl';
import {useSearchParams} from 'next/navigation';
import {useCallback, useEffect, useMemo, useState} from 'react';
import {fetchProperties} from '@/lib/api/properties';
import type {PropertyMapItem} from '@/lib/api/types';
import {
  type ConfidenceLevelParam,
  type PropertyTypeParam,
} from '@/lib/map/constants';
import {
  parseMapSearchParams,
  serializeMapSearchParams,
  withFiltersReset,
} from '@/lib/map/url-state';
import {usePathname, useRouter} from '@/i18n/navigation';
import {FilterPanel} from './FilterPanel';
import {PropertyList} from './PropertyList';

const PropertyMap = dynamic(() => import('./PropertyMap').then((mod) => mod.PropertyMap), {
  ssr: false,
});

const ERROR_CODES = new Set([
  'PROPERTY_BBOX_INVALID',
  'PROPERTY_FILTER_INVALID',
  'PROPERTY_PAGE_INVALID',
  'HEALTH_DATABASE_UNAVAILABLE',
  'INTERNAL_ERROR',
]);

export function MapView() {
  const tMap = useTranslations('map');
  const tErrors = useTranslations('errors');
  const tCommon = useTranslations('common');
  const searchParams = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';

  const state = useMemo(
    () => parseMapSearchParams(new URLSearchParams(searchParams.toString())),
    [searchParams],
  );

  const [selected, setSelected] = useState<PropertyMapItem | null>(null);

  const replaceState = useCallback(
    (next: typeof state) => {
      const query = serializeMapSearchParams(next);
      router.replace(query ? `${pathname}?${query}` : pathname, {scroll: false});
    },
    [pathname, router],
  );

  const query = useQuery({
    queryKey: ['properties', state.bbox, state.type, state.confidence, state.page],
    queryFn: ({signal}) =>
      fetchProperties(
        apiUrl,
        {
          bbox: state.bbox,
          type: state.type,
          confidence: state.confidence,
          page: state.page,
        },
        signal,
      ),
    placeholderData: keepPreviousData,
    retry: false,
    refetchOnWindowFocus: false,
  });

  const page = query.data?.body.data ?? null;

  useEffect(() => {
    if (!page) {
      return;
    }
    if (page.totalElements > 0 && page.page > 0 && page.items.length === 0) {
      replaceState({...state, page: 0});
    }
  }, [page, replaceState, state]);

  let errorMessage: string | null = null;
  if (query.isError) {
    errorMessage = tCommon('error');
  } else if (query.data && !query.data.body.success) {
    const code = query.data.body.message;
    errorMessage =
      code && ERROR_CODES.has(code) ? tErrors(code as 'INTERNAL_ERROR') : tCommon('error');
  }

  const showSkeleton = query.isLoading && !query.data;

  return (
    <div className="space-y-4 p-4">
      <h1 className="text-2xl font-semibold">{tMap('title')}</h1>
      <FilterPanel
        type={state.type}
        confidence={state.confidence}
        onTypeChange={(type: PropertyTypeParam | undefined) =>
          replaceState(withFiltersReset(state, {type}))
        }
        onConfidenceChange={(confidence: ConfidenceLevelParam | undefined) =>
          replaceState(withFiltersReset(state, {confidence}))
        }
      />
      {errorMessage ? (
        <div role="alert" className="rounded border border-red-300 bg-red-50 p-2 text-red-800">
          {errorMessage}
        </div>
      ) : null}
      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_320px]">
        <div className="min-h-[480px] overflow-hidden rounded border">
          <PropertyMap
            bbox={state.bbox}
            items={page?.items ?? []}
            selected={selected}
            onSelect={setSelected}
            onClearSelection={() => setSelected(null)}
            onBboxChange={(bbox) => replaceState(withFiltersReset(state, {bbox}))}
          />
        </div>
        <div>
          {showSkeleton ? (
            <div className="animate-pulse space-y-2">
              <div className="h-6 rounded bg-slate-200" />
              <div className="h-20 rounded bg-slate-200" />
              <div className="h-20 rounded bg-slate-200" />
            </div>
          ) : (
            <PropertyList
              page={page}
              selectedId={selected?.id ?? null}
              onSelect={setSelected}
              onPageChange={(pageNumber) => replaceState({...state, page: pageNumber})}
            />
          )}
        </div>
      </div>
    </div>
  );
}
