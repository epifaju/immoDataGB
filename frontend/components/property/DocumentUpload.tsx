'use client';

import {useMutation, useQueryClient} from '@tanstack/react-query';
import {useTranslations} from 'next-intl';
import {useId, useRef, useState} from 'react';
import type {FormEvent} from 'react';
import {uploadPropertyDocument} from '@/lib/api/documents';
import type {DocumentType} from '@/lib/api/types';
import {DOCUMENT_TYPES} from '@/lib/api/types';
import {useDocumentErrorMessage} from './documentErrors';

const MAX_DOCUMENT_SIZE_BYTES = 52_428_800;

type DocumentUploadProps = {
  propertyId: string;
  apiUrl: string;
};

export function DocumentUpload({propertyId, apiUrl}: DocumentUploadProps) {
  const t = useTranslations('property');
  const queryClient = useQueryClient();
  const errorMessage = useDocumentErrorMessage();
  const fileId = useId();
  const typeId = useId();
  const fileRef = useRef<HTMLInputElement>(null);
  const [documentType, setDocumentType] = useState<DocumentType>(DOCUMENT_TYPES[0]);
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  const mutation = useMutation({
    mutationFn: async (payload: {file: File; documentType: DocumentType}) => {
      const {body} = await uploadPropertyDocument(
        apiUrl,
        propertyId,
        payload.file,
        payload.documentType,
      );
      if (!body.success) {
        throw new Error(body.message ?? 'INTERNAL_ERROR');
      }
    },
    onSuccess: async () => {
      setSuccess(true);
      setFile(null);
      if (fileRef.current) {
        fileRef.current.value = '';
      }
      await queryClient.invalidateQueries({queryKey: ['property-documents', propertyId]});
    },
    onError: (err: Error) => setError(errorMessage(err.message)),
  });

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSuccess(false);
    if (!file) {
      setError(t('documents.upload.fileRequired'));
      return;
    }
    if (file.size > MAX_DOCUMENT_SIZE_BYTES) {
      setError(errorMessage('DOCUMENT_FILE_TOO_LARGE'));
      return;
    }
    setError(null);
    mutation.mutate({file, documentType});
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-2 rounded border border-slate-200 p-2">
      <h3 className="font-medium">{t('documents.upload.title')}</h3>
      <div className="flex flex-col gap-1">
        <label htmlFor={fileId} className="text-sm">
          {t('documents.upload.file')}
        </label>
        <input
          id={fileId}
          ref={fileRef}
          type="file"
          accept=".pdf,.jpg,.jpeg,.png,.webp,.heic,.docx,.xlsx"
          onChange={(event) => setFile(event.target.files?.[0] ?? null)}
        />
      </div>
      <div className="flex flex-col gap-1">
        <label htmlFor={typeId} className="text-sm">
          {t('documents.upload.type')}
        </label>
        <select
          id={typeId}
          value={documentType}
          onChange={(event) => setDocumentType(event.target.value as DocumentType)}
        >
          {DOCUMENT_TYPES.map((type) => (
            <option key={type} value={type}>
              {t(`documentType.${type}`)}
            </option>
          ))}
        </select>
      </div>
      <button
        type="submit"
        className="rounded bg-blue-700 px-3 py-1 text-sm text-white"
        disabled={mutation.isPending}
      >
        {mutation.isPending ? t('documents.upload.submitting') : t('documents.upload.submit')}
      </button>
      {error ? <p role="alert">{error}</p> : null}
      {success ? <p role="status">{t('documents.upload.success')}</p> : null}
    </form>
  );
}
