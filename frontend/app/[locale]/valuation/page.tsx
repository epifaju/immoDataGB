import {getTranslations, setRequestLocale} from 'next-intl/server';
import {ValuationForm} from '@/components/valuation/ValuationForm';

export default async function ValuationPage({params}: {params: {locale: string}}) {
  setRequestLocale(params.locale);
  const t = await getTranslations('valuation');
  return (
    <main className="space-y-4 p-4">
      <h1 className="text-2xl font-semibold">{t('title')}</h1>
      <p className="text-sm text-slate-600">{t('intro')}</p>
      <ValuationForm />
    </main>
  );
}
