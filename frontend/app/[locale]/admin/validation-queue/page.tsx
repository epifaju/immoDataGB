import {getTranslations, setRequestLocale} from 'next-intl/server';
import {ValidationQueueView} from '@/components/admin/ValidationQueueView';

export default async function ValidationQueuePage({params}: {params: {locale: string}}) {
  setRequestLocale(params.locale);
  const t = await getTranslations('admin');
  return (
    <main className="space-y-4 p-4">
      <h1 className="text-2xl font-semibold">{t('queue.title')}</h1>
      <p className="text-sm text-slate-600">{t('queue.intro')}</p>
      <ValidationQueueView />
    </main>
  );
}
