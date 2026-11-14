'use client';

import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {useLocale, useTranslations} from 'next-intl';
import {useState} from 'react';
import {
  ADMIN_PAGE_SIZE,
  AUDIT_QUERY_KEY,
  fetchValidationQueue,
  rejectProperty,
  validateProperty,
  VALIDATION_QUEUE_QUERY_KEY,
} from '@/lib/api/admin';
import type {ValidationQueueItem} from '@/lib/api/types';
import {PROPERTY_TYPES} from '@/lib/map/constants';
import {useAdminErrorMessage} from './adminErrors';
import {formatInstant} from './formatInstant';

type QueueAction = 'validate' | 'reject';

export function ValidationQueueView() {
  const t = useTranslations('admin');
  const tErrors = useTranslations('errors');
  const tMap = useTranslations('map');
  const locale = useLocale();
  const queryClient = useQueryClient();
  const adminError = useAdminErrorMessage();
  const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';
  const mutationsEnabled = process.env.NEXT_PUBLIC_ADMIN_MUTATIONS_ENABLED === 'true';
  const [page, setPage] = useState(0);
  const [notice, setNotice] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const queueQuery = useQuery({
    queryKey: [...VALIDATION_QUEUE_QUERY_KEY, page, ADMIN_PAGE_SIZE],
    queryFn: async ({signal}) => {
      const {body} = await fetchValidationQueue(apiUrl, {page, size: ADMIN_PAGE_SIZE}, signal);
      if (!body.success || !body.data) {
        throw new Error(body.message ?? 'INTERNAL_ERROR');
      }
      return body.data;
    },
    retry: false,
    refetchOnWindowFocus: false,
  });

  const mutation = useMutation({
    mutationFn: async (input: {id: string; action: QueueAction}) => {
      const response =
        input.action === 'validate'
          ? await validateProperty(apiUrl, input.id)
          : await rejectProperty(apiUrl, input.id);
      if (!response.body.success) {
        throw new Error(response.body.message ?? 'INTERNAL_ERROR');
      }
      return input.action;
    },
    onMutate: () => {
      setNotice(null);
      setActionError(null);
    },
    onSuccess: async (action) => {
      setNotice(action === 'validate' ? t('queue.actions.validated') : t('queue.actions.rejected'));
      await queryClient.invalidateQueries({queryKey: [...VALIDATION_QUEUE_QUERY_KEY]});
      await queryClient.invalidateQueries({queryKey: [...AUDIT_QUERY_KEY]});
    },
    onError: (error: Error) => {
      if (error.message === 'NOT_FOUND') {
        setActionError(t('queue.actions.unavailable'));
        return;
      }
      setActionError(adminError(error.message, tErrors('INTERNAL_ERROR')));
    },
  });

  function propertyTypeLabel(propertyType: string): string {
    if ((PROPERTY_TYPES as readonly string[]).includes(propertyType)) {
      return tMap(`type.${propertyType as (typeof PROPERTY_TYPES)[number]}`);
    }
    return propertyType;
  }

  const data = queueQuery.data;
  const loadError =
    queueQuery.isError && queueQuery.error instanceof Error
      ? adminError(queueQuery.error.message, t('queue.error'))
      : null;

  return (
    <div className="space-y-3">
      {notice ? <p role="status">{notice}</p> : null}
      {actionError ? (
        <p role="alert" className="text-sm text-red-700">
          {actionError}
        </p>
      ) : null}
      {queueQuery.isPending ? <p>{t('queue.loading')}</p> : null}
      {loadError && !data ? (
        <p role="alert" className="text-sm text-red-700">
          {loadError}
        </p>
      ) : null}
      {data && data.items.length === 0 ? <p>{t('queue.empty')}</p> : null}
      {data && data.items.length > 0 ? (
        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-left text-sm">
            <thead>
              <tr className="border-b border-slate-200 text-slate-600">
                <th className="py-2 pr-3 font-medium" scope="col">
                  {t('queue.columns.reference')}
                </th>
                <th className="py-2 pr-3 font-medium" scope="col">
                  {t('queue.columns.title')}
                </th>
                <th className="py-2 pr-3 font-medium" scope="col">
                  {t('queue.columns.type')}
                </th>
                <th className="py-2 pr-3 font-medium" scope="col">
                  {t('queue.columns.createdAt')}
                </th>
                <th className="py-2 pr-3 font-medium" scope="col">
                  {t('queue.columns.location')}
                </th>
                {mutationsEnabled ? (
                  <th className="py-2 font-medium" scope="col">
                    {t('queue.columns.actions')}
                  </th>
                ) : null}
              </tr>
            </thead>
            <tbody>
              {data.items.map((item) => (
                <tr key={item.id} className="border-b border-slate-100">
                  <td className="py-2 pr-3">{item.reference}</td>
                  <td className="py-2 pr-3">{item.title ?? '—'}</td>
                  <td className="py-2 pr-3">{propertyTypeLabel(item.propertyType)}</td>
                  <td className="py-2 pr-3">{formatInstant(item.createdAt, locale)}</td>
                  <td className="py-2 pr-3">
                    {item.latitude}, {item.longitude}
                  </td>
                  {mutationsEnabled ? (
                    <td className="py-2">
                      <QueueActions
                        item={item}
                        pending={mutation.isPending}
                        onValidate={() => mutation.mutate({id: item.id, action: 'validate'})}
                        onReject={() => mutation.mutate({id: item.id, action: 'reject'})}
                        validateLabel={t('queue.actions.validate')}
                        rejectLabel={t('queue.actions.reject')}
                      />
                    </td>
                  ) : null}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
      {data && data.totalElements > 0 ? (
        <div className="flex flex-wrap items-center gap-3 text-sm">
          <p>
            {t('queue.range', {
              from: data.page * data.size + 1,
              to: Math.min((data.page + 1) * data.size, data.totalElements),
              total: data.totalElements,
            })}
          </p>
          <button
            type="button"
            className="rounded border border-slate-300 px-2 py-1 disabled:opacity-50"
            disabled={data.page <= 0}
            onClick={() => setPage((current) => current - 1)}
          >
            {t('queue.previous')}
          </button>
          <button
            type="button"
            className="rounded border border-slate-300 px-2 py-1 disabled:opacity-50"
            disabled={data.page + 1 >= data.totalPages}
            onClick={() => setPage((current) => current + 1)}
          >
            {t('queue.next')}
          </button>
        </div>
      ) : null}
    </div>
  );
}

function QueueActions({
  item,
  pending,
  onValidate,
  onReject,
  validateLabel,
  rejectLabel,
}: {
  item: ValidationQueueItem;
  pending: boolean;
  onValidate: () => void;
  onReject: () => void;
  validateLabel: string;
  rejectLabel: string;
}) {
  return (
    <div className="flex gap-2">
      <button
        type="button"
        className="rounded bg-slate-900 px-2 py-1 text-white disabled:opacity-50"
        disabled={pending}
        aria-label={`${validateLabel} ${item.reference}`}
        onClick={onValidate}
      >
        {validateLabel}
      </button>
      <button
        type="button"
        className="rounded border border-red-300 px-2 py-1 text-red-800 disabled:opacity-50"
        disabled={pending}
        aria-label={`${rejectLabel} ${item.reference}`}
        onClick={onReject}
      >
        {rejectLabel}
      </button>
    </div>
  );
}
