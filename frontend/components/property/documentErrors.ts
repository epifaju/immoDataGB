import {useTranslations} from 'next-intl';

const DOCUMENT_ERROR_CODES = [
  'DOCUMENT_ID_INVALID',
  'DOCUMENT_NOT_FOUND',
  'DOCUMENT_TYPE_INVALID',
  'DOCUMENT_FILE_MISSING',
  'DOCUMENT_FILE_TOO_LARGE',
  'DOCUMENT_FILE_TYPE_REJECTED',
  'DOCUMENT_STORAGE_UNAVAILABLE',
  'INTERNAL_ERROR',
] as const;

type DocumentErrorCode = (typeof DOCUMENT_ERROR_CODES)[number];

export function useDocumentErrorMessage(): (code: string | null | undefined) => string {
  const tErrors = useTranslations('errors');
  const tCommon = useTranslations('common');
  return (code) => {
    if (code && (DOCUMENT_ERROR_CODES as readonly string[]).includes(code)) {
      return tErrors(code as DocumentErrorCode);
    }
    return tCommon('error');
  };
}
