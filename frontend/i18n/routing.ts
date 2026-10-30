import {defineRouting} from 'next-intl/routing';

export const routing = defineRouting({
  locales: ['pt', 'fr', 'en'],
  defaultLocale: 'pt',
  localePrefix: 'always',
});
