const { getJson, nonEmpty } = require('./http');
const sourceconfig = require('./sourceconfig');

const KEY = 'bj';
const BASE_URL = 'https://forooshonline20.ir/wp-json/mapi/v1';
const HEADERS = { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36' };
// The API host answers 403 for its own uploads; the CDN mirror serves the same paths.
const IMAGE_REWRITE = ['https://forooshonline20.ir/wp-content/', 'https://seo2024.ir/wp-content/'];

const seriesCache = new Map();

function imageUrl(raw) {
  const url = nonEmpty(raw);
  const rewritten = sourceconfig.rewriteImage(KEY, url);
  if (rewritten !== url) return rewritten;
  return url.replace(IMAGE_REWRITE[0], IMAGE_REWRITE[1]);
}

async function call(path, cacheTtlMs = 0) {
  return getJson(`${sourceconfig.baseUrl(KEY, BASE_URL)}${path}`, {
    headers: sourceconfig.headers(KEY, HEADERS),
    timeoutMs: 15000,
    cacheTtlMs
  });
}

function parseMovieItem(raw) {
  const typeStr = nonEmpty(raw.type) || 'movie';
  const isSeries = typeStr === 'serie' || typeStr === 'series' || typeStr === 'tvshow';
  return {
    source: 'bj',
    id: raw.id,
    title: nonEmpty(raw.fa_title, raw.title) || 'بدون عنوان',
    image: imageUrl(nonEmpty(raw.thumbnail, raw.image)),
    type: isSeries ? 1 : 0,
    year: nonEmpty(raw.release) || null,
    hasSub: raw.subtitle !== false && raw.has_subtitle !== false,
    hasDub: Boolean(raw.dubbed || raw.has_dubbed),
    rating: nonEmpty(raw.imdb_rate) || null
  };
}

async function listEndpoint(path, cacheTtlMs = 3 * 60 * 1000) {
  const { json } = await call(path, cacheTtlMs);
  const data = json && json.data;
  return Array.isArray(data) ? data.map(parseMovieItem) : [];
}

function getMovies(page = 1) {
  return listEndpoint(`/post/movies?page=${page}&per_page=20`);
}

function getSeries(page = 1) {
  return listEndpoint(`/post/series?page=${page}&per_page=20`);
}

function getCartoons(page = 1) {
  return listEndpoint(`/post/cartoons?page=${page}&per_page=20`);
}

function getSuggestions() {
  return listEndpoint('/post/suggestions');
}

function getRecent(page = 1) {
  return getMovies(page);
}

async function search(query, page = 1) {
  const { json } = await call(`/post/search?search=${encodeURIComponent(query.trim())}&page=${page}&per_page=20`);
  const data = json && json.data;
  return Array.isArray(data) ? data.map(parseMovieItem) : [];
}

async function getHomeSections() {
  const [suggestions, movies, series, cartoons] = await Promise.all([
    getSuggestions(),
    getMovies(1),
    getSeries(1),
    getCartoons(1)
  ]);
  const sections = [];
  let id = 1;
  if (suggestions.length) sections.push({ id: id++, title: 'پیشنهادات ویژه (مشتری)', items: suggestions });
  if (movies.length) sections.push({ id: id++, title: 'جدیدترین فیلم‌های روز (مشتری)', items: movies });
  if (series.length) sections.push({ id: id++, title: 'برترین سریال‌های روز (مشتری)', items: series });
  if (cartoons.length) sections.push({ id: id++, title: 'انیمیشن و کارتون‌های منتخب (مشتری)', items: cartoons });
  return sections;
}

function matchNumber(text, pattern, fallback) {
  const match = new RegExp(pattern, 'i').exec(text || '');
  const value = match ? parseInt(match[1], 10) : NaN;
  return Number.isFinite(value) ? value : fallback;
}

async function getDetails(id) {
  const { json } = await call(`/post/${id}`);
  const d = json && json.data;
  if (!d) return null;

  const typeStr = nonEmpty(d.type) || 'movie';
  const isSeries = typeStr === 'serie' || typeStr === 'series' || typeStr === 'tvshow';
  const thumbnail = imageUrl(nonEmpty(d.thumbnail, d.image));
  const downloadLinks = Array.isArray(d.download_links) ? d.download_links : [];

  const seasons = [];
  const directQualities = [];

  if (isSeries) {
    const seasonNums = new Set();
    const epMap = new Map();

    downloadLinks.forEach((dl) => {
      const qualityName = nonEmpty(dl.quality) || 'کیفیت اصلی';
      const linkType = nonEmpty(dl.type).toUpperCase();
      const sNum = matchNumber(nonEmpty(dl.name), '(?:season|فصل)\\s*(\\d+)', 1);
      seasonNums.add(sNum);

      const items = Array.isArray(dl.items) ? dl.items : [];
      if (!epMap.has(sNum)) epMap.set(sNum, new Map());
      const seasonEpisodes = epMap.get(sNum);

      items.forEach((item, j) => {
        const epTitle = nonEmpty(item.title) || `Episode ${j + 1}`;
        const epNum = matchNumber(epTitle, '(?:episode|قسمت)\\s*(\\d+)', j + 1);
        const link = nonEmpty(item.link);
        if (!link) return;
        if (!seasonEpisodes.has(epNum)) seasonEpisodes.set(epNum, []);
        const qList = seasonEpisodes.get(epNum);
        qList.push({
          id: sNum * 1000 + epNum * 10 + qList.length,
          type: linkType || 'MP4',
          title: linkType ? `${qualityName} (${linkType})` : qualityName,
          size: 'مستقیم',
          directUrl: link
        });
      });
    });

    const sorted = seasonNums.size ? [...seasonNums].sort((a, b) => a - b) : [1];
    sorted.forEach((s) => seasons.push({ season: s, title: `فصل ${s}` }));

    const finalMap = {};
    epMap.forEach((epData, sNum) => {
      finalMap[sNum] = [...epData.keys()]
        .sort((a, b) => a - b)
        .map((epNum) => ({ episode: epNum, title: `قسمت ${epNum}`, qualities: epData.get(epNum) }));
    });
    if (Object.keys(finalMap).length) seriesCache.set(id, finalMap);
  } else {
    downloadLinks.forEach((dl, i) => {
      const link = nonEmpty(dl.dl_link);
      if (!link) return;
      const quality = nonEmpty(dl.quality_link) || 'کیفیت اصلی';
      const capacity = nonEmpty(dl.dl_capacity);
      const linkType = nonEmpty(dl.link_type).toUpperCase();
      const parts = [quality];
      if (linkType) parts.push(`(${linkType})`);
      if (capacity) parts.push(`— ${capacity}`);
      directQualities.push({
        id: i + 1,
        type: linkType || 'MP4',
        title: parts.join(' '),
        size: capacity || 'مستقیم',
        directUrl: link
      });
    });
  }

  return {
    source: 'bj',
    id: Number.isFinite(d.id) ? d.id : id,
    title: nonEmpty(d.fa_title, d.title) || 'بدون عنوان',
    image: thumbnail,
    banner: imageUrl(nonEmpty(d.background_image, d.background)) || thumbnail,
    type: isSeries ? 1 : 0,
    imdbRate: nonEmpty(d.imdb_rate) || null,
    duration: nonEmpty(d.runtime) ? `${nonEmpty(d.runtime)} دقیقه` : null,
    year: nonEmpty(d.release) || null,
    description: nonEmpty(d.fa_plot, d.plot, d.en_plot) || 'خلاصه داستانی ثبت نشده است.',
    seasons,
    directQualities
  };
}

async function getEpisodes(movieId, seasonNumber) {
  const cached = seriesCache.get(movieId);
  if (cached && cached[seasonNumber] && cached[seasonNumber].length) return cached[seasonNumber];

  const { json } = await call(`/post/${movieId}/season/${seasonNumber}`);
  const links = json && json.data && json.data.download_links;
  if (links && typeof links === 'object') {
    const epMap = new Map();
    Object.keys(links).forEach((qualityKey) => {
      const epArray = Array.isArray(links[qualityKey]) ? links[qualityKey] : [];
      epArray.forEach((ep, i) => {
        const epTitle = nonEmpty(ep.title) || `Episode ${i + 1}`;
        const epNum = matchNumber(epTitle, '(?:episode|قسمت)\\s*(\\d+)', i + 1);
        const link = nonEmpty(ep.link);
        if (!link) return;
        if (!epMap.has(epNum)) epMap.set(epNum, []);
        const qList = epMap.get(epNum);
        qList.push({
          id: seasonNumber * 1000 + epNum * 10 + qList.length,
          type: 'MP4',
          title: qualityKey,
          size: 'مستقیم',
          directUrl: link
        });
      });
    });

    if (epMap.size) {
      const episodes = [...epMap.keys()]
        .sort((a, b) => a - b)
        .map((epNum) => ({ episode: epNum, title: `قسمت ${epNum}`, qualities: epMap.get(epNum) }));
      const existing = seriesCache.get(movieId) || {};
      existing[seasonNumber] = episodes;
      seriesCache.set(movieId, existing);
      return episodes;
    }
  }

  await getDetails(movieId);
  const fallback = seriesCache.get(movieId);
  return (fallback && fallback[seasonNumber]) || [];
}

async function getQualities(movieId) {
  const detail = await getDetails(movieId);
  return (detail && detail.directQualities) || [];
}

module.exports = {
  key: 'bj',
  label: 'موتور BJ',
  getHomeSections,
  getRecent,
  getMovies,
  getSeries,
  getCartoons,
  search,
  getDetails,
  getEpisodes,
  getQualities
};
