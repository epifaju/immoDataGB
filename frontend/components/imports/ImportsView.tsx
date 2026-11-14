'use client';

import {useQuery} from '@tanstack/react-query';
import {useTranslations} from 'next-intl';
import {useState} from 'react';
import {listImports} from '@/lib/api/imports';
import {ImportJobPanel} from './ImportJobPanel';
import {ImportUploadForm} from './ImportUploadForm';

export function ImportsView() {
  const t = useTranslations('imports');
  const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';
  const mutationsEnabled = process.env.NEXT_PUBLIC_IMPORTS_MUTATIONS_ENABLED === 'true';
  const [jobId, setJobId] = useState<string | null>(null);

  const recentQuery = useQuery({
    queryKey: ['import-jobs'],
    queryFn: async ({signal}) => {
      const {body} = await listImports(apiUrl, 0, 10, signal);
      if (!body.success || !body.data) {
        throw new Error(body.message ?? 'INTERNAL_ERROR');
      }
      return body.data.items;
    },
    retry: false,
    refetchOnWindowFocus: false,
  });

  const recent = recentQuery.data ?? [];

  return (
    <div className="space-y-4">
      {mutationsEnabled ? <ImportUploadForm apiUrl={apiUrl} onStarted={setJobId} /> : null}
      {jobId ? <ImportJobPanel key={jobId} apiUrl={apiUrl} jobId={jobId} /> : null}
      {recent.length > 0 ? (
        <section className="space-y-1">
          <h2 className="font-medium">{t('recent.title')}</h2>
          <ul className="space-y-1 text-sm">
            {recent.map((job) => (
              <li key={job.jobId} className="flex items-center gap-2">
                <span>{job.originalFilename}</span>
                <span className="text-slate-600">{t(`status.${job.status}`)}</span>
                <button
                  type="button"
                  className="rounded border border-slate-300 px-2 py-0.5"
                  onClick={() => setJobId(job.jobId)}
                >
                  {t('recent.open')}
                </button>
              </li>
            ))}
          </ul>
        </section>
      ) : null}
    </div>
  );
}
