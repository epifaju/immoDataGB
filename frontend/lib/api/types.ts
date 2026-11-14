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

export const DOCUMENT_TYPES = [
  'titre_foncier',
  'attestation_possession',
  'contrat_vente',
  'contrat_location',
  'plan_parcelle',
  'photo_terrain',
  'piece_identite_masquee',
  'acte_notarie',
  'document_administratif',
  'autre',
] as const;
export type DocumentType = (typeof DOCUMENT_TYPES)[number];

export type DocumentItem = {
  id: string;
  propertyId: string;
  documentType: string;
  originalFilename: string;
  contentType: string;
  sizeBytes: number;
  createdAt: string;
};

export type SignedUrlResult = {
  url: string;
  expiresAt: string;
};

export type PropertyDocumentsResult = {
  status: number;
  body: ApiResponse<DocumentItem[] | null>;
};

export type DocumentUploadResult = {
  status: number;
  body: ApiResponse<DocumentItem | null>;
};

export type SignedUrlResponse = {
  status: number;
  body: ApiResponse<SignedUrlResult | null>;
};

export type DocumentDeleteResult = {
  status: number;
  body: ApiResponse<null>;
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

export type ValuationPropertyType = 'house' | 'apartment' | 'land' | 'commercial';

export type EstimateRequest = {
  propertyType: ValuationPropertyType;
  areaSqm: number;
  latitude: number;
  longitude: number;
  radiusMeters: number;
  periodMonths: number;
};

export type ComparableItem = {
  id: string;
  propertyType: string;
  priceXof: number;
  areaSqm: number;
  pricePerSqm: number;
  observedAt: string;
  distanceMeters: number;
};

export type EstimateResult = {
  currency: string;
  medianTotal: number;
  rangeLow: number;
  rangeHigh: number;
  medianPricePerSqm: number;
  rangeLowPricePerSqm: number;
  rangeHighPricePerSqm: number;
  comparablesCount: number;
  comparablesRawCount: number;
  confidenceScore: number;
  confidenceLevel: 'low' | 'medium' | 'high';
  warning: string | null;
  comparables: ComparableItem[];
};

export type EstimateResponse = {
  status: number;
  body: ApiResponse<EstimateResult | null>;
};

export const IMPORT_JOB_STATUSES = ['queued', 'processing', 'completed', 'failed'] as const;

export type ImportJobState = (typeof IMPORT_JOB_STATUSES)[number];

export type ImportStartResult = {
  jobId: string;
  status: ImportJobState;
};

export type ImportJobStatus = {
  jobId: string;
  status: ImportJobState;
  originalFilename: string;
  totalRows: number;
  successRows: number;
  errorRows: number;
  errorMessage: string | null;
  createdAt: string;
};

export type ImportRowError = {
  rowNumber: number;
  code: string;
  message: string;
  rawExcerpt: string | null;
};

export type ImportReport = ImportJobStatus & {
  errors: ImportRowError[];
};

export type ImportStartResponse = {
  status: number;
  body: ApiResponse<ImportStartResult | null>;
};

export type ImportStatusResponse = {
  status: number;
  body: ApiResponse<ImportJobStatus | null>;
};

export type ImportReportResponse = {
  status: number;
  body: ApiResponse<ImportReport | null>;
};

export type ImportListResponse = {
  status: number;
  body: ApiResponse<PageResponse<ImportJobStatus> | null>;
};
export type OccupationStatus = 'occupied' | 'vacant' | 'unknown';

export type FieldObservationRequest = {
  latitude: number;
  longitude: number;
  propertyType: ValuationPropertyType;
  title?: string;
  occupationStatus?: OccupationStatus;
  notes?: string;
};

export type FieldObservationCreated = {
  id: string;
  reference: string;
  title: string;
  propertyType: ValuationPropertyType;
  validationStatus: 'pending_validation' | 'validated';
  latitude: number;
  longitude: number;
  confidenceScore: number;
  confidenceLevel: string;
};

export type FieldObservationResponse = {
  status: number;
  body: ApiResponse<FieldObservationCreated | null>;
};
