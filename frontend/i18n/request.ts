import {getRequestConfig} from 'next-intl/server';
import {routing} from './routing';

export default getRequestConfig(async ({requestLocale}) => {
  let locale = await requestLocale;
  if (!locale || !routing.locales.includes(locale as (typeof routing.locales)[number])) {
    locale = routing.defaultLocale;
  }
  const common = (await import(`../messages/${locale}/common.json`)).default;
  const errors = (await import(`../messages/${locale}/errors.json`)).default;
  const imports = (await import(`../messages/${locale}/imports.json`)).default;
  const map = (await import(`../messages/${locale}/map.json`)).default;
  const property = (await import(`../messages/${locale}/property.json`)).default;
  const valuation = (await import(`../messages/${locale}/valuation.json`)).default;
  const field = (await import(`../messages/${locale}/field.json`)).default;
  const admin = (await import(`../messages/${locale}/admin.json`)).default;
  return {
    locale,
    messages: {
      common,
      errors,
      imports,
      map,
      property,
      valuation,
      field,
      admin,
    },
  };
});
