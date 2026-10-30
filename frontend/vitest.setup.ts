import {createRequire} from 'node:module';
import {cleanup} from '@testing-library/react';
import {afterEach} from 'vitest';

const nodeRequire = createRequire(import.meta.url);
const nodeModule = nodeRequire('module') as {
  _load: (request: string, parent: unknown, isMain: boolean) => unknown;
};

// next-intl 3.26.5's CJS navigation calls require('next/navigation').
// That native require does not see vi.mock, so usePathname() is null and Link crashes.
// The locale test mocks the pathname as `/pt`; mirror that for the CJS require.
const originalLoad = nodeModule._load.bind(nodeModule);
nodeModule._load = (request, parent, isMain) => {
  const loaded = originalLoad(request, parent, isMain);
  if (request !== 'next/navigation' || loaded === null || typeof loaded !== 'object') {
    return loaded;
  }
  return new Proxy(loaded, {
    get(target, prop, receiver) {
      if (prop === 'usePathname') {
        return () => '/pt';
      }
      return Reflect.get(target, prop, receiver);
    },
  });
};

afterEach(() => {
  cleanup();
});
