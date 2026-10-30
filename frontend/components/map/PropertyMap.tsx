'use client';

import {useEffect, useRef} from 'react';
import Map, {Marker, Popup, type MapRef} from 'react-map-gl/maplibre';
import type {PropertyMapItem} from '@/lib/api/types';
import {OSM_RASTER_STYLE} from '@/lib/map/constants';
import {PropertyPopup} from './PropertyPopup';
import 'maplibre-gl/dist/maplibre-gl.css';

const MARKER_COLOR: Record<PropertyMapItem['confidenceLevel'], string> = {
  low: '#b91c1c',
  medium: '#c2410c',
  high: '#15803d',
};

function parseBbox(bbox: string): [[number, number], [number, number]] {
  const [west, south, east, north] = bbox.split(',').map(Number);
  return [
    [west, south],
    [east, north],
  ];
}

export function PropertyMap({
  bbox,
  items,
  selected,
  onSelect,
  onClearSelection,
  onBboxChange,
}: {
  bbox: string;
  items: PropertyMapItem[];
  selected: PropertyMapItem | null;
  onSelect: (item: PropertyMapItem) => void;
  onClearSelection: () => void;
  onBboxChange: (bbox: string) => void;
}) {
  const mapRef = useRef<MapRef | null>(null);
  const ignoreMoveEnd = useRef(true);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const initialBounds = parseBbox(bbox);

  useEffect(() => {
    if (!selected || !mapRef.current) {
      return;
    }
    const map = mapRef.current.getMap();
    const point: [number, number] = [selected.longitude, selected.latitude];
    if (!map.getBounds().contains(point)) {
      map.easeTo({center: point, duration: 500});
    }
  }, [selected]);

  useEffect(() => {
    return () => {
      if (debounceRef.current) {
        clearTimeout(debounceRef.current);
      }
    };
  }, []);

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        onClearSelection();
      }
    }
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [onClearSelection]);

  return (
    <Map
      ref={mapRef}
      initialViewState={{
        bounds: initialBounds,
        fitBoundsOptions: {padding: 0},
      }}
      mapStyle={OSM_RASTER_STYLE}
      style={{width: '100%', height: '100%', minHeight: '480px'}}
      onClick={() => onClearSelection()}
      onMoveEnd={(event) => {
        if (ignoreMoveEnd.current) {
          ignoreMoveEnd.current = false;
          return;
        }
        const bounds = event.target.getBounds();
        const next = [
          bounds.getWest(),
          bounds.getSouth(),
          bounds.getEast(),
          bounds.getNorth(),
        ]
          .map((n) => n.toFixed(5))
          .join(',');
        if (debounceRef.current) {
          clearTimeout(debounceRef.current);
        }
        debounceRef.current = setTimeout(() => onBboxChange(next), 300);
      }}
    >
      {items.map((item) => (
        <Marker
          key={item.id}
          longitude={item.longitude}
          latitude={item.latitude}
          color={MARKER_COLOR[item.confidenceLevel]}
          onClick={(event) => {
            event.originalEvent.stopPropagation();
            onSelect(item);
          }}
        />
      ))}
      {selected ? (
        <Popup
          longitude={selected.longitude}
          latitude={selected.latitude}
          anchor="bottom"
          onClose={onClearSelection}
          closeOnClick={false}
        >
          <PropertyPopup item={selected} />
        </Popup>
      ) : null}
    </Map>
  );
}
