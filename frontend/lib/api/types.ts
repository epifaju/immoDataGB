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
