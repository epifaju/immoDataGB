import {render, screen} from '@testing-library/react';
import {NextIntlClientProvider} from 'next-intl';
import type {ReactElement} from 'react';
import {describe, expect, test} from 'vitest';
import ptMap from '@/messages/pt/map.json';
import {ConfidenceBadge} from './ConfidenceBadge';

function wrap(ui: ReactElement) {
  return render(
    <NextIntlClientProvider locale="pt" messages={{map: ptMap}}>
      {ui}
    </NextIntlClientProvider>,
  );
}

describe('ConfidenceBadge', () => {
  test('renders label score and color class per level', () => {
    const {rerender} = wrap(<ConfidenceBadge level="low" score={12} />);
    expect(screen.getByText('Baixa')).toBeTruthy();
    expect(screen.getByText('12/100')).toBeTruthy();
    expect(screen.getByText('Baixa').parentElement?.className).toContain('bg-red-100');

    rerender(
      <NextIntlClientProvider locale="pt" messages={{map: ptMap}}>
        <ConfidenceBadge level="medium" score={50} />
      </NextIntlClientProvider>,
    );
    expect(screen.getByText('Média').parentElement?.className).toContain('bg-orange-100');

    rerender(
      <NextIntlClientProvider locale="pt" messages={{map: ptMap}}>
        <ConfidenceBadge level="high" score={91} />
      </NextIntlClientProvider>,
    );
    expect(screen.getByText('Alta').parentElement?.className).toContain('bg-green-100');
  });
});
