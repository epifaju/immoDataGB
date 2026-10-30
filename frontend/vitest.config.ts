import path from 'node:path';
import react from '@vitejs/plugin-react';
import type {Plugin} from 'vite';
import {defineConfig} from 'vitest/config';

const nextIntlEsmEntries: Record<string, string> = {
  'next-intl': 'index.react-client.js',
  'next-intl/navigation': 'navigation.react-client.js',
  'next-intl/routing': 'routing.js',
};

const nextNavigationExports = [
  'notFound',
  'permanentRedirect',
  'redirect',
  'useParams',
  'usePathname',
  'useRouter',
];

const nextIntlNavigationId = '\0next-intl-next-navigation';

// next-intl's default CJS entries require('next/navigation') natively, which vi.mock cannot reach.
// Its ESM build imports next/navigation through Vite instead. That import goes through a module
// that reads each export on call, because next-intl reads `redirect` at load time and vi.mock
// throws for exports a test's factory leaves out; those fall back to the real Next.js export.
function nextIntlEsm(): Plugin {
  const esmDir = path.resolve(__dirname, 'node_modules/next-intl/dist/esm');
  return {
    name: 'next-intl-esm',
    enforce: 'pre',
    resolveId(source, importer) {
      const entry = nextIntlEsmEntries[source];
      if (entry) {
        return path.join(esmDir, entry);
      }
      if (source === 'next/navigation' && importer?.replaceAll('\\', '/').includes('/node_modules/next-intl/')) {
        return nextIntlNavigationId;
      }
      return null;
    },
    load(id) {
      if (id !== nextIntlNavigationId) {
        return null;
      }
      const forwarders = nextNavigationExports.map(
        (name) =>
          `export function ${name}(...args) { return ('${name}' in mocked ? mocked : actual).${name}(...args); }`,
      );
      return [
        "import * as mocked from 'next/navigation';",
        "import * as actual from 'next/dist/client/components/navigation.js';",
        ...forwarders,
      ].join('\n');
    },
  };
}

export default defineConfig({
  plugins: [nextIntlEsm(), react()],
  test: {
    environment: 'jsdom',
    setupFiles: ['./vitest.setup.ts'],
    server: {
      deps: {
        inline: ['next-intl'],
      },
    },
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname),
    },
  },
});
