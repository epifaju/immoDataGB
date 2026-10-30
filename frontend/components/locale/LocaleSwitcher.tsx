'use client';

import {Link, usePathname} from '@/i18n/navigation';
import {routing} from '@/i18n/routing';

const labels = {
  pt: 'Português',
  fr: 'Français',
  en: 'English',
} as const;

export function LocaleSwitcher() {
  const pathname = usePathname();
  return (
    <nav>
      {routing.locales.map((locale) => (
        <Link key={locale} href={pathname} locale={locale}>
          {labels[locale]}
        </Link>
      ))}
    </nav>
  );
}
