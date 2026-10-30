import {getRequestConfig} from 'next-intl/server';
import {routing} from './routing';

export default getRequestConfig(async ({requestLocale}) => {
  let locale = await requestLocale;
  if (!locale || !routing.locales.includes(locale as (typeof routing.locales)[number])) {
    locale = routing.defaultLocale;
  }
  const common = (await import(`../messages/${locale}/common.json`)).default;
  const errors = (await import(`../messages/${locale}/errors.json`)).default;
  const map = (await import(`../messages/${locale}/map.json`)).default;
  return {
    locale,
    messages: {
      common,
      errors,
      map,
    },
  };
});
