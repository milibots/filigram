const almas = require('./almas');
const bj = require('./bj');
const nextmovie = require('./nextmovie');
const rezflix = require('./rezflix');
const remoteconfig = require('./remoteconfig');

const engines = { almasmovie: almas, bj, nextmovie, rezflix };

const ENGINE_ORDER = ['bj', 'nextmovie', 'almasmovie', 'rezflix'];

function engine(key) {
  return engines[key] || bj;
}

// Engines are presented to users as planets; the keys stay tied to the source APIs.
const PLANETS = {
  bj: 'مشتری',
  nextmovie: 'زحل',
  almasmovie: 'اورانوس',
  rezflix: 'نپتون'
};

function engineList() {
  return ENGINE_ORDER.map((key) => ({ key, label: PLANETS[key] || engines[key].label }));
}

async function homeSections(sourceKey) {
  const api = engine(sourceKey);
  const sections = await api.getHomeSections();
  if (sections.length) return sections;
  const recent = await api.getRecent(1);
  return recent.length ? [{ id: 1, title: 'جدیدترین‌ها', items: recent }] : [];
}

async function listing(sourceKey, kind, page) {
  const api = engine(sourceKey);
  if (api.browse) return api.browse(kind, page);
  if (kind === 'series') {
    if (api.getSeries) return api.getSeries(page);
    const items = await api.getRecent(page);
    return items.filter((i) => i.type === 1);
  }
  if (kind === 'movies') {
    if (api.getMovies) return api.getMovies(page);
    const items = await api.getRecent(page);
    return items.filter((i) => i.type === 0);
  }
  return api.getRecent(page);
}

function search(sourceKey, query, page) {
  return engine(sourceKey).search(query, page);
}

function details(sourceKey, id, type) {
  const api = engine(sourceKey);
  if (sourceKey === 'almasmovie') return api.getDetails(id, type === 1 ? 'tvshow' : 'movie');
  return api.getDetails(id);
}

function episodes(sourceKey, id, season) {
  return engine(sourceKey).getEpisodes(id, season);
}

function qualities(sourceKey, id) {
  return engine(sourceKey).getQualities(id);
}

function genres() {
  return almas.getGenres();
}

function postsByGenre(slug, page) {
  return almas.getPostsByGenre(slug, page);
}

function comments(sourceKey, id, page) {
  if (sourceKey !== 'almasmovie') return Promise.resolve([]);
  return almas.getComments(id, page);
}

module.exports = {
  engineList,
  homeSections,
  listing,
  search,
  details,
  episodes,
  qualities,
  genres,
  postsByGenre,
  comments,
  banners: remoteconfig.getBanners,
  announcements: remoteconfig.getAnnouncements
};
