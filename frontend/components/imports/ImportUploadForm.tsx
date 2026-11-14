'use client';

import {useMutation, useQueryClient} from '@tanstack/react-query';
import {useTranslations} from 'next-intl';
import {useId, useRef, useState} from 'react';
import type {FormEvent} from 'react';
import {MAX_IMPORT_SIZE_BYTES, startImport} from '@/lib/api/imports';
import {useImportErrorMessage} from './importErrors';

type ImportUploadFormProps = {
  apiUrl: string;
  onStarted: (jobId: string) => void;
};

export function ImportUploadForm({apiUrl, onStarted}: ImportUploadFormProps) {
  const t = useTranslations('imports');
  const queryClient = useQueryClient();
  const errorMessage = useImportErrorMessage();
  const fileId = useId();
  const fileRef = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: async (payload: File) => {
      const {body} = await startImport(apiUrl, payload);
      if (!body.success || !body.data) {
        throw new Error(body.message ?? 'INTERNAL_ERROR');
      }
      return body.data;
    },
    onSuccess: async (result) => {
      setFile(null);
      if (fileRef.current) {
        fileRef.current.value = '';
      }
      onStarted(result.jobId);
      await queryClient.invalidateQueries({queryKey: ['import-jobs']});
    },
    onError: (err: Error) =>
      setError(err.message === 'NOT_FOUND' ? t('upload.unavailable') : errorMessage(err.message)),
  });

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!file) {
      setError(t('upload.fileRequired'));
      return;
    }
    if (file.size > MAX_IMPORT_SIZE_BYTES) {
      setError(errorMessage('IMPORT_FILE_TOO_LARGE'));
      return;
    }
    setError(null);
    mutation.mutate(file);
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-2 rounded border border-slate-200 p-2">
      <h2 className="font-medium">{t('upload.title')}</h2>
      <p className="text-sm text-slate-600">{t('upload.hint')}</p>
      <div className="flex flex-col gap-1">
        <label htmlFor={fileId} className="text-sm">
          {t('upload.file')}
        </label>
        <input
          id={fileId}
          ref={fileRef}
          type="file"
          accept=".csv,text/csv"
          onChange={(event) => setFile(event.target.files?.[0] ?? null)}
        />
      </div>
      <button
        type="submit"
        className="rounded bg-blue-700 px-3 py-1 text-sm text-white"
        disabled={mutation.isPending}
      >
        {mutation.isPending ? t('upload.submitting') : t('upload.submit')}
      </button>
      {error ? <p role="alert">{error}</p> : null}
    </form>
  );
}
