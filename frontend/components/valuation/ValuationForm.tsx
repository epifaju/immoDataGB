'use client';

import {useMutation} from '@tanstack/react-query';
import {useTranslations} from 'next-intl';
import {useId, useState, type FormEvent} from 'react';
import {estimateValuation} from '@/lib/api/valuation';
import type {EstimateRequest, EstimateResult, ValuationPropertyType} from '@/lib/api/types';
import {PROPERTY_TYPES} from '@/lib/map/constants';
import {ValuationResultView} from './ValuationResultView';

const VALUATION_ERROR_CODES = [
  'VALUATION_REQUEST_INVALID',
  'VALUATION_NO_COMPARABLES',
  'HEALTH_DATABASE_UNAVAILABLE',
  'INTERNAL_ERROR',
] as const;

const MAX_AREA_SQM = 1_000_000;

type FieldName = 'areaSqm' | 'latitude' | 'longitude' | 'radiusMeters' | 'periodMonths';

type FormValues = {
  propertyType: ValuationPropertyType;
  areaSqm: string;
  latitude: string;
  longitude: string;
  radiusMeters: string;
  periodMonths: string;
};

export const VALUATION_DEFAULTS: FormValues = {
  propertyType: 'apartment',
  areaSqm: '80',
  latitude: '11.863',
  longitude: '-15.598',
  radiusMeters: '3000',
  periodMonths: '24',
};

function parseNumber(raw: string): number | null {
  if (raw.trim() === '') {
    return null;
  }
  const value = Number(raw);
  return Number.isFinite(value) ? value : null;
}

function inRange(value: number | null, min: number, max: number): value is number {
  return value !== null && value >= min && value <= max;
}

function isInteger(value: number | null, min: number, max: number): value is number {
  return inRange(value, min, max) && Number.isInteger(value);
}

type ValidationOutcome =
  | {ok: true; request: EstimateRequest}
  | {ok: false; errors: Partial<Record<FieldName, true>>};

export function validateValuationForm(values: FormValues): ValidationOutcome {
  const areaSqm = parseNumber(values.areaSqm);
  const latitude = parseNumber(values.latitude);
  const longitude = parseNumber(values.longitude);
  const radiusMeters = parseNumber(values.radiusMeters);
  const periodMonths = parseNumber(values.periodMonths);
  const errors: Partial<Record<FieldName, true>> = {};

  if (areaSqm === null || areaSqm <= 0 || areaSqm > MAX_AREA_SQM) {
    errors.areaSqm = true;
  }
  if (!inRange(latitude, -90, 90)) {
    errors.latitude = true;
  }
  if (!inRange(longitude, -180, 180)) {
    errors.longitude = true;
  }
  if (!isInteger(radiusMeters, 100, 50_000)) {
    errors.radiusMeters = true;
  }
  if (!isInteger(periodMonths, 1, 120)) {
    errors.periodMonths = true;
  }
  if (
    Object.keys(errors).length > 0 ||
    areaSqm === null ||
    latitude === null ||
    longitude === null ||
    radiusMeters === null ||
    periodMonths === null
  ) {
    return {ok: false, errors};
  }
  return {
    ok: true,
    request: {
      propertyType: values.propertyType,
      areaSqm,
      latitude,
      longitude,
      radiusMeters,
      periodMonths,
    },
  };
}

export function ValuationForm({apiUrl}: {apiUrl?: string}) {
  const t = useTranslations('valuation');
  const tMap = useTranslations('map');
  const tErrors = useTranslations('errors');
  const tCommon = useTranslations('common');
  const baseUrl = apiUrl ?? process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';
  const ids = {
    propertyType: useId(),
    areaSqm: useId(),
    latitude: useId(),
    longitude: useId(),
    radiusMeters: useId(),
    periodMonths: useId(),
  };
  const [values, setValues] = useState<FormValues>(VALUATION_DEFAULTS);
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<FieldName, true>>>({});
  const [apiError, setApiError] = useState<string | null>(null);
  const [result, setResult] = useState<EstimateResult | null>(null);

  const mutation = useMutation({
    mutationFn: async (request: EstimateRequest): Promise<EstimateResult> => {
      const {body} = await estimateValuation(baseUrl, request);
      if (!body.success || body.data === null) {
        throw new Error(body.message ?? 'INTERNAL_ERROR');
      }
      return body.data;
    },
    onSuccess: (data) => {
      setApiError(null);
      setResult(data);
    },
    onError: (err: Error) => {
      setResult(null);
      setApiError(
        (VALUATION_ERROR_CODES as readonly string[]).includes(err.message)
          ? tErrors(err.message as (typeof VALUATION_ERROR_CODES)[number])
          : tCommon('error'),
      );
    },
  });

  function update<K extends keyof FormValues>(key: K, value: FormValues[K]) {
    setValues((current) => ({...current, [key]: value}));
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const outcome = validateValuationForm(values);
    if (!outcome.ok) {
      setFieldErrors(outcome.errors);
      return;
    }
    setFieldErrors({});
    setApiError(null);
    mutation.mutate(outcome.request);
  }

  function numberField(name: FieldName, step: string) {
    return (
      <div className="flex flex-col gap-1">
        <label htmlFor={ids[name]} className="text-sm">
          {t(`form.${name}`)}
        </label>
        <input
          id={ids[name]}
          type="number"
          step={step}
          value={values[name]}
          aria-invalid={fieldErrors[name] ? true : undefined}
          onChange={(event) => update(name, event.target.value)}
          className="rounded border border-slate-300 px-2 py-1"
        />
        {fieldErrors[name] ? (
          <p role="alert" className="text-sm text-red-700">
            {t(`validation.${name}`)}
          </p>
        ) : null}
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <form
        onSubmit={handleSubmit}
        noValidate
        className="grid max-w-xl gap-3 rounded border border-slate-200 p-4"
      >
        <div className="flex flex-col gap-1">
          <label htmlFor={ids.propertyType} className="text-sm">
            {t('form.propertyType')}
          </label>
          <select
            id={ids.propertyType}
            value={values.propertyType}
            onChange={(event) => update('propertyType', event.target.value as ValuationPropertyType)}
            className="rounded border border-slate-300 px-2 py-1"
          >
            {PROPERTY_TYPES.map((type) => (
              <option key={type} value={type}>
                {tMap(`type.${type}`)}
              </option>
            ))}
          </select>
        </div>
        {numberField('areaSqm', 'any')}
        {numberField('latitude', 'any')}
        {numberField('longitude', 'any')}
        {numberField('radiusMeters', '1')}
        {numberField('periodMonths', '1')}
        <button
          type="submit"
          className="rounded bg-blue-700 px-3 py-1 text-sm text-white"
          disabled={mutation.isPending}
        >
          {mutation.isPending ? t('form.submitting') : t('form.submit')}
        </button>
      </form>
      {apiError ? <p role="alert">{apiError}</p> : null}
      {result ? <ValuationResultView result={result} /> : null}
    </div>
  );
}
