import {getTranslations, setRequestLocale} from 'next-intl/server';
import {AuditView} from '@/components/admin/AuditView';

export default async function AuditPage({params}: {params: {locale: string}}) {
  setRequestLocale(params.locale);
  const t = await getTranslations('admin');
  return (
    <main className="space-y-4 p-4">
      <h1 className="text-2xl font-semibold">{t('audit.title')}</h1>
      <p className="text-sm text-slate-600">{t('audit.intro')}</p>
      <AuditView />
    </main>
  );
}
