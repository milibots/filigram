const { getJson, nonEmpty } = require('./http');

const BASE_URL = 'https://mihan-cdn.com';
const AUTH_TOKEN = 'j1LG8eYNnk0EBzTCRcXyo6kebJrnX6EQx6zsmFKv7e5077d7';

const HEADERS = {
  Authorization: `Bearer ${AUTH_TOKEN}`,
  Platform: 'android/6.4',
  'User-Agent': 'okhttp/5.5.0',
  'Content-Type': 'application/json; charset=UTF-8',
  Accept: 'application/json'
};

const seriesCache = new Map();

function call(path, options = {}) {
  return getJson(`${BASE_URL}${path}`, { headers: HEADERS, timeoutMs: 20000, ...options });
}

function parseMovieItem(raw) {
  const typeStr = nonEmpty(raw.type) || 'movie';
  const isSeries = typeStr === 'series' || typeStr === 'tvshow';
  return {
    source: 'nextmovie',
    id: raw.id,
    title: nonEmpty(raw.title) || 'بدون عنوان',
    image: nonEmpty(raw.poster),
    type: isSeries ? 1 : 0,
    year: nonEmpty(raw.year) || null,
    hasSub: nonEmpty(raw.sub_status) === 'sub',
    hasDub: Boolean(raw.is_dubbed),
    rating: nonEmpty(raw.imdb_rating) || null
  };
}

// The search endpoint rejects `type: "all"` and rejects `title` combined with `type`.
async function searchRequest(payload, page) {
  const { json } = await call(`/api/v3/search?page=${page}`, {
    method: 'POST',
    body: JSON.stringify(payload)
  });
  const items = json && json.data && json.data.items;
  return Array.isArray(items) ? items.map(parseMovieItem) : [];
}

function getRecent(page = 1) {
  return searchRequest({}, page);
}

function browse(kind, page = 1) {
  if (kind === 'movies') return searchRequest({ type: 'movie' }, page);
  if (kind === 'series') return searchRequest({ type: 'series' }, page);
  return searchRequest({}, page);
}

function search(query, page = 1) {
  return searchRequest({ title: query }, page);
}

async function fetchLinksAndSeasons(movieId) {
  const { json } = await call(`/api/movie/${movieId}/links`);
  const data = json && json.data;
  if (!data) return { qualities: [], seasons: [] };

  const qualities = [];
  const seasons = [];

  const movieLinks = Array.isArray(data.movie_links) ? data.movie_links : [];
  movieLinks.forEach((link, i) => {
    const url = nonEmpty(link.url);
    if (!url) return;
    const quality = nonEmpty(link.quality) || 'HD';
    const type = (nonEmpty(link.type) || 'mp4').toUpperCase();
    qualities.push({
      id: Number.isFinite(link.id) ? link.id : i + 1,
      type,
      title: `${quality} (${type})`,
      size: 'مستقیم',
      directUrl: url
    });
  });

  const seasonsArr = Array.isArray(data.seasons) ? data.seasons : [];
  if (seasonsArr.length) {
    const epMap = {};
    seasonsArr.forEach((season, s) => {
      const order = s + 1;
      seasons.push({ season: order, title: nonEmpty(season.title) || `فصل ${order}` });
      const epArr = Array.isArray(season.episodes) ? season.episodes : [];
      epMap[order] = epArr.map((ep, e) => {
        const epOrder = e + 1;
        const links = Array.isArray(ep.links) ? ep.links : [];
        const epQualities = links
          .map((l, li) => {
            const url = nonEmpty(l.url);
            if (!url) return null;
            const quality = nonEmpty(l.quality) || 'کیفیت اصلی';
            const type = (nonEmpty(l.type) || 'mp4').toUpperCase();
            return {
              id: Number.isFinite(l.id) ? l.id : epOrder * 100 + li,
              type,
              title: `${quality} (${type})`,
              size: 'مستقیم',
              directUrl: url
            };
          })
          .filter(Boolean);
        return {
          episode: epOrder,
          title: nonEmpty(ep.title) || `قسمت ${epOrder}`,
          qualities: epQualities
        };
      });
    });
    seriesCache.set(movieId, epMap);
  }

  return { qualities, seasons };
}

async function getDetails(movieId) {
  const { json } = await call(`/api/movie/${movieId}/details`, { cacheTtlMs: 10 * 60 * 1000 });
  const d = json && json.data;
  if (!d) return null;

  const typeStr = nonEmpty(d.type) || 'movie';
  const isSeries = typeStr === 'series' || typeStr === 'tvshow';
  const poster = nonEmpty(d.poster);
  const { qualities, seasons } = await fetchLinksAndSeasons(movieId);

  return {
    source: 'nextmovie',
    id: Number.isFinite(d.id) ? d.id : movieId,
    title: nonEmpty(d.title) || 'بدون عنوان',
    image: poster,
    banner: poster,
    type: isSeries ? 1 : 0,
    imdbRate: nonEmpty(d.imdb_rating) || null,
    duration: nonEmpty(d.duration) ? `${nonEmpty(d.duration)} دقیقه` : null,
    year: nonEmpty(d.year) || null,
    description: nonEmpty(d.description) || 'خلاصه داستانی ثبت نشده است.',
    seasons,
    directQualities: qualities
  };
}

async function getEpisodes(movieId, seasonNumber) {
  const cached = seriesCache.get(movieId);
  if (cached && cached[seasonNumber] && cached[seasonNumber].length) return cached[seasonNumber];
  await fetchLinksAndSeasons(movieId);
  const fresh = seriesCache.get(movieId);
  return (fresh && fresh[seasonNumber]) || [];
}

async function getQualities(movieId) {
  const { qualities } = await fetchLinksAndSeasons(movieId);
  return qualities;
}

async function getHomeSections() {
  const [mixed, movies, series] = await Promise.all([browse('all', 1), browse('movies', 1), browse('series', 1)]);
  const sections = [];
  let id = 1;
  if (mixed.length) sections.push({ id: id++, title: 'تازه‌ترین‌های فیلیگرام', items: mixed });
  if (movies.length) sections.push({ id: id++, title: 'جدیدترین فیلم‌ها', items: movies });
  if (series.length) sections.push({ id: id++, title: 'سریال‌های تازه', items: series });
  return sections;
}

module.exports = {
  key: 'nextmovie',
  label: 'نکست‌مووی',
  getHomeSections,
  getRecent,
  search,
  getDetails,
  getEpisodes,
  getQualities
};
