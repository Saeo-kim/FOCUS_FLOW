// FOCUS FLOW service worker. Purpose: make the app installable and let the app shell open offline.
// It never caches API responses or signed-in pages, so account data and sessions stay live and per-user.
const VERSION = "focusflow-shell-v2";
const SHELL = ["/login", "/install.html", "/manifest.json", "/css/app.css", "/css/shell.css", "/css/login.css",
  "/css/member.css", "/css/admin.css", "/favicon.svg", "/icons/icon-192.png", "/icons/icon-512.png", "/icons/apple-touch-icon.png",
  "/js/app.js", "/js/login.js", "/js/shell.js", "/js/workspace.js", "/js/member.js", "/js/admin.js", "/js/pwa.js"];

self.addEventListener("install", (event) => {
  event.waitUntil(caches.open(VERSION).then((cache) => cache.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", (event) => {
  event.waitUntil(caches.keys()
    .then((keys) => Promise.all(keys.filter((k) => k !== VERSION).map((k) => caches.delete(k))))
    .then(() => self.clients.claim()));
});

self.addEventListener("fetch", (event) => {
  const request = event.request;
  const url = new URL(request.url);
  if (request.method !== "GET" || url.origin !== self.location.origin) return;
  // Account data, sessions and the API always go to the network. Nothing from them is stored.
  if (url.pathname.startsWith("/api/")) return;
  if (request.mode === "navigate") {
    // Pages that depend on the signed-in user are never stored: fall back to the sign-in shell only offline.
    event.respondWith(fetch(request).catch(() => caches.match("/login")));
    return;
  }
  // Static shell: network first so updates arrive, cached copy when offline.
  event.respondWith(fetch(request).then((response) => {
    if (response.ok && SHELL.includes(url.pathname)) {
      const copy = response.clone();
      caches.open(VERSION).then((cache) => cache.put(request, copy));
    }
    return response;
  }).catch(() => caches.match(request)));
});
