import {getTranslations, setRequestLocale} from 'next-intl/server';
import {ImportsView} from '@/components/imports/ImportsView';

export default async function ImportsPage({params}: {params: {locale: string}}) {
  setRequestLocale(params.locale);
  const t = await getTranslations('imports');
  return (
    <main className="space-y-4 p-4">
      <h1 className="text-2xl font-semibold">{t('title')}</h1>
      <p className="text-sm text-slate-600">{t('intro')}</p>
      <ImportsView />
    </main>
  );
}
