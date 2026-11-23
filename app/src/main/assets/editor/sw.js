// Сервис-воркер: страницы и API всегда берутся с сервера (без кэша). Кэшируются только значки и манифест.
const C = 'lists-v9';
self.addEventListener('install', e => {
  e.waitUntil(caches.open(C).then(c => c.addAll(['manifest.json', 'icon.svg'])).catch(() => {}));
  self.skipWaiting();
});
self.addEventListener('activate', e => {
  e.waitUntil(caches.keys().then(k => Promise.all(k.filter(x => x !== C).map(x => caches.delete(x)))).then(() => clients.claim()));
});
self.addEventListener('fetch', e => {
  const r = e.request;
  if (r.method !== 'GET') return;
  const u = new URL(r.url);
  if (u.origin !== location.origin) return;
  if (!/\/(manifest\.json|icon\.svg|icon-\d+\.png)$/.test(u.pathname)) return;
  e.respondWith(
    fetch(r).then(res => {
      if (res.ok) { const cp = res.clone(); caches.open(C).then(c => c.put(r, cp)); }
      return res;
    }).catch(() => caches.match(r))
  );
});
