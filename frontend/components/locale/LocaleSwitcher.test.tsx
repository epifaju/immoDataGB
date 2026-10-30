import {render, screen} from '@testing-library/react';
import {NextIntlClientProvider} from 'next-intl';
import {expect, test, vi} from 'vitest';
import ptCommon from '../../messages/pt/common.json';
import ptErrors from '../../messages/pt/errors.json';
import {LocaleSwitcher} from './LocaleSwitcher';

vi.mock('next/navigation', () => ({
  usePathname: () => '/map',
  useRouter: () => ({push: vi.fn(), replace: vi.fn(), prefetch: vi.fn()}),
  useSearchParams: () => new URLSearchParams('bbox=-15.70,11.80,-15.50,11.95&type=house'),
}));

test('links keep the current path, query string, and switch the locale prefix', () => {
  render(
    <NextIntlClientProvider locale="pt" messages={{common: ptCommon, errors: ptErrors}}>
      <LocaleSwitcher />
    </NextIntlClientProvider>,
  );
  expect(screen.getByRole('link', {name: 'Português'}).getAttribute('href')).toBe(
    '/pt/map?bbox=-15.70%2C11.80%2C-15.50%2C11.95&type=house',
  );
  expect(screen.getByRole('link', {name: 'Français'}).getAttribute('href')).toBe(
    '/fr/map?bbox=-15.70%2C11.80%2C-15.50%2C11.95&type=house',
  );
  expect(screen.getByRole('link', {name: 'English'}).getAttribute('href')).toBe(
    '/en/map?bbox=-15.70%2C11.80%2C-15.50%2C11.95&type=house',
  );
});
