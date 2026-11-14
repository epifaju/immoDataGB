import {useTranslations} from 'next-intl';

const ADMIN_ERROR_CODES = ['NOT_FOUND', 'PROPERTY_NOT_FOUND', 'ADMIN_REQUEST_INVALID'] as const;

type AdminErrorCode = (typeof ADMIN_ERROR_CODES)[number];

export function useAdminErrorMessage(): (code: string | null | undefined, fallback: string) => string {
  const tErrors = useTranslations('errors');
  return (code, fallback) => {
    if (code && (ADMIN_ERROR_CODES as readonly string[]).includes(code)) {
      return tErrors(code as AdminErrorCode);
    }
    return fallback;
  };
}
