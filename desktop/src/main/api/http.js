const memCache = new Map();

function cacheGet(key, ttlMs) {
  const hit = memCache.get(key);
  if (!hit) return null;
  if (Date.now() - hit.at > ttlMs) {
    memCache.delete(key);
    return null;
  }
  return hit.body;
}

function cachePut(key, body) {
  memCache.set(key, { at: Date.now(), body });
}

async function request(url, options = {}) {
  const {
    method = 'GET',
    headers = {},
    body = null,
    timeoutMs = 20000,
    cacheTtlMs = 0
  } = options;

  const key = `${method} ${url}`;
  if (cacheTtlMs > 0 && method === 'GET') {
    const cached = cacheGet(key, cacheTtlMs);
    if (cached !== null) return { status: 200, text: cached };
  }

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch(url, { method, headers, body, signal: controller.signal });
    const text = await res.text();
    if (cacheTtlMs > 0 && method === 'GET' && res.status === 200 && text) {
      cachePut(key, text);
    }
    return { status: res.status, text };
  } catch (err) {
    return { status: -1, text: '', error: String(err && err.message ? err.message : err) };
  } finally {
    clearTimeout(timer);
  }
}

async function getJson(url, options = {}) {
  const { status, text, error } = await request(url, options);
  if (status !== 200 || !text) return { status, json: null, error };
  try {
    return { status, json: JSON.parse(text) };
  } catch (err) {
    return { status, json: null, error: 'invalid json' };
  }
}

function str(value) {
  if (value === null || value === undefined) return '';
  return String(value);
}

function nonEmpty(...values) {
  for (const v of values) {
    const s = str(v).trim();
    if (s && s !== 'null' && s !== 'undefined') return s;
  }
  return '';
}

module.exports = { request, getJson, str, nonEmpty };
