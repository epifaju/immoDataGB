import {render, screen} from '@testing-library/react';
import {NextIntlClientProvider} from 'next-intl';
import {describe, expect, test} from 'vitest';
import type {SourceItem} from '@/lib/api/types';
import ptProperty from '@/messages/pt/property.json';
import {SourceList} from './SourceList';

function renderList(sources: SourceItem[]) {
  return render(
    <NextIntlClientProvider locale="pt" messages={{property: ptProperty}}>
      <SourceList sources={sources} />
    </NextIntlClientProvider>,
  );
}

describe('SourceList', () => {
  test('shows empty state', () => {
    renderList([]);
    expect(screen.getByText('Nenhuma fonte registada.')).toBeTruthy();
  });

  test('uses label as title and type as secondary when label present', () => {
    renderList([
      {
        id: '1',
        sourceType: 'institutional',
        label: 'Cadastro municipal',
        observedAt: '2026-03-04',
        isVerified: true,
        weight: 3,
      },
    ]);
    expect(screen.getByText('Cadastro municipal')).toBeTruthy();
    expect(screen.getByText('Fonte institucional')).toBeTruthy();
    expect(screen.getByText(/Observada em/)).toBeTruthy();
    expect(screen.getByText('Verificada')).toBeTruthy();
    expect(screen.getByText('Peso: 3')).toBeTruthy();
  });

  test('uses translated type as title when label missing', () => {
    renderList([
      {
        id: '2',
        sourceType: 'field_observation',
        label: null,
        observedAt: null,
        isVerified: false,
        weight: 2,
      },
    ]);
    expect(screen.getByText('Observação de campo')).toBeTruthy();
    expect(screen.getByText('Não verificada')).toBeTruthy();
    expect(screen.getByText('Peso: 2')).toBeTruthy();
  });

  test('falls back to raw type for unknown sourceType', () => {
    renderList([
      {
        id: '3',
        sourceType: 'custom_feed',
        label: null,
        observedAt: null,
        isVerified: false,
        weight: 1,
      },
    ]);
    expect(screen.getByText('custom_feed')).toBeTruthy();
    expect(screen.getByText('Peso: 1')).toBeTruthy();
  });
});
