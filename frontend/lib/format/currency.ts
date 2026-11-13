export function formatXof(amount: number, locale: string): string {
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency: 'XOF',
    maximumFractionDigits: 0,
  }).format(amount);
}
