'use client';

import {useQuery} from '@tanstack/react-query';
import {useLocale, useTranslations} from 'next-intl';
import {useState, type FormEvent} from 'react';
import {ADMIN_PAGE_SIZE, AUDIT_QUERY_KEY, fetchAudit} from '@/lib/api/admin';
import {useAdminErrorMessage} from './adminErrors';
import {formatInstant} from './formatInstant';

type AuditFilters = {
  entityType: string;
  action: string;
  from: string;
  to: string;
};

const EMPTY_FILTERS: AuditFilters = {
  entityType: '',
  action: '',
  from: '',
  to: '',
};

export function AuditView() {
  const t = useTranslations('admin');
  const locale = useLocale();
  const adminError = useAdminErrorMessage();
  const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';
  const [draft, setDraft] = useState<AuditFilters>(EMPTY_FILTERS);
  const [applied, setApplied] = useState<AuditFilters>(EMPTY_FILTERS);
  const [page, setPage] = useState(0);

  const auditQuery = useQuery({
    queryKey: [
      ...AUDIT_QUERY_KEY,
      applied.entityType,
      applied.action,
      applied.from,
      applied.to,
      page,
      ADMIN_PAGE_SIZE,
    ],
    queryFn: async ({signal}) => {
      const {body} = await fetchAudit(
        apiUrl,
        {
          entityType: applied.entityType || undefined,
          action: applied.action || undefined,
          from: applied.from || undefined,
          to: applied.to || undefined,
          page,
          size: ADMIN_PAGE_SIZE,
        },
        signal,
      );
      if (!body.success || !body.data) {
        throw new Error(body.message ?? 'INTERNAL_ERROR');
      }
      return body.data;
    },
    retry: false,
    refetchOnWindowFocus: false,
  });

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    setPage(0);
    setApplied({
      entityType: draft.entityType.trim(),
      action: draft.action.trim(),
      from: draft.from.trim(),
      to: draft.to.trim(),
    });
  }

  function actionLabel(action: string): string {
    if (action === 'validate' || action === 'reject') {
      return t(`audit.actions.${action}`);
    }
    return action;
  }

  function entityLabel(entityType: string): string {
    if (entityType === 'property') {
      return t('audit.filters.property');
    }
    return entityType;
  }

  const data = auditQuery.data;
  const loadError =
    auditQuery.isError && auditQuery.error instanceof Error
      ? adminError(auditQuery.error.message, t('audit.error'))
      : null;

  return (
    <div className="space-y-4">
      <form className="flex flex-wrap items-end gap-3" onSubmit={onSubmit}>
        <div>
          <label className="block text-sm font-medium" htmlFor="audit-entity-type">
            {t('audit.filters.entityType')}
          </label>
          <select
            id="audit-entity-type"
            className="mt-1 rounded border border-slate-300 px-2 py-1"
            value={draft.entityType}
            onChange={(event) => setDraft((current) => ({...current, entityType: event.target.value}))}
          >
            <option value="">{t('audit.filters.all')}</option>
            <option value="property">{t('audit.filters.property')}</option>
          </select>
        </div>
        <div>
          <label className="block text-sm font-medium" htmlFor="audit-action">
            {t('audit.filters.action')}
          </label>
          <select
            id="audit-action"
            className="mt-1 rounded border border-slate-300 px-2 py-1"
            value={draft.action}
            onChange={(event) => setDraft((current) => ({...current, action: event.target.value}))}
          >
            <option value="">{t('audit.filters.all')}</option>
            <option value="validate">{t('audit.actions.validate')}</option>
            <option value="reject">{t('audit.actions.reject')}</option>
          </select>
        </div>
        <div>
          <label className="block text-sm font-medium" htmlFor="audit-from">
            {t('audit.filters.from')}
          </label>
          <input
            id="audit-from"
            className="mt-1 rounded border border-slate-300 px-2 py-1"
            value={draft.from}
            onChange={(event) => setDraft((current) => ({...current, from: event.target.value}))}
            placeholder="2026-11-01T00:00:00Z"
            autoComplete="off"
          />
        </div>
        <div>
          <label className="block text-sm font-medium" htmlFor="audit-to">
            {t('audit.filters.to')}
          </label>
          <input
            id="audit-to"
            className="mt-1 rounded border border-slate-300 px-2 py-1"
            value={draft.to}
            onChange={(event) => setDraft((current) => ({...current, to: event.target.value}))}
            placeholder="2026-11-14T23:59:59Z"
            autoComplete="off"
          />
        </div>
        <button type="submit" className="rounded bg-slate-900 px-3 py-1 text-white">
          {t('audit.filters.apply')}
        </button>
      </form>
      <p className="text-xs text-slate-500">{t('audit.filters.hint')}</p>

      {auditQuery.isPending ? <p>{t('audit.loading')}</p> : null}
      {loadError && !data ? (
        <p role="alert" className="text-sm text-red-700">
          {loadError}
        </p>
      ) : null}
      {data && data.items.length === 0 ? <p>{t('audit.empty')}</p> : null}
      {data && data.items.length > 0 ? (
        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-left text-sm">
            <thead>
              <tr className="border-b border-slate-200 text-slate-600">
                <th className="py-2 pr-3 font-medium" scope="col">
                  {t('audit.columns.createdAt')}
                </th>
                <th className="py-2 pr-3 font-medium" scope="col">
                  {t('audit.columns.action')}
                </th>
                <th className="py-2 pr-3 font-medium" scope="col">
                  {t('audit.columns.entityType')}
                </th>
                <th className="py-2 pr-3 font-medium" scope="col">
                  {t('audit.columns.entityId')}
                </th>
                <th className="py-2 font-medium" scope="col">
                  {t('audit.columns.summary')}
                </th>
              </tr>
            </thead>
            <tbody>
              {data.items.map((event) => (
                <tr key={event.id} className="border-b border-slate-100">
                  <td className="py-2 pr-3">{formatInstant(event.createdAt, locale)}</td>
                  <td className="py-2 pr-3">{actionLabel(event.action)}</td>
                  <td className="py-2 pr-3">{entityLabel(event.entityType)}</td>
                  <td className="max-w-[14rem] break-all py-2 pr-3 font-mono text-xs">{event.entityId}</td>
                  <td className="py-2">{event.summary ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
      {data && data.totalElements > 0 ? (
        <div className="flex flex-wrap items-center gap-3 text-sm">
          <p>
            {t('audit.range', {
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
            {t('audit.previous')}
          </button>
          <button
            type="button"
            className="rounded border border-slate-300 px-2 py-1 disabled:opacity-50"
            disabled={data.page + 1 >= data.totalPages}
            onClick={() => setPage((current) => current + 1)}
          >
            {t('audit.next')}
          </button>
        </div>
      ) : null}
    </div>
  );
}
