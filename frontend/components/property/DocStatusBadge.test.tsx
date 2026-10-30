import {render, screen} from '@testing-library/react';
import {NextIntlClientProvider} from 'next-intl';
import type {ReactElement} from 'react';
import {describe, expect, test} from 'vitest';
import ptProperty from '@/messages/pt/property.json';
import {DocStatusBadge} from './DocStatusBadge';

function wrap(ui: ReactElement) {
  return render(
    <NextIntlClientProvider locale="pt" messages={{property: ptProperty}}>
      {ui}
    </NextIntlClientProvider>,
  );
}

const cases = [
  {status: 'none', label: 'Sem documentação', className: 'bg-slate-100'},
  {status: 'declared', label: 'Declarada', className: 'bg-blue-100'},
  {status: 'partial', label: 'Parcial', className: 'bg-violet-100'},
  {status: 'verified', label: 'Verificada', className: 'bg-cyan-100'},
  {status: 'litigious', label: 'Em litígio', className: 'bg-pink-100'},
] as const;

describe('DocStatusBadge', () => {
  test.each(cases)('renders $status with label and color', ({status, label, className}) => {
    wrap(<DocStatusBadge status={status} />);
    const el = screen.getByText(label);
    expect(el.className).toContain(className);
  });

  test('falls back to raw value for unknown status', () => {
    wrap(<DocStatusBadge status="mystery" />);
    expect(screen.getByText('mystery')).toBeTruthy();
  });
});
