const almas = require('./almas');
const bj = require('./bj');
const nextmovie = require('./nextmovie');
const rezflix = require('./rezflix');
const remoteconfig = require('./remoteconfig');
const sourceconfig = require('./sourceconfig');

const engines = { almasmovie: almas, bj, nextmovie, rezflix };

const ENGINE_ORDER = ['bj', 'nextmovie', 'almasmovie', 'rezflix'];

function engine(key) {
  return engines[key] || bj;
}

// Engines are presented to users as planets. Labels, order and the kill switch
// come from the remote config; the keys stay tied to the source APIs.
function engineList() {
  const configured = sourceconfig
    .enabledList()
    .filter((s) => engines[s.key])
    .map((s) => ({ key: s.key, label: s.label || engines[s.key].label, planet: s.planet || s.key }));

  return configured.length
    ? configured
    : ENGINE_ORDER.map((key) => ({ key, label: engines[key].label, planet: key }));
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
  loadSources: () => sourceconfig.load(),
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
