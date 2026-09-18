const { request, getJson, nonEmpty, str } = require('./http');

const BASE_URL = 'https://almasandroid.com/api/almas/v1';
const DEVICE_ID = '00d04fe2-dd3b-4d14-b2ba-88469cb8a01f';
const DEVICE_FINGERPRINT = '06073e34483010815f52e0b9cc1bb0378a2592cb8d4163759f51f513ab74f972';
const USER_AGENT = 'Dalvik/2.1.0 (Linux; U; Android 9; G576D Build/PQ3B.190801.04221524)';

let accessToken = '6X96p0fIP2Zzt2_9evF4lnKzaXtFsy_Axm4yLH5yHHRgHc9QEOV3fwErdb8uey_R';
let refreshToken = 'mIkn5vbe34uI3B5-XH3Yiql7MZ0hJrxmmsiibUN961qI0fPdar-jcc1V68OGp8zG';

const seriesCache = new Map();

function buildHeaders(includeAuth = true) {
  const headers = {
    Accept: 'application/json',
    'Content-Type': 'application/json; charset=utf-8',
    'User-Agent': USER_AGENT,
    'X-Almas-App-Version-Code': '8',
    'X-Almas-App-Version-Name': '3.0.0',
    'X-Almas-Client': 'android',
    'X-Almas-Device-Fingerprint-Hash': DEVICE_FINGERPRINT,
    'X-Almas-Device-Id': DEVICE_ID,
    'X-Almas-Device-Manufacturer': 'Google Phone',
    'X-Almas-Device-Model': 'G576D',
    'X-Almas-Device-Name': 'Google Phone G576D',
    'X-Almas-OS-Version': '9',
    'X-Almas-Platform': 'android_mobile'
  };
  if (includeAuth && accessToken) headers.Authorization = `Bearer ${accessToken}`;
  return headers;
}

async function refreshAccessToken() {
  const { status, text } = await request(`${BASE_URL}/auth/refresh/`, {
    method: 'POST',
    headers: buildHeaders(false),
    body: JSON.stringify({ refresh_token: refreshToken })
  });
  if (status !== 200) return false;
  try {
    const root = JSON.parse(text);
    const tokens = root && root.data && root.data.tokens;
    if (root.success && tokens && tokens.access_token) {
      accessToken = tokens.access_token;
      if (tokens.refresh_token) refreshToken = tokens.refresh_token;
      return true;
    }
  } catch (_) {}
  return false;
}

async function call(path, options = {}) {
  const { method = 'GET', body = null, includeAuth = true, retryOn401 = true, cacheTtlMs = 0 } = options;
  const url = `${BASE_URL}${path}`;
  const res = await getJson(url, {
    method,
    headers: buildHeaders(includeAuth),
    body,
    cacheTtlMs
  });
  if (res.status === 401 && retryOn401 && (await refreshAccessToken())) {
    return call(path, { ...options, retryOn401: false, cacheTtlMs: 0 });
  }
  return res;
}

function parseMovieItem(raw) {
  const typeStr = nonEmpty(raw.type) || 'movie';
  const isSeries = typeStr === 'tvshow' || typeStr === 'series';
  const title = raw.title || {};
  const poster = raw.poster || {};
  const badges = raw.badges || {};

  let year = nonEmpty(raw.year);
  if (!year && Array.isArray(raw.facts)) {
    const fact = raw.facts.find((f) => f && f.id === 'year');
    if (fact) year = nonEmpty(fact.value);
  }

  return {
    source: 'almasmovie',
    id: raw.id,
    title: nonEmpty(title.farsi, title.english, title.display, raw.title) || 'بدون عنوان',
    image: nonEmpty(poster.thumb220330, poster.full, poster.thumbnail),
    type: isSeries ? 1 : 0,
    year: year || null,
    hasSub: Boolean(badges.has_subtitle),
    hasDub: false,
    rating: null
  };
}

async function getHomeSections() {
  const { json } = await call('/home/', { cacheTtlMs: 5 * 60 * 1000 });
  const sections = json && json.data && json.data.sections;
  if (!Array.isArray(sections)) return [];
  const out = [];
  sections.forEach((sec, index) => {
    const title = nonEmpty(sec.title);
    const items = Array.isArray(sec.items) ? sec.items.map(parseMovieItem) : [];
    if (title && items.length) out.push({ id: index + 1, title, items });
  });
  return out;
}

async function getSectionPosts(sectionId = 'new_movie', page = 1, perPage = 24) {
  const { json } = await call(
    `/post-list/?section_id=${encodeURIComponent(sectionId)}&source=section_query&sort=default&page=${page}&per_page=${perPage}`
  );
  const items = json && json.data && json.data.items;
  return Array.isArray(items) ? items.map(parseMovieItem) : [];
}

function getRecent(page = 1) {
  return getSectionPosts('new_movie', page, 24);
}

async function search(query, page = 1) {
  const { json } = await call(
    `/search/?q=${encodeURIComponent(query)}&type=all&sort=relevance&page=${page}&per_page=24`
  );
  const items = json && json.data && json.data.items;
  return Array.isArray(items) ? items.map(parseMovieItem) : [];
}

function qualityId(url, fallback) {
  let hash = 0;
  for (let i = 0; i < url.length; i += 1) {
    hash = (hash * 31 + url.charCodeAt(i)) & 0x7fffffff;
  }
  return hash || fallback;
}

async function fetchDownloads(postId) {
  const { json } = await call(`/posts/${postId}/downloads/?include_locked=1`);
  const downloads = json && json.data && json.data.downloads;
  if (!downloads) return { directQualities: [], seasons: [], episodesBySeason: {} };

  if (Array.isArray(downloads.items)) {
    const directQualities = downloads.items
      .map((item, i) => {
        const url = nonEmpty(item.url);
        if (!url) return null;
        return {
          id: qualityId(url, i + 1),
          type: nonEmpty(item.resolution) || '1080p',
          title: nonEmpty(item.label) || 'کیفیت اصلی',
          size: nonEmpty(item.size),
          directUrl: url
        };
      })
      .filter(Boolean);
    return { directQualities, seasons: [], episodesBySeason: {} };
  }

  if (Array.isArray(downloads.seasons)) {
    const seasons = [];
    const episodesBySeason = {};
    downloads.seasons.forEach((season, sIndex) => {
      const order = Number.isFinite(season.season_order) ? season.season_order : sIndex + 1;
      seasons.push({ season: order, title: `فصل ${nonEmpty(season.name) || order}` });

      const byEpisode = new Map();
      const qualities = Array.isArray(season.qualities) ? season.qualities : [];
      qualities.forEach((q) => {
        const qName = nonEmpty(q.name) || 'کیفیت';
        const episodes = Array.isArray(q.episodes) ? q.episodes : [];
        episodes.forEach((ep, eIndex) => {
          const epOrder = Number.isFinite(ep.episode_order) ? ep.episode_order : eIndex + 1;
          const url = nonEmpty(ep.download_url);
          if (!url) return;
          if (!byEpisode.has(epOrder)) byEpisode.set(epOrder, []);
          byEpisode.get(epOrder).push({
            id: qualityId(url, eIndex + 1),
            type: 'لینک مستقیم',
            title: qName,
            size: nonEmpty(ep.size),
            directUrl: url
          });
        });
      });

      episodesBySeason[order] = [...byEpisode.keys()]
        .sort((a, b) => a - b)
        .map((epOrder) => ({
          episode: epOrder,
          title: `قسمت ${epOrder}`,
          qualities: byEpisode.get(epOrder)
        }));
    });
    return { directQualities: [], seasons, episodesBySeason };
  }

  return { directQualities: [], seasons: [], episodesBySeason: {} };
}

async function getDetails(postId, mediaType = 'movie') {
  const { json } = await call(`/posts/${postId}/`, { cacheTtlMs: 10 * 60 * 1000 });
  const data = json && json.data;
  if (!data) return null;

  const typeStr = nonEmpty(data.type) || mediaType;
  const isSeries = typeStr === 'tvshow' || typeStr === 'series' || mediaType === 'tvshow';
  const title = data.title || {};
  const poster = data.poster || (data.media && data.media.poster) || {};
  const backdrop = data.backdrop || (data.media && data.media.backdrop) || {};
  const summaries = data.summaries || {};
  const ratings = data.ratings || {};

  let year = null;
  let duration = null;
  if (Array.isArray(data.facts)) {
    data.facts.forEach((f) => {
      if (!f) return;
      if (f.id === 'year') year = nonEmpty(f.value) || null;
      if (f.id === 'runtime') duration = nonEmpty(f.value) || null;
    });
  }

  const image = nonEmpty(poster.full, poster.thumb220330, poster.thumbnail);
  const downloads = await fetchDownloads(postId);
  if (isSeries) seriesCache.set(postId, downloads.episodesBySeason);

  return {
    source: 'almasmovie',
    id: postId,
    title: nonEmpty(title.farsi, title.english, title.display, data.wordpress_title) || 'عنوان نامشخص',
    image,
    banner: nonEmpty(backdrop.large, backdrop.full) || image,
    type: isSeries ? 1 : 0,
    imdbRate: nonEmpty(ratings.imdb) || null,
    duration,
    year,
    description: nonEmpty(summaries.display, summaries.farsi, summaries.english) || null,
    seasons: isSeries ? downloads.seasons : [],
    directQualities: isSeries ? [] : downloads.directQualities
  };
}

async function getEpisodes(postId, seasonNumber) {
  const cached = seriesCache.get(postId);
  if (cached && cached[seasonNumber] && cached[seasonNumber].length) return cached[seasonNumber];
  const downloads = await fetchDownloads(postId);
  seriesCache.set(postId, downloads.episodesBySeason);
  return downloads.episodesBySeason[seasonNumber] || [];
}

async function getQualities(postId) {
  const downloads = await fetchDownloads(postId);
  return downloads.directQualities;
}

async function getGenres() {
  const { json } = await call('/taxonomies/genre/terms/?page=1&per_page=50&hide_empty=1', {
    includeAuth: false,
    cacheTtlMs: 60 * 60 * 1000
  });
  const terms = json && json.data && json.data.terms;
  return Array.isArray(terms)
    ? terms.map((t) => ({ slug: nonEmpty(t.slug), name: nonEmpty(t.name, t.title) })).filter((t) => t.slug && t.name)
    : [];
}

async function getPostsByGenre(termSlug, page = 1) {
  const { json } = await call(
    `/taxonomies/genre/terms/${encodeURIComponent(termSlug)}/posts/?page=${page}&per_page=24&sort=modified`
  );
  const items = json && json.data && json.data.items;
  return Array.isArray(items) ? items.map(parseMovieItem) : [];
}

async function getComments(postId, page = 1) {
  const { json } = await call(`/posts/${postId}/comments/?page=${page}&per_page=8`);
  const comments = json && json.data && json.data.comments;
  if (!Array.isArray(comments)) return [];
  return comments.map((c) => ({
    body: nonEmpty(c.body, c.content),
    author: nonEmpty(c.author && c.author.name, c.author_name, c.member && c.member.name) || 'کاربر فیلیگرام',
    date: nonEmpty(c.created_at, c.date)
  }));
}

module.exports = {
  key: 'almasmovie',
  label: 'الماس‌مووی',
  getHomeSections,
  getRecent,
  getSectionPosts,
  search,
  getDetails,
  getEpisodes,
  getQualities,
  getGenres,
  getPostsByGenre,
  getComments,
  str
};
