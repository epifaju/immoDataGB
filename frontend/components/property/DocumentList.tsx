'use client';

import {useMutation, useQueryClient} from '@tanstack/react-query';
import {useLocale, useTranslations} from 'next-intl';
import {useState} from 'react';
import {deleteDocument, requestDocumentSignedUrl} from '@/lib/api/documents';
import type {DocumentItem} from '@/lib/api/types';
import {DOCUMENT_TYPES} from '@/lib/api/types';
import {formatIsoDate} from '@/lib/format/date';
import {useDocumentErrorMessage} from './documentErrors';

function translateType(
  t: ReturnType<typeof useTranslations<'property'>>,
  documentType: string,
): string {
  if ((DOCUMENT_TYPES as readonly string[]).includes(documentType)) {
    return t(`documentType.${documentType as (typeof DOCUMENT_TYPES)[number]}`);
  }
  return documentType;
}

type DocumentListProps = {
  documents: DocumentItem[];
  propertyId: string;
  apiUrl: string;
  mutationsEnabled: boolean;
};

export function DocumentList({
  documents,
  propertyId,
  apiUrl,
  mutationsEnabled,
}: DocumentListProps) {
  const t = useTranslations('property');
  const locale = useLocale();
  const queryClient = useQueryClient();
  const errorMessage = useDocumentErrorMessage();
  const [actionError, setActionError] = useState<string | null>(null);

  const downloadMutation = useMutation({
    mutationFn: async (documentId: string) => {
      const {body} = await requestDocumentSignedUrl(apiUrl, documentId);
      if (!body.success || !body.data?.url) {
        throw new Error(body.message ?? 'INTERNAL_ERROR');
      }
      return body.data.url;
    },
    onMutate: () => setActionError(null),
    onSuccess: (url) => {
      window.open(url, '_blank', 'noopener,noreferrer');
    },
    onError: (error: Error) => setActionError(errorMessage(error.message)),
  });

  const deleteMutation = useMutation({
    mutationFn: async (documentId: string) => {
      const {body} = await deleteDocument(apiUrl, documentId);
      if (!body.success) {
        throw new Error(body.message ?? 'INTERNAL_ERROR');
      }
    },
    onMutate: () => setActionError(null),
    onSuccess: () =>
      queryClient.invalidateQueries({queryKey: ['property-documents', propertyId]}),
    onError: (error: Error) => setActionError(errorMessage(error.message)),
  });

  if (documents.length === 0) {
    return <p>{t('documents.empty')}</p>;
  }

  function handleDelete(documentId: string) {
    if (window.confirm(t('documents.deleteConfirm'))) {
      deleteMutation.mutate(documentId);
    }
  }

  return (
    <div className="space-y-2">
      {actionError ? <p role="alert">{actionError}</p> : null}
      <ul className="space-y-3">
        {documents.map((doc) => (
          <li key={doc.id} className="rounded border border-slate-200 p-2">
            <div className="font-medium">{doc.originalFilename}</div>
            <div className="text-sm text-slate-600">
              {translateType(t, doc.documentType)}
            </div>
            <div className="text-sm text-slate-600">
              {t('documents.size', {size: Math.max(1, Math.round(doc.sizeBytes / 1024))})}
            </div>
            <div className="text-sm text-slate-600">
              {t('documents.uploadedAt', {
                date: formatIsoDate(doc.createdAt.slice(0, 10), locale),
              })}
            </div>
            {mutationsEnabled ? (
              <div className="mt-1 flex flex-wrap gap-2">
                <button
                  type="button"
                  className="rounded border border-slate-300 px-2 py-0.5 text-sm"
                  disabled={downloadMutation.isPending}
                  onClick={() => downloadMutation.mutate(doc.id)}
                >
                  {t('documents.download')}
                </button>
                <button
                  type="button"
                  className="rounded border border-red-300 px-2 py-0.5 text-sm text-red-700"
                  disabled={deleteMutation.isPending}
                  onClick={() => handleDelete(doc.id)}
                >
                  {t('documents.delete')}
                </button>
              </div>
            ) : null}
          </li>
        ))}
      </ul>
    </div>
  );
}
