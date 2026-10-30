'use client';

import {useQuery} from '@tanstack/react-query';
import {useTranslations} from 'next-intl';
import {useSearchParams} from 'next/navigation';
import {useMemo} from 'react';
import {fetchPropertyDetail, fetchPropertySources} from '@/lib/api/properties';
import {Link} from '@/i18n/navigation';
import {ConfidenceBadge} from '@/components/map/ConfidenceBadge';
import {parseMapSearchParams, serializeMapSearchParams} from '@/lib/map/url-state';
import {DocStatusBadge} from './DocStatusBadge';
import {SourceList} from './SourceList';

const DETAIL_ERROR_CODES = new Set([
  'HEALTH_DATABASE_UNAVAILABLE',
  'INTERNAL_ERROR',
]);

export function PropertyDetailView({id}: {id: string}) {
  const t = useTranslations('property');
  const tMap = useTranslations('map');
  const tErrors = useTranslations('errors');
  const tCommon = useTranslations('common');
  const searchParams = useSearchParams();
  const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';

  const mapHref = useMemo(() => {
    const raw = new URLSearchParams(searchParams.toString());
    const hasAny =
      raw.has('bbox') || raw.has('type') || raw.has('confidence') || raw.has('page');
    if (!hasAny) {
      return '/map';
    }
    const state = parseMapSearchParams(raw);
    const query = serializeMapSearchParams(state);
    return query ? `/map?${query}` : '/map';
  }, [searchParams]);

  const detailQuery = useQuery({
    queryKey: ['property', id],
    queryFn: ({signal}) => fetchPropertyDetail(apiUrl, id, signal),
    retry: false,
    refetchOnWindowFocus: false,
  });

  const sourcesQuery = useQuery({
    queryKey: ['property-sources', id],
    queryFn: ({signal}) => fetchPropertySources(apiUrl, id, signal),
    enabled: Boolean(detailQuery.data?.body.success && detailQuery.data.body.data),
    retry: false,
    refetchOnWindowFocus: false,
  });

  if (detailQuery.isLoading) {
    return (
      <div className="space-y-3 p-4">
        <div className="h-8 w-1/2 animate-pulse rounded bg-slate-200" />
        <div className="h-24 animate-pulse rounded bg-slate-200" />
      </div>
    );
  }

  if (detailQuery.isError) {
    return (
      <div className="space-y-3 p-4">
        <p role="alert">{tCommon('error')}</p>
        <Link href={mapHref}>{t('backToMap')}</Link>
      </div>
    );
  }

  const detailBody = detailQuery.data?.body;
  if (
    !detailBody?.success ||
    detailBody.message === 'PROPERTY_NOT_FOUND' ||
    detailBody.message === 'PROPERTY_ID_INVALID' ||
    !detailBody.data
  ) {
    if (
      detailBody?.message === 'PROPERTY_NOT_FOUND' ||
      detailBody?.message === 'PROPERTY_ID_INVALID' ||
      detailQuery.data?.status === 404
    ) {
      return (
        <div className="space-y-3 p-4">
          <p role="alert">{t('notFound')}</p>
          <Link href={mapHref}>{t('backToMap')}</Link>
        </div>
      );
    }
    const code = detailBody?.message;
    const message =
      code && DETAIL_ERROR_CODES.has(code)
        ? tErrors(code as 'INTERNAL_ERROR')
        : tCommon('error');
    return (
      <div className="space-y-3 p-4">
        <p role="alert">{message}</p>
        <Link href={mapHref}>{t('backToMap')}</Link>
      </div>
    );
  }

  const detail = detailBody.data;
  const address = detail.addressText?.trim() ? detail.addressText : null;

  let sourcesError: string | null = null;
  if (sourcesQuery.isError) {
    sourcesError = tCommon('error');
  } else if (sourcesQuery.data && !sourcesQuery.data.body.success) {
    const code = sourcesQuery.data.body.message;
    if (code === 'PROPERTY_NOT_FOUND' || code === 'PROPERTY_ID_INVALID') {
      sourcesError = t('notFound');
    } else if (code && DETAIL_ERROR_CODES.has(code)) {
      sourcesError = tErrors(code as 'INTERNAL_ERROR');
    } else {
      sourcesError = tCommon('error');
    }
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6 p-4">
      <Link href={mapHref} className="text-sm text-blue-700 underline">
        {t('backToMap')}
      </Link>
      <header className="space-y-2">
        <h1 className="text-2xl font-semibold">{detail.title}</h1>
        <p className="text-sm text-slate-600">
          {t('reference')}: {detail.reference}
        </p>
        <p>{tMap(`type.${detail.propertyType}`)}</p>
        {address ? (
          <p>
            {t('address')}: {address}
          </p>
        ) : null}
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-sm font-medium">{t('confidence')}</span>
          <ConfidenceBadge level={detail.confidenceLevel} score={detail.confidenceScore} />
        </div>
        <p className="text-sm text-slate-700">{detail.confidenceSummary}</p>
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-sm font-medium">{t('docStatus.title')}</span>
          <DocStatusBadge status={detail.docStatus} />
        </div>
      </header>
      <section className="space-y-2">
        <h2 className="text-xl font-medium">{t('sources.title')}</h2>
        {sourcesQuery.isLoading ? (
          <div className="h-20 animate-pulse rounded bg-slate-200" />
        ) : sourcesError ? (
          <p role="alert">{sourcesError}</p>
        ) : (
          <SourceList sources={sourcesQuery.data?.body.data ?? []} />
        )}
      </section>
    </div>
  );
}
