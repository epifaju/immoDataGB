'use client';

import {Popup} from 'react-map-gl/maplibre';
import type {PropertyMapItem} from '@/lib/api/types';
import {PropertyPopupContent} from './PropertyPopupContent';

export function PropertyPopup({
  item,
  mapQuery,
  onClose,
}: {
  item: PropertyMapItem;
  mapQuery: string;
  onClose: () => void;
}) {
  return (
    <Popup
      longitude={item.longitude}
      latitude={item.latitude}
      anchor="bottom"
      onClose={onClose}
      closeOnClick={false}
    >
      <PropertyPopupContent item={item} mapQuery={mapQuery} />
    </Popup>
  );
}
