'use client';

import {useSearchParams} from 'next/navigation';
import {Link, usePathname} from '@/i18n/navigation';
import {routing} from '@/i18n/routing';

const labels = {
  pt: 'Português',
  fr: 'Français',
  en: 'English',
} as const;

export function LocaleSwitcher() {
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const query = searchParams.toString();
  const href = query ? `${pathname}?${query}` : pathname;
  return (
    <nav>
      {routing.locales.map((locale) => (
        <Link key={locale} href={href} locale={locale}>
          {labels[locale]}
        </Link>
      ))}
    </nav>
  );
}
