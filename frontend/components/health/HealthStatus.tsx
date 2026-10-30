'use client';

import {useQuery} from '@tanstack/react-query';
import {useTranslations} from 'next-intl';
import {Card, CardContent, CardHeader, CardTitle} from '@/components/ui/card';
import {fetchHealth} from '@/lib/api/health';
import type {HealthView} from '@/lib/api/types';

export function HealthStatus() {
  const t = useTranslations('common');
  const errors = useTranslations('errors');
  const query = useQuery({
    queryKey: ['health'],
    queryFn: () => fetchHealth(process.env.NEXT_PUBLIC_API_URL ?? ''),
    retry: false,
    refetchOnWindowFocus: false,
  });

  if (query.isPending) {
    return <p>{t('loading')}</p>;
  }
  if (query.isError || !query.data) {
    return <p>{t('error')}</p>;
  }

  const {status, body} = query.data;
  if (status === 500 && body.message === 'INTERNAL_ERROR') {
    return <p>{errors('INTERNAL_ERROR')}</p>;
  }
  if (body.message !== null && body.message !== 'HEALTH_DATABASE_UNAVAILABLE') {
    return <p>{t('error')}</p>;
  }

  const data: HealthView | null = body.data;
  if (!data) {
    return <p>{t('error')}</p>;
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('health.title')}</CardTitle>
      </CardHeader>
      <CardContent>
        {status === 503 && body.message === 'HEALTH_DATABASE_UNAVAILABLE' ? (
          <p>{errors('HEALTH_DATABASE_UNAVAILABLE')}</p>
        ) : null}
        <StatusLine label={t('health.api')} value={labelFor(t, data.api)} />
        <StatusLine label={t('health.database')} value={labelFor(t, data.database)} />
        <StatusLine
          label={t('health.postgis')}
          value={status === 503 ? '—' : (data.postgis ?? '—')}
        />
      </CardContent>
    </Card>
  );
}

function labelFor(t: ReturnType<typeof useTranslations<'common'>>, value: string): string {
  if (value === 'up') {
    return t('health.up');
  }
  if (value === 'down') {
    return t('health.down');
  }
  return value;
}

function StatusLine({label, value}: {label: string; value: string}) {
  return (
    <p>
      <span>{label}</span> <span>{value}</span>
    </p>
  );
}
