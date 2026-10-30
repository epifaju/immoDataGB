import {fireEvent, render, screen} from '@testing-library/react';
import {NextIntlClientProvider} from 'next-intl';
import {describe, expect, test, vi} from 'vitest';
import type {PageResponse, PropertyMapItem} from '@/lib/api/types';
import ptMap from '@/messages/pt/map.json';
import {PropertyList} from './PropertyList';

const item: PropertyMapItem = {
  id: '1',
  organisationId: 'a',
  reference: 'BIS-001',
  title: 'Moradia',
  propertyType: 'house',
  confidenceScore: 12,
  confidenceLevel: 'low',
  confidenceSummary: 'Fonte declarativa',
  longitude: -15.68,
  latitude: 11.82,
};

function renderList(
  page: PageResponse<PropertyMapItem> | null,
  onSelect = vi.fn(),
  onPageChange = vi.fn(),
) {
  return render(
    <NextIntlClientProvider locale="pt" messages={{map: ptMap}}>
      <PropertyList
        page={page}
        selectedId={null}
        onSelect={onSelect}
        onPageChange={onPageChange}
      />
    </NextIntlClientProvider>,
  );
}

describe('PropertyList', () => {
  test('shows empty state', () => {
    renderList({page: 0, size: 20, totalElements: 0, totalPages: 0, items: []});
    expect(screen.getByText('Nenhum imóvel nesta área.')).toBeTruthy();
  });

  test('renders range and pagination controls', () => {
    const onPageChange = vi.fn();
    renderList(
      {page: 0, size: 20, totalElements: 24, totalPages: 2, items: [item]},
      vi.fn(),
      onPageChange,
    );
    expect(screen.getByText('1–20 de 24')).toBeTruthy();
    expect((screen.getByText('Anterior') as HTMLButtonElement).disabled).toBe(true);
    fireEvent.click(screen.getByText('Seguinte'));
    expect(onPageChange).toHaveBeenCalledWith(1);
  });

  test('calls onSelect on item click', () => {
    const onSelect = vi.fn();
    renderList(
      {page: 0, size: 20, totalElements: 1, totalPages: 1, items: [item]},
      onSelect,
    );
    fireEvent.click(screen.getByText('Moradia'));
    expect(onSelect).toHaveBeenCalledWith(item);
  });
});
