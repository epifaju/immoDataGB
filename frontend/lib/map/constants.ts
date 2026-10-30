export const BISSAU_BBOX = '-15.70,11.80,-15.50,11.95' as const;

export const OSM_RASTER_STYLE = {
  version: 8 as const,
  sources: {
    osm: {
      type: 'raster' as const,
      tiles: ['https://tile.openstreetmap.org/{z}/{x}/{y}.png'],
      tileSize: 256,
      attribution: '© OpenStreetMap contributors',
      maxzoom: 19,
    },
  },
  layers: [
    {
      id: 'osm',
      type: 'raster' as const,
      source: 'osm',
    },
  ],
};

export const PROPERTY_TYPES = ['house', 'apartment', 'land', 'commercial'] as const;
export const CONFIDENCE_LEVELS = ['low', 'medium', 'high'] as const;

export type PropertyTypeParam = (typeof PROPERTY_TYPES)[number];
export type ConfidenceLevelParam = (typeof CONFIDENCE_LEVELS)[number];
