import {useTranslations} from 'next-intl';

const IMPORT_ERROR_CODES = [
  'IMPORT_FILE_REQUIRED',
  'IMPORT_FILE_TOO_LARGE',
  'IMPORT_INVALID_TYPE',
  'IMPORT_INVALID_HEADER',
  'IMPORT_JOB_NOT_FOUND',
  'IMPORT_JOB_ID_INVALID',
  'IMPORT_PAGE_INVALID',
  'IMPORT_MISSING_FIELD',
  'IMPORT_INVALID_PROPERTY_TYPE',
  'IMPORT_INVALID_COORDINATE',
  'IMPORT_DUPLICATE_REFERENCE',
  'INTERNAL_ERROR',
] as const;

type ImportErrorCode = (typeof IMPORT_ERROR_CODES)[number];

export function useImportErrorMessage(): (code: string | null | undefined) => string {
  const tErrors = useTranslations('errors');
  const tCommon = useTranslations('common');
  return (code) => {
    if (code && (IMPORT_ERROR_CODES as readonly string[]).includes(code)) {
      return tErrors(code as ImportErrorCode);
    }
    return tCommon('error');
  };
}
