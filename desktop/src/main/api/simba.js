const { getJson, nonEmpty } = require('./http');
const sourceconfig = require('./sourceconfig');

const KEY = 'simba';
const BASE_URL = 'http://fitlinerim.info';
const STATIC_KEY = '4F5A9C3D9A86FA54EACEDDD635185';
const HEADERS = {
  'User-Agent': 'okhttp/4.9.3',
  Accept: 'application/json, text/html, video/*, */*',
  'Accept-Encoding': 'gzip, deflate',
  Connection: 'keep-alive'
};
const itemCache = new Map();
const seasonCache = new Map();

function call(path) {
  return getJson(`${sourceconfig.baseUrl(KEY, BASE_URL)}${path}`, {
    headers: sourceconfig.headers(KEY, HEADERS), timeoutMs: 20000
  });
}

function unwrap(json, ...keys) {
  if (Array.isArray(json)) return json;
  if (!json || typeof json !== 'object') return [];
  if (json.code != null && !['0', '1', '200'].includes(String(json.code))) return [];
  for (const key of keys) if (Array.isArray(json[key])) return json[key];
  return Array.isArray(json.values) ? json.values : [];
}

function absolute(url) {
  if (!url) return '';
  try { return new URL(url, `${sourceconfig.baseUrl(KEY, BASE_URL)}/`).href; } catch (_) { return url; }
}

function item(raw, type) {
  itemCache.set(Number(raw.id), raw);
  return {
    source: KEY, id: Number(raw.id), title: nonEmpty(raw.title) || 'بدون عنوان',
    image: absolute(nonEmpty(raw.image)), type,
    year: raw.year == null ? null : String(raw.year),
    rating: raw.imdb == null ? null : String(raw.imdb)
  };
}

async function getMovies() {
  const { json } = await call(`/api/movie/by/filtres/0/created/0/${STATIC_KEY}/`);
  return unwrap(json, 'movies', 'data').map((x) => item(x, 0));
}

async function getSeries() {
  const { json } = await call(`/api/serie/by/filtres/0/created/0/${STATIC_KEY}/`);
  return unwrap(json, 'series', 'data').map((x) => item(x, 1));
}

async function getRecent() { return [...await getMovies(), ...await getSeries()]; }
async function browse(kind) { return kind === 'series' ? getSeries() : kind === 'movies' ? getMovies() : getRecent(); }
async function search(query) {
  const list = await getRecent();
  const q = String(query || '').trim().toLocaleLowerCase('fa');
  return q ? list.filter((x) => x.title.toLocaleLowerCase('fa').includes(q)) : list;
}

async function loadSeasons(id) {
  if (seasonCache.has(id)) return seasonCache.get(id);
  const { json } = await call(`/api/season/by/serie/${id}/${STATIC_KEY}/`);
  const list = unwrap(json, 'seasons', 'data');
  seasonCache.set(id, list);
  return list;
}

function qualities(raw) {
  const sources = Array.isArray(raw && raw.sources) ? raw.sources : [];
  return sources.map((s, i) => {
    const url = absolute(s.url);
    const quality = s.quality == null ? 'کیفیت اصلی' : String(s.quality);
    return {
      id: Number(s.id) || i + 1,
      type: (nonEmpty(s.type) || (url.includes('.m3u8') ? 'HLS' : 'MP4')).toUpperCase(),
      title: /^\d+$/.test(quality) ? `${quality}p` : quality,
      size: nonEmpty(s.size) || 'مستقیم', directUrl: url
    };
  }).filter((q) => q.directUrl);
}

async function getDetails(id) {
  if (!itemCache.has(Number(id))) await getRecent();
  const d = itemCache.get(Number(id));
  if (!d) return null;
  const isSeries = d.is_serial === true || d.type === 'serie';
  const rawSeasons = isSeries ? await loadSeasons(Number(id)) : [];
  return {
    source: KEY, id: Number(id), title: nonEmpty(d.title) || 'بدون عنوان',
    image: absolute(nonEmpty(d.image)), banner: absolute(nonEmpty(d.cover, d.image)), type: isSeries ? 1 : 0,
    imdbRate: d.imdb == null ? null : String(d.imdb), duration: null,
    year: d.year == null ? null : String(d.year), description: nonEmpty(d.description) || 'خلاصه داستانی ثبت نشده است.',
    seasons: rawSeasons.map((s, i) => ({ season: Number(s.id) || i + 1, title: nonEmpty(s.title) || `فصل ${i + 1}` })),
    directQualities: isSeries ? [] : qualities(d)
  };
}

async function getEpisodes(id, seasonNumber) {
  const seasons = await loadSeasons(Number(id));
  const season = seasons.find((s) => Number(s.id) === Number(seasonNumber)) || seasons[Number(seasonNumber) - 1];
  const episodes = season && Array.isArray(season.episodes) ? season.episodes : [];
  return episodes.map((ep, i) => ({
    episode: Number(ep.id) || i + 1,
    title: nonEmpty(ep.title) || `قسمت ${i + 1}`,
    qualities: qualities(ep)
  }));
}

async function getQualities(id) {
  if (!itemCache.has(Number(id))) await getMovies();
  return qualities(itemCache.get(Number(id)));
}
async function getHomeSections() {
  const [movies, series] = await Promise.all([getMovies(), getSeries()]);
  return [
    movies.length ? { id: 1, title: 'فیلم‌های سیاره مریخ', items: movies } : null,
    series.length ? { id: 2, title: 'سریال‌های سیاره مریخ', items: series } : null
  ].filter(Boolean);
}

module.exports = { key: KEY, label: 'Simba', getHomeSections, getRecent, getMovies, getSeries, browse, search, getDetails, getEpisodes, getQualities };
