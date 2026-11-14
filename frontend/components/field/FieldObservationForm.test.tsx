import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {NextIntlClientProvider} from 'next-intl';
import {afterEach, beforeEach, describe, expect, test, vi} from 'vitest';
import enErrors from '@/messages/en/errors.json';
import enField from '@/messages/en/field.json';
import enMap from '@/messages/en/map.json';
import {FIELD_DEFAULTS, FieldObservationForm, validateFieldForm} from './FieldObservationForm';

vi.mock('@/lib/api/field', () => ({
  createFieldObservation: vi.fn(),
}));

vi.mock('@/i18n/navigation', () => ({
  Link: ({href, children}: {href: string; children: React.ReactNode}) => <a href={href}>{children}</a>,
}));

import {createFieldObservation} from '@/lib/api/field';

const createMock = vi.mocked(createFieldObservation);

function renderForm() {
  const client = new QueryClient({defaultOptions: {mutations: {retry: false}}});
  return render(
    <QueryClientProvider client={client}>
      <NextIntlClientProvider locale="en" messages={{field: enField, errors: enErrors, map: enMap}}>
        <FieldObservationForm />
      </NextIntlClientProvider>
    </QueryClientProvider>,
  );
}

describe('validateFieldForm', () => {
  test('accepts defaults', () => {
    const result = validateFieldForm(FIELD_DEFAULTS);
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.request.latitude).toBe(11.863);
      expect(result.request.longitude).toBe(-15.598);
    }
  });

  test('rejects invalid latitude', () => {
    const result = validateFieldForm({...FIELD_DEFAULTS, latitude: '95'});
    expect(result.ok).toBe(false);
  });
});

describe('FieldObservationForm', () => {
  const originalEnv = process.env.NEXT_PUBLIC_FIELD_MUTATIONS_ENABLED;

  beforeEach(() => {
    process.env.NEXT_PUBLIC_FIELD_MUTATIONS_ENABLED = 'true';
    process.env.NEXT_PUBLIC_API_URL = 'http://localhost:8081';
    createMock.mockReset();
  });

  afterEach(() => {
    process.env.NEXT_PUBLIC_FIELD_MUTATIONS_ENABLED = originalEnv;
  });

  test('hides form when mutations flag is off', () => {
    process.env.NEXT_PUBLIC_FIELD_MUTATIONS_ENABLED = 'false';
    renderForm();
    expect(screen.getByText(enField.form.unavailable)).toBeTruthy();
    expect(screen.queryByRole('button', {name: enField.form.submit})).toBeNull();
  });

  test('submits and shows success with detail link', async () => {
    createMock.mockResolvedValue({
      status: 201,
      body: {
        success: true,
        data: {
          id: '00000000-0000-0000-0001-00000000fld1',
          reference: 'FLD-20261114-abcd1234',
          title: 'Campo',
          propertyType: 'house',
          validationStatus: 'pending_validation',
          latitude: 11.863,
          longitude: -15.598,
          confidenceScore: 12,
          confidenceLevel: 'low',
        },
        message: null,
        meta: {},
      },
    });
    renderForm();
    fireEvent.click(screen.getByRole('button', {name: enField.form.submit}));
    await waitFor(() => expect(screen.getByTestId('field-success')).toBeTruthy());
    expect(screen.getByText('FLD-20261114-abcd1234')).toBeTruthy();
    expect(screen.getByRole('link', {name: enField.success.openDetail}).getAttribute('href')).toBe(
      '/properties/00000000-0000-0000-0001-00000000fld1',
    );
  });

  test('fills coordinates from geolocation', async () => {
    const getCurrentPosition = vi.fn((success: PositionCallback) => {
      success({
        coords: {
          latitude: 11.9,
          longitude: -15.55,
          accuracy: 10,
          altitude: null,
          altitudeAccuracy: null,
          heading: null,
          speed: null,
          toJSON() {
            return this;
          },
        },
        timestamp: Date.now(),
        toJSON() {
          return this;
        },
      } as GeolocationPosition);
    });
    Object.defineProperty(global.navigator, 'geolocation', {
      configurable: true,
      value: {getCurrentPosition},
    });
    renderForm();
    fireEvent.click(screen.getByRole('button', {name: enField.form.useLocation}));
    await waitFor(() => {
      expect((screen.getByLabelText(enField.form.latitude) as HTMLInputElement).value).toBe('11.9');
      expect((screen.getByLabelText(enField.form.longitude) as HTMLInputElement).value).toBe('-15.55');
    });
  });
});
