import {render, screen} from '@testing-library/react';
import {NextIntlClientProvider} from 'next-intl';
import {expect, test, vi} from 'vitest';
import ptCommon from '../../messages/pt/common.json';
import ptErrors from '../../messages/pt/errors.json';
import {LocaleSwitcher} from './LocaleSwitcher';

vi.mock('next/navigation', () => ({
  usePathname: () => '/pt',
  useRouter: () => ({push: vi.fn(), replace: vi.fn(), prefetch: vi.fn()}),
  useSearchParams: () => new URLSearchParams(),
}));

test('links keep the current path and switch the locale prefix', () => {
  render(
    <NextIntlClientProvider locale="pt" messages={{common: ptCommon, errors: ptErrors}}>
      <LocaleSwitcher />
    </NextIntlClientProvider>,
  );
  expect(screen.getByRole('link', {name: 'Português'}).getAttribute('href')).toBe('/pt');
  expect(screen.getByRole('link', {name: 'Français'}).getAttribute('href')).toBe('/fr');
  expect(screen.getByRole('link', {name: 'English'}).getAttribute('href')).toBe('/en');
});
