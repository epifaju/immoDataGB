'use client';

import {useQuery} from '@tanstack/react-query';
import {useTranslations} from 'next-intl';
import {getImportReport, getImportStatus} from '@/lib/api/imports';
import type {ImportJobState} from '@/lib/api/types';
import {useImportErrorMessage} from './importErrors';

type ImportJobPanelProps = {
  apiUrl: string;
  jobId: string;
  pollIntervalMs?: number;
};

const DEFAULT_POLL_INTERVAL_MS = 1500;

function isTerminal(status: ImportJobState | undefined): boolean {
  return status === 'completed' || status === 'failed';
}

export function ImportJobPanel({
  apiUrl,
  jobId,
  pollIntervalMs = DEFAULT_POLL_INTERVAL_MS,
}: ImportJobPanelProps) {
  const t = useTranslations('imports');
  const errorMessage = useImportErrorMessage();

  const statusQuery = useQuery({
    queryKey: ['import-status', jobId],
    queryFn: async ({signal}) => {
      const {body} = await getImportStatus(apiUrl, jobId, signal);
      if (!body.success || !body.data) {
        throw new Error(body.message ?? 'INTERNAL_ERROR');
      }
      return body.data;
    },
    refetchInterval: (query) => (isTerminal(query.state.data?.status) ? false : pollIntervalMs),
    retry: false,
    refetchOnWindowFocus: false,
  });

  const terminal = isTerminal(statusQuery.data?.status);

  const reportQuery = useQuery({
    queryKey: ['import-report', jobId],
    queryFn: async ({signal}) => {
      const {body} = await getImportReport(apiUrl, jobId, signal);
      if (!body.success || !body.data) {
        throw new Error(body.message ?? 'INTERNAL_ERROR');
      }
      return body.data;
    },
    enabled: terminal,
    retry: false,
    refetchOnWindowFocus: false,
  });

  if (statusQuery.isError) {
    return <p role="alert">{errorMessage(statusQuery.error.message)}</p>;
  }

  const job = statusQuery.data;
  if (!job) {
    return <p role="status">{t('job.loading')}</p>;
  }

  const report = reportQuery.data;

  return (
    <section className="space-y-3 rounded border border-slate-200 p-2" aria-label={t('job.title')}>
      <h2 className="font-medium">{t('job.title')}</h2>
      <dl className="grid grid-cols-2 gap-1 text-sm sm:grid-cols-5">
        <div>
          <dt className="text-slate-600">{t('job.file')}</dt>
          <dd>{job.originalFilename}</dd>
        </div>
        <div>
          <dt className="text-slate-600">{t('job.status')}</dt>
          <dd data-testid="import-status">{t(`status.${job.status}`)}</dd>
        </div>
        <div>
          <dt className="text-slate-600">{t('job.total')}</dt>
          <dd>{job.totalRows}</dd>
        </div>
        <div>
          <dt className="text-slate-600">{t('job.success')}</dt>
          <dd>{job.successRows}</dd>
        </div>
        <div>
          <dt className="text-slate-600">{t('job.errors')}</dt>
          <dd>{job.errorRows}</dd>
        </div>
      </dl>
      {!terminal ? <p role="status">{t('job.polling')}</p> : null}
      {job.status === 'failed' ? (
        <p role="alert">
          {t('job.failed')}
          {job.errorMessage ? ` ${job.errorMessage}` : ''}
        </p>
      ) : null}
      {reportQuery.isError ? <p role="alert">{errorMessage(reportQuery.error.message)}</p> : null}
      {report ? (
        <div className="space-y-1">
          <h3 className="font-medium">{t('report.title')}</h3>
          {report.errors.length === 0 ? (
            <p>{t('report.empty')}</p>
          ) : (
            <table className="w-full text-left text-sm">
              <thead>
                <tr>
                  <th scope="col">{t('report.row')}</th>
                  <th scope="col">{t('report.error')}</th>
                  <th scope="col">{t('report.excerpt')}</th>
                </tr>
              </thead>
              <tbody>
                {report.errors.map((rowError) => (
                  <tr key={`${rowError.rowNumber}-${rowError.code}`}>
                    <td>{rowError.rowNumber}</td>
                    <td>
                      {errorMessage(rowError.code)}{' '}
                      <span className="text-xs text-slate-500">({rowError.code})</span>
                    </td>
                    <td className="font-mono text-xs">{rowError.rawExcerpt ?? ''}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      ) : null}
    </section>
  );
}
