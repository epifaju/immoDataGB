'use client';

import {useMutation} from '@tanstack/react-query';
import {useTranslations} from 'next-intl';
import {useId, useState, type FormEvent} from 'react';
import {Link} from '@/i18n/navigation';
import {createFieldObservation} from '@/lib/api/field';
import type {
  FieldObservationCreated,
  FieldObservationRequest,
  OccupationStatus,
  ValuationPropertyType,
} from '@/lib/api/types';
import {PROPERTY_TYPES} from '@/lib/map/constants';

const FIELD_ERROR_CODES = [
  'FIELD_REQUEST_INVALID',
  'FIELD_ORGANISATION_MISSING',
  'NOT_FOUND',
  'HEALTH_DATABASE_UNAVAILABLE',
  'INTERNAL_ERROR',
] as const;

const OCCUPATIONS: OccupationStatus[] = ['occupied', 'vacant', 'unknown'];

type FormValues = {
  propertyType: ValuationPropertyType;
  title: string;
  occupationStatus: '' | OccupationStatus;
  notes: string;
  latitude: string;
  longitude: string;
};

export const FIELD_DEFAULTS: FormValues = {
  propertyType: 'house',
  title: '',
  occupationStatus: '',
  notes: '',
  latitude: '11.863',
  longitude: '-15.598',
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

type FieldName = 'latitude' | 'longitude' | 'propertyType';

export function validateFieldForm(values: FormValues):
  | {ok: true; request: FieldObservationRequest}
  | {ok: false; errors: Partial<Record<FieldName, true>>} {
  const latitude = parseNumber(values.latitude);
  const longitude = parseNumber(values.longitude);
  const errors: Partial<Record<FieldName, true>> = {};
  if (!PROPERTY_TYPES.includes(values.propertyType)) {
    errors.propertyType = true;
  }
  if (!inRange(latitude, -90, 90)) {
    errors.latitude = true;
  }
  if (!inRange(longitude, -180, 180)) {
    errors.longitude = true;
  }
  if (Object.keys(errors).length > 0) {
    return {ok: false, errors};
  }
  const request: FieldObservationRequest = {
    latitude: latitude!,
    longitude: longitude!,
    propertyType: values.propertyType,
  };
  const title = values.title.trim();
  if (title) {
    request.title = title.slice(0, 500);
  }
  if (values.occupationStatus) {
    request.occupationStatus = values.occupationStatus;
  }
  const notes = values.notes.trim();
  if (notes) {
    request.notes = notes.slice(0, 200);
  }
  return {ok: true, request};
}

export function FieldObservationForm() {
  const t = useTranslations('field');
  const tErrors = useTranslations('errors');
  const tMap = useTranslations('map');
  const formId = useId();
  const mutationsEnabled = process.env.NEXT_PUBLIC_FIELD_MUTATIONS_ENABLED === 'true';
  const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';

  const [values, setValues] = useState<FormValues>(FIELD_DEFAULTS);
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<FieldName, true>>>({});
  const [geoError, setGeoError] = useState(false);
  const [locating, setLocating] = useState(false);
  const [created, setCreated] = useState<FieldObservationCreated | null>(null);
  const [apiError, setApiError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: (request: FieldObservationRequest) => createFieldObservation(apiUrl, request),
    onSuccess: (response) => {
      if (response.status === 201 && response.body.success && response.body.data) {
        setCreated(response.body.data);
        setApiError(null);
        return;
      }
      const code = response.body.message ?? 'INTERNAL_ERROR';
      setApiError(
        (FIELD_ERROR_CODES as readonly string[]).includes(code)
          ? tErrors(code as (typeof FIELD_ERROR_CODES)[number])
          : tErrors('INTERNAL_ERROR'),
      );
    },
    onError: () => setApiError(tErrors('INTERNAL_ERROR')),
  });

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    const outcome = validateFieldForm(values);
    if (!outcome.ok) {
      setFieldErrors(outcome.errors);
      return;
    }
    setFieldErrors({});
    setCreated(null);
    mutation.mutate(outcome.request);
  }

  function useLocation() {
    if (!navigator.geolocation) {
      setGeoError(true);
      return;
    }
    setLocating(true);
    setGeoError(false);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setValues((current) => ({
          ...current,
          latitude: String(position.coords.latitude),
          longitude: String(position.coords.longitude),
        }));
        setLocating(false);
      },
      () => {
        setGeoError(true);
        setLocating(false);
      },
      {enableHighAccuracy: true, timeout: 10_000},
    );
  }

  if (!mutationsEnabled) {
    return <p className="rounded border border-amber-200 bg-amber-50 p-3 text-sm">{t('form.unavailable')}</p>;
  }

  if (created) {
    return (
      <section className="space-y-2 rounded border border-emerald-200 bg-emerald-50 p-4" data-testid="field-success">
        <h2 className="text-lg font-semibold">{t('success.title')}</h2>
        <p>
          {t('success.reference')}: <strong>{created.reference}</strong>
        </p>
        <p className="text-sm text-slate-700">{t('success.pending')}</p>
        <Link className="text-blue-700 underline" href={`/properties/${created.id}`}>
          {t('success.openDetail')}
        </Link>
      </section>
    );
  }

  return (
    <form className="max-w-lg space-y-3" onSubmit={onSubmit} noValidate>
      <div>
        <label className="block text-sm font-medium" htmlFor={`${formId}-type`}>
          {t('form.propertyType')}
        </label>
        <select
          id={`${formId}-type`}
          className="mt-1 w-full rounded border px-2 py-2"
          value={values.propertyType}
          onChange={(e) =>
            setValues((c) => ({...c, propertyType: e.target.value as ValuationPropertyType}))
          }
        >
          {PROPERTY_TYPES.map((type) => (
            <option key={type} value={type}>
              {tMap(`type.${type}`)}
            </option>
          ))}
        </select>
      </div>

      <div>
        <label className="block text-sm font-medium" htmlFor={`${formId}-title`}>
          {t('form.title')}
        </label>
        <input
          id={`${formId}-title`}
          className="mt-1 w-full rounded border px-2 py-2"
          value={values.title}
          onChange={(e) => setValues((c) => ({...c, title: e.target.value}))}
        />
      </div>

      <div>
        <label className="block text-sm font-medium" htmlFor={`${formId}-occ`}>
          {t('form.occupationStatus')}
        </label>
        <select
          id={`${formId}-occ`}
          className="mt-1 w-full rounded border px-2 py-2"
          value={values.occupationStatus}
          onChange={(e) =>
            setValues((c) => ({
              ...c,
              occupationStatus: e.target.value as FormValues['occupationStatus'],
            }))
          }
        >
          <option value="">{t('form.occupationNone')}</option>
          {OCCUPATIONS.map((status) => (
            <option key={status} value={status}>
              {t(`form.${status}`)}
            </option>
          ))}
        </select>
      </div>

      <div>
        <label className="block text-sm font-medium" htmlFor={`${formId}-notes`}>
          {t('form.notes')}
        </label>
        <textarea
          id={`${formId}-notes`}
          className="mt-1 w-full rounded border px-2 py-2"
          rows={3}
          value={values.notes}
          onChange={(e) => setValues((c) => ({...c, notes: e.target.value}))}
        />
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className="block text-sm font-medium" htmlFor={`${formId}-lat`}>
            {t('form.latitude')}
          </label>
          <input
            id={`${formId}-lat`}
            className="mt-1 w-full rounded border px-2 py-2"
            value={values.latitude}
            onChange={(e) => setValues((c) => ({...c, latitude: e.target.value}))}
            inputMode="decimal"
            aria-invalid={fieldErrors.latitude ? true : undefined}
          />
          {fieldErrors.latitude ? <p className="text-sm text-red-600">{t('form.required')}</p> : null}
        </div>
        <div>
          <label className="block text-sm font-medium" htmlFor={`${formId}-lng`}>
            {t('form.longitude')}
          </label>
          <input
            id={`${formId}-lng`}
            className="mt-1 w-full rounded border px-2 py-2"
            value={values.longitude}
            onChange={(e) => setValues((c) => ({...c, longitude: e.target.value}))}
            inputMode="decimal"
            aria-invalid={fieldErrors.longitude ? true : undefined}
          />
          {fieldErrors.longitude ? <p className="text-sm text-red-600">{t('form.required')}</p> : null}
        </div>
      </div>

      <button
        type="button"
        className="rounded border px-3 py-2 text-sm"
        onClick={useLocation}
        disabled={locating}
      >
        {locating ? t('form.locating') : t('form.useLocation')}
      </button>
      {geoError ? <p className="text-sm text-amber-700">{t('form.geoDenied')}</p> : null}

      {apiError ? <p className="text-sm text-red-600">{apiError}</p> : null}

      <button
        type="submit"
        className="rounded bg-slate-900 px-4 py-2 text-white disabled:opacity-50"
        disabled={mutation.isPending}
      >
        {mutation.isPending ? t('form.submitting') : t('form.submit')}
      </button>
    </form>
  );
}
