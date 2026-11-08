/* Cache only static application shell. NEVER cache /api or queue timer writes. */
const CACHE = "worktime-shell-v9";
const SHELL = [
  "/", "/index.html", "/offline.html", "/static/styles.css", "/static/app.js",
  "/manifest.webmanifest", "/icons/icon-192.png", "/icons/icon-512.png"
];

self.addEventListener("install", (event) => {
  event.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(SHELL))
    .then(() => self.skipWaiting()));
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys().then((names) => Promise.all(names.filter((name) => name !== CACHE)
      .map((name) => caches.delete(name)))).then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (event) => {
  const request = event.request;
  const url = new URL(request.url);
  if (url.origin !== self.location.origin || request.method !== "GET") return;
  // Reports and employee state must never be stale; POST requests bypass the SW as well.
  if (url.pathname.startsWith("/api/")) return;

  if (request.mode === "navigate") {
    event.respondWith(fetch(request).catch(async () => {
      if (url.pathname === "/" || url.pathname === "/index.html") {
        const shell = await caches.match("/index.html");
        if (shell) return shell;
      }
      return caches.match("/offline.html");
    }));
    return;
  }
  if (!SHELL.includes(url.pathname)) return;
  event.respondWith(fetch(request).then((response) => {
    if (response.ok) {
      const copy = response.clone();
      event.waitUntil(caches.open(CACHE).then((cache) => cache.put(request, copy)));
    }
    return response;
  }).catch(() => caches.match(request)));
});
