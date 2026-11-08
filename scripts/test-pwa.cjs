// Dependency-free PWA contract checks: node --test scripts/test-pwa.cjs
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../src/main/resources/static');

function workerWithMockNetwork({ offline = false, hasShell = false } = {}) {
  const handlers = {};
  const self = {
    location: { origin: 'https://worktime.example.test' },
    addEventListener: (name, handler) => { handlers[name] = handler; },
    skipWaiting: () => Promise.resolve(),
    clients: { claim: () => Promise.resolve() },
  };
  const caches = {
    match: async (url) => url === "/index.html"
      ? (hasShell ? { appShell: true } : undefined)
      : { offlinePage: true },
    open: async () => ({
      addAll: async () => {},
      put: async () => {},
    }),
    keys: async () => [],
    delete: async () => true,
  };
  const fetch = async () => {
    if (offline) throw new Error('Disconnected');
    return { ok: true, clone() { return this; } };
  };
  vm.runInNewContext(fs.readFileSync(path.join(root, 'service-worker.js'), 'utf8'),
    { self, caches, fetch, URL, Promise });
  return handlers;
}

function fetchEvent(handlers, pathname, method = 'GET', mode = 'cors') {
  const event = {
    request: { url: `https://worktime.example.test${pathname}`, method, mode },
    response: undefined,
    respondWith(promise) { this.response = promise; },
    waitUntil() {},
  };
  handlers.fetch(event);
  return event;
}

test('manifest is installable and includes existing 192 and 512 PNG icons', () => {
  const manifest = JSON.parse(fs.readFileSync(path.join(root, 'manifest.webmanifest'), 'utf8'));
  assert.equal(manifest.display, 'standalone');
  assert.equal(manifest.start_url, '/');
  for (const size of [192, 512]) {
    const icon = manifest.icons.find((i) => i.sizes === `${size}x${size}`);
    assert.ok(icon, `missing ${size}px icon`);
    const bytes = fs.readFileSync(path.join(root, icon.src.slice(1)));
    assert.equal(bytes.subarray(1, 4).toString('ascii'), 'PNG');
    assert.equal(bytes.readUInt32BE(16), size);
    assert.equal(bytes.readUInt32BE(20), size);
  }
});

test('service worker never caches API reads or intercepts timer writes', () => {
  const handlers = workerWithMockNetwork();
  assert.equal(fetchEvent(handlers, '/api/reports').response, undefined);
  assert.equal(fetchEvent(handlers, '/api/employees/1/timer/start', 'POST').response, undefined);
  assert.equal(fetchEvent(handlers, '/api/employees/1/timer/stop', 'POST').response, undefined);
  assert.equal(fetchEvent(handlers, '/api/sessions/active').response, undefined);
  assert.equal(fetchEvent(handlers, '/api/sessions/1/stop', 'POST').response, undefined);
});

test('offline navigation restores the app shell so it can show the saved timer', async () => {
  const handlers = workerWithMockNetwork({ offline: true, hasShell: true });
  const event = fetchEvent(handlers, '/', 'GET', 'navigate');
  assert.equal((await event.response).appShell, true);
});

test('offline navigation uses the cached offline page', async () => {
  const handlers = workerWithMockNetwork({ offline: true });
  const event = fetchEvent(handlers, '/', 'GET', 'navigate');
  assert.ok(event.response);
  assert.equal((await event.response).offlinePage, true);
});
