import {
  BISSAU_BBOX,
  CONFIDENCE_LEVELS,
  PROPERTY_TYPES,
  type ConfidenceLevelParam,
  type PropertyTypeParam,
} from './constants';

export type MapUrlState = {
  bbox: string;
  type?: PropertyTypeParam;
  confidence?: ConfidenceLevelParam;
  page: number;
};

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

function parseBbox(raw: string | null): string {
  if (!raw) {
    return BISSAU_BBOX;
  }
  const parts = raw.split(',');
  if (parts.length !== 4) {
    return BISSAU_BBOX;
  }
  const numbers = parts.map((part) => Number(part.trim()));
  if (numbers.some((n) => !Number.isFinite(n))) {
    return BISSAU_BBOX;
  }
  let [west, south, east, north] = numbers;
  west = clamp(west, -180, 180);
  east = clamp(east, -180, 180);
  south = clamp(south, -90, 90);
  north = clamp(north, -90, 90);
  if (!(west < east) || !(south < north)) {
    return BISSAU_BBOX;
  }
  return [west, south, east, north].map((n) => n.toFixed(5)).join(',');
}

function parseType(raw: string | null): PropertyTypeParam | undefined {
  if (!raw) {
    return undefined;
  }
  return (PROPERTY_TYPES as readonly string[]).includes(raw)
    ? (raw as PropertyTypeParam)
    : undefined;
}

function parseConfidence(raw: string | null): ConfidenceLevelParam | undefined {
  if (!raw) {
    return undefined;
  }
  return (CONFIDENCE_LEVELS as readonly string[]).includes(raw)
    ? (raw as ConfidenceLevelParam)
    : undefined;
}

function parsePage(raw: string | null): number {
  if (!raw) {
    return 0;
  }
  const page = Number(raw);
  if (!Number.isInteger(page) || page < 0) {
    return 0;
  }
  return page;
}

export function parseMapSearchParams(params: URLSearchParams): MapUrlState {
  return {
    bbox: parseBbox(params.get('bbox')),
    type: parseType(params.get('type')),
    confidence: parseConfidence(params.get('confidence')),
    page: parsePage(params.get('page')),
  };
}

export function serializeMapSearchParams(state: MapUrlState): string {
  const params = new URLSearchParams();
  const bboxParts = state.bbox.split(',').map((part) => Number(part.trim()));
  const rounded = bboxParts.map((n) => n.toFixed(5)).join(',');
  params.set('bbox', rounded);
  if (state.type) {
    params.set('type', state.type);
  }
  if (state.confidence) {
    params.set('confidence', state.confidence);
  }
  if (state.page > 0) {
    params.set('page', String(state.page));
  }
  return params.toString();
}

export function withFiltersReset(
  state: MapUrlState,
  patch: Partial<Pick<MapUrlState, 'bbox' | 'type' | 'confidence'>>,
): MapUrlState {
  return {
    ...state,
    ...patch,
    page: 0,
  };
}
