const { getJson, nonEmpty } = require('./http');
const sourceconfig = require('./sourceconfig');

const KEY = 'rezflix';
const BASE_URL = 'http://server-win-iran.info';
const TOKEN = '4F5A9C3D9A86FA54EACEDDD635185';
const HEADERS = { 'User-Agent': 'okhttp/4.12.0' };

function base() {
  return sourceconfig.baseUrl(KEY, BASE_URL);
}

function token() {
  return sourceconfig.auth(KEY).token || TOKEN;
}

function headers() {
  return sourceconfig.headers(KEY, HEADERS);
}

function parseMovieItem(raw) {
  const isSeries = nonEmpty(raw.type).toLowerCase() === 'serie';
  return {
    source: 'rezflix',
    id: raw.id,
    title: nonEmpty(raw.title) || 'بدون عنوان',
    image: nonEmpty(raw.image),
    type: isSeries ? 1 : 0,
    year: nonEmpty(raw.year) || null,
    hasSub: false,
    hasDub: false,
    rating: nonEmpty(raw.imdb) || null
  };
}

async function getMovies(page = 1) {
  const { json } = await getJson(`${base()}/api/movie/by/filtres/0/created/0/${token()}/?page=${page}`, {
    headers: headers(),
    timeoutMs: 12000,
    cacheTtlMs: 3 * 60 * 1000
  });
  return Array.isArray(json) ? json.map(parseMovieItem) : [];
}

async function search(query, page = 1) {
  const { json } = await getJson(
    `${base()}/api-user/newapi/like.php?action=search-movie&q=${encodeURIComponent(query)}&pageno=${page}`,
    { headers: headers(), timeoutMs: 12000 }
  );
  return Array.isArray(json) ? json.map(parseMovieItem) : [];
}

async function getDetails(id) {
  const { json } = await getJson(`${base()}/api/movie/by/${id}/${token()}/`, {
    headers: headers(),
    timeoutMs: 12000,
    cacheTtlMs: 10 * 60 * 1000
  });
  if (!json) return null;
  const isSeries = nonEmpty(json.type).toLowerCase() === 'serie';
  const image = nonEmpty(json.image);

  const directQualities = [];
  const sources = Array.isArray(json.sources) ? json.sources : [];
  sources.forEach((s, i) => {
    const url = nonEmpty(s.url, s.link, s.file);
    // Rezflix pads items with trailer files (often on dead hosts): they are not real links.
    if (!url || /trailer|teaser/i.test(url)) return;
    directQualities.push({
      id: i + 1,
      type: (nonEmpty(s.type) || 'MP4').toUpperCase(),
      title: nonEmpty(s.label, s.quality) || 'کیفیت اصلی',
      size: 'مستقیم',
      directUrl: url
    });
  });

  const streamUrl = nonEmpty(json.streamlink, json.stream_link, json.url);
  if (!directQualities.length && streamUrl) {
    directQualities.push({ id: 1, type: 'MP4', title: 'پخش مستقیم', size: 'مستقیم', directUrl: streamUrl });
  }

  return {
    source: 'rezflix',
    id,
    title: nonEmpty(json.title) || 'بدون عنوان',
    image,
    banner: nonEmpty(json.cover) || image,
    type: isSeries ? 1 : 0,
    imdbRate: nonEmpty(json.imdb) || null,
    duration: nonEmpty(json.duration) || null,
    year: nonEmpty(json.year) || null,
    description: nonEmpty(json.description) || 'خلاصه داستانی ثبت نشده است.',
    seasons: isSeries
      ? [...groupEpisodes(directQualities).keys()].sort((a, b) => a - b).map((n) => ({ season: n, title: `فصل ${n}` }))
      : [],
    directQualities: isSeries && groupEpisodes(directQualities).size ? [] : directQualities
  };
}

/** Rezflix lists series files flat; the season/episode is only encoded in the file name (S01E02). */
function groupEpisodes(directQualities) {
  const seasons = new Map();
  directQualities.forEach((q) => {
    const m = /S(\d{1,2})[\s._-]*E(\d{1,3})/i.exec(decodeURIComponent(q.directUrl || '').replace(/%(?![0-9a-f]{2})/gi, '%25'));
    if (!m) return;
    const season = Number(m[1]);
    const episode = Number(m[2]);
    if (!seasons.has(season)) seasons.set(season, new Map());
    const eps = seasons.get(season);
    if (!eps.has(episode)) eps.set(episode, []);
    eps.get(episode).push(q);
  });
  return seasons;
}

async function getHomeSections() {
  const items = await getMovies(1);
  if (!items.length) return [];
  return [{ id: 1, title: 'تازه‌ها (نپتون)', items }];
}

module.exports = {
  key: 'rezflix',
  label: 'رزفلیکس',
  getHomeSections,
  getRecent: getMovies,
  search,
  getDetails,
  getEpisodes: async (id, season) => {
    const { json } = await getJson(`${base()}/api/movie/by/${id}/${token()}/`, {
      headers: headers(),
      timeoutMs: 12000,
      cacheTtlMs: 10 * 60 * 1000
    });
    const sources = (json && Array.isArray(json.sources) ? json.sources : [])
      .map((x, i) => ({
        id: i + 1,
        type: (nonEmpty(x.type) || 'MP4').toUpperCase(),
        title: nonEmpty(x.label, x.quality) || 'کیفیت اصلی',
        size: 'مستقیم',
        directUrl: nonEmpty(x.url, x.link, x.file)
      }))
      .filter((q) => q.directUrl && !/trailer|teaser/i.test(q.directUrl));
    const eps = groupEpisodes(sources).get(Number(season)) || new Map();
    return [...eps.keys()]
      .sort((a, b) => a - b)
      .map((n) => ({ episode: n, title: `قسمت ${n}`, qualities: eps.get(n) }));
  },
  getQualities: async (id) => {
    const detail = await getDetails(id);
    return (detail && detail.directQualities) || [];
  }
};
