import {Suspense} from 'react';
import {setRequestLocale} from 'next-intl/server';
import {PropertyDetailView} from '@/components/property/PropertyDetailView';

export default function PropertyDetailPage({
  params,
}: {
  params: {locale: string; id: string};
}) {
  setRequestLocale(params.locale);
  return (
    <Suspense fallback={<p>…</p>}>
      <PropertyDetailView id={params.id} />
    </Suspense>
  );
}
