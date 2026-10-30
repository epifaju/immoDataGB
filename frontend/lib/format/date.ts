export function formatIsoDate(iso: string, locale: string): string {
  const [year, month, day] = iso.split('-').map(Number);
  if (!year || !month || !day) {
    return iso;
  }
  const date = new Date(Date.UTC(year, month - 1, day));
  return new Intl.DateTimeFormat(locale, {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    timeZone: 'UTC',
  }).format(date);
}
