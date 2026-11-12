export type HealthView = {
  api: string;
  database: string;
  postgis: string | null;
};

export type ApiResponse<T> = {
  success: boolean;
  data: T;
  message: string | null;
  meta: Record<string, unknown>;
};

export type HealthResult = {
  status: number;
  body: ApiResponse<HealthView | null>;
};

export type PageResponse<T> = {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  items: T[];
};

export type PropertyMapItem = {
  id: string;
  organisationId: string;
  reference: string;
  title: string;
  propertyType: string;
  confidenceScore: number;
  confidenceLevel: 'low' | 'medium' | 'high';
  confidenceSummary: string;
  longitude: number;
  latitude: number;
};

export const DOC_STATUSES = [
  'none',
  'declared',
  'partial',
  'verified',
  'litigious',
] as const;
export type DocStatus = (typeof DOC_STATUSES)[number];

export const SOURCE_TYPES = [
  'agency_listing',
  'field_observation',
  'scanned_doc',
  'institutional',
  'third_party_user',
  'partner',
  'open_layer',
] as const;
export type SourceType = (typeof SOURCE_TYPES)[number];

export type PropertyDetail = PropertyMapItem & {
  docStatus: string;
  addressText: string | null;
};

export type SourceItem = {
  id: string;
  sourceType: string;
  label: string | null;
  observedAt: string | null;
  isVerified: boolean;
  weight: number;
};

export type PropertiesResult = {
  status: number;
  body: ApiResponse<PageResponse<PropertyMapItem> | null>;
};

export type PropertyDetailResult = {
  status: number;
  body: ApiResponse<PropertyDetail | null>;
};

export type PropertySourcesResult = {
  status: number;
  body: ApiResponse<SourceItem[] | null>;
};
