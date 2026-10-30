import {Suspense} from 'react';
import {setRequestLocale} from 'next-intl/server';
import {MapView} from '@/components/map/MapView';

export default function MapPage({params}: {params: {locale: string}}) {
  setRequestLocale(params.locale);
  return (
    <Suspense fallback={<p>…</p>}>
      <MapView />
    </Suspense>
  );
}
