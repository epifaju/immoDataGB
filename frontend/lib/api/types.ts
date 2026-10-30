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

export type PropertiesResult = {
  status: number;
  body: ApiResponse<PageResponse<PropertyMapItem> | null>;
};
