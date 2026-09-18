const { getJson } = require('./http');
const store = require('../store');

const CF_BASE_URL = 'https://filigramv1.miladjobs22.workers.dev';
const GITHUB_RAW_URL = 'https://raw.githubusercontent.com/milibots/filigram_config/refs/heads/main/data.json';
const ARVAN_BASE_URL = 'https://filmapi1.milaadfarzian-tnljt.arvanedge.ir';

const HEADERS = { 'User-Agent': 'Filigram-Desktop-Client/1.0', Accept: 'application/json' };

// Structure only: base urls, headers and tokens live in the remote config, and each
// engine module keeps its own built-in values as the last-resort fallback.
const DEFAULT_ORDER = [
  { key: 'bj', label: 'مشتری', planet: 'jupiter', enabled: true, order: 1 },
  { key: 'nextmovie', label: 'زحل', planet: 'saturn', enabled: true, order: 2 },
  { key: 'almasmovie', label: 'اورانوس', planet: 'uranus', enabled: true, order: 3 },
  { key: 'rezflix', label: 'نپتون', planet: 'neptune', enabled: true, order: 4 }
];

const DESKTOP_KEYS = new Set(DEFAULT_ORDER.map((s) => s.key));

let sources = new Map(DEFAULT_ORDER.map((s) => [s.key, s]));

function isUsable(list) {
  return Array.isArray(list) && list.some((s) => s && typeof s.key === 'string');
}

function adopt(list) {
  const merged = new Map(DEFAULT_ORDER.map((s) => [s.key, { ...s }]));
  list.forEach((remote) => {
    if (!remote || !DESKTOP_KEYS.has(remote.key)) return;
    merged.set(remote.key, { ...merged.get(remote.key), ...remote });
  });
  sources = merged;
}

async function fetchRemote() {
  const cf = await getJson(`${CF_BASE_URL}/api/sources?all=1`, { headers: HEADERS, timeoutMs: 4000 });
  if (isUsable(cf.json)) return cf.json;

  const gh = await getJson(GITHUB_RAW_URL, { headers: HEADERS, timeoutMs: 4000 });
  if (gh.json && isUsable(gh.json.sources)) return gh.json.sources;

  const arvan = await getJson(`${ARVAN_BASE_URL}/api/sources?all=1`, { headers: HEADERS, timeoutMs: 6000 });
  if (isUsable(arvan.json)) return arvan.json;

  return null;
}

async function load() {
  const cached = store.get('sourcesCache');
  if (isUsable(cached)) adopt(cached);

  const remote = await fetchRemote();
  if (remote) {
    adopt(remote);
    store.set('sourcesCache', remote);
  }
  return list();
}

function get(key) {
  return sources.get(key) || {};
}

function list() {
  return [...sources.values()].sort((a, b) => (a.order || 0) - (b.order || 0));
}

function enabledList() {
  return list().filter((s) => s.enabled !== false);
}

// Engines call these so a missing remote value always falls back to their built-in one.
function baseUrl(key, fallback) {
  const value = get(key).base_url;
  return typeof value === 'string' && value ? value.replace(/\/$/, '') : fallback;
}

function headers(key, fallback = {}) {
  const cfg = get(key);
  const merged = { ...fallback, ...(cfg.headers || {}) };
  if (cfg.cookies) merged.Cookie = cfg.cookies;
  return merged;
}

function auth(key) {
  return get(key).auth || {};
}

function rewriteImage(key, url) {
  const rules = get(key).image_rewrite;
  if (!Array.isArray(rules) || !url) return url;
  return rules.reduce((acc, rule) => {
    if (!Array.isArray(rule) || rule.length !== 2) return acc;
    return acc.split(rule[0]).join(rule[1]);
  }, url);
}

module.exports = { load, get, list, enabledList, baseUrl, headers, auth, rewriteImage };
