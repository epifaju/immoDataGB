import type {ReactNode} from 'react';
import {Suspense} from 'react';
import {NextIntlClientProvider} from 'next-intl';
import {getMessages, getTranslations, setRequestLocale} from 'next-intl/server';
import {notFound} from 'next/navigation';
import {QueryProvider} from '@/components/providers/QueryProvider';
import {LocaleSwitcher} from '@/components/locale/LocaleSwitcher';
import {Link} from '@/i18n/navigation';
import {routing} from '@/i18n/routing';
import '../globals.css';

export function generateStaticParams() {
  return routing.locales.map((locale) => ({locale}));
}

export default async function LocaleLayout({
  children,
  params,
}: {
  children: ReactNode;
  params: {locale: string};
}) {
  const {locale} = params;
  if (!routing.locales.includes(locale as (typeof routing.locales)[number])) {
    notFound();
  }
  setRequestLocale(locale);
  const messages = await getMessages();
  const t = await getTranslations('common');
  return (
    <html lang={locale}>
      <body>
        <NextIntlClientProvider messages={messages}>
          <QueryProvider>
            <header className="flex flex-wrap items-center gap-4 p-4">
              <p>{t('appName')}</p>
              <Link href="/map">{t('nav.map')}</Link>
              <Link href="/valuation">{t('nav.valuation')}</Link>
              <Link href="/admin/imports">{t('nav.imports')}</Link>
              <Link href="/admin/validation-queue">{t('nav.validationQueue')}</Link>
              <Link href="/admin/audit">{t('nav.audit')}</Link>
              <Link href="/field/new">{t('nav.field')}</Link>
              <Suspense fallback={null}>
                <LocaleSwitcher />
              </Suspense>
            </header>
            {children}
          </QueryProvider>
        </NextIntlClientProvider>
      </body>
    </html>
  );
}
