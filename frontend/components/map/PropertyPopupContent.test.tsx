import {render, screen} from '@testing-library/react';
import {NextIntlClientProvider} from 'next-intl';
import {describe, expect, test, vi} from 'vitest';
import type {PropertyMapItem} from '@/lib/api/types';
import ptMap from '@/messages/pt/map.json';
import {PropertyPopupContent} from './PropertyPopupContent';

vi.mock('next/navigation', () => ({
  usePathname: () => '/map',
  useRouter: () => ({push: vi.fn(), replace: vi.fn(), prefetch: vi.fn()}),
  useSearchParams: () => new URLSearchParams(),
}));

const item: PropertyMapItem = {
  id: '00000000-0000-0000-0001-000000000005',
  organisationId: 'a',
  reference: 'BIS-005',
  title: 'Apartamento Centro',
  propertyType: 'apartment',
  confidenceScore: 70,
  confidenceLevel: 'high',
  confidenceSummary: 'Várias fontes',
  longitude: -15.6,
  latitude: 11.86,
};

describe('PropertyPopupContent', () => {
  test('links to property detail with map query', () => {
    const mapQuery = 'bbox=-15.70000%2C11.80000%2C-15.50000%2C11.95000&type=apartment&page=1';
    render(
      <NextIntlClientProvider locale="pt" messages={{map: ptMap}}>
        <PropertyPopupContent item={item} mapQuery={mapQuery} />
      </NextIntlClientProvider>,
    );
    const link = screen.getByRole('link', {name: 'Ver ficha'});
    expect(link.getAttribute('href')).toBe(
      `/pt/properties/${item.id}?${mapQuery}`,
    );
  });

  test('links without query when mapQuery empty', () => {
    render(
      <NextIntlClientProvider locale="pt" messages={{map: ptMap}}>
        <PropertyPopupContent item={item} mapQuery="" />
      </NextIntlClientProvider>,
    );
    expect(screen.getByRole('link', {name: 'Ver ficha'}).getAttribute('href')).toBe(
      `/pt/properties/${item.id}`,
    );
  });
});
