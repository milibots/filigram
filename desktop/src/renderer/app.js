const api = window.filigram;

const state = {
  source: 'bj',
  tab: 'home',
  lastTab: 'home',
  store: null,
  heroItems: [],
  heroIndex: 0,
  heroTimer: null,
  listing: { kind: null, page: 1, items: [], loading: false, query: '' },
  detail: null,
  player: { item: null, quality: null, key: '' },
  engines: [],
  activeSources: [],
  downloads: []
};

const view = document.getElementById('view');
const toastEl = document.getElementById('toast');
const engineBar = document.getElementById('engineBar');
const searchInput = document.getElementById('searchInput');
const playerOverlay = document.getElementById('playerOverlay');
const btnUpdate = document.getElementById('btnUpdate');
const video = document.getElementById('video');
const DEFAULT_COVER = 'assets/default-cover.jpg';

btnUpdate.addEventListener('click', async () => {
  btnUpdate.disabled = true;
  btnUpdate.textContent = 'در حال بررسی…';
  try {
    await api.checkForUpdates(true);
  } finally {
    btnUpdate.disabled = false;
    btnUpdate.textContent = 'آپدیت';
  }
});

const FA_DIGITS = ['۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹'];

function fa(value) {
  return String(value === null || value === undefined ? '' : value).replace(/\d/g, (d) => FA_DIGITS[Number(d)]);
}

function clockText(seconds) {
  if (!Number.isFinite(seconds) || seconds < 0) seconds = 0;
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = Math.floor(seconds % 60);
  const pad = (n) => String(n).padStart(2, '0');
  return fa(h > 0 ? `${pad(h)}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`);
}

function toast(message, ms = 2600) {
  toastEl.textContent = message;
  toastEl.classList.remove('hidden');
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => toastEl.classList.add('hidden'), ms);
}

function el(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}

const posterCache = new Map();
const posterQueue = [];
let posterWorkers = 0;

/** Looks the title up in the other engines and returns the first poster that exists. */
function lookupPoster(title, exclude) {
  const key = normTitle(title);
  if (!key) return Promise.resolve(null);
  if (!posterCache.has(key)) {
    posterCache.set(
      key,
      new Promise((resolve) => {
        posterQueue.push(async () => {
          const found = await firstResult(
            state.engines
              .filter((e) => e.key !== exclude)
              .map(async (e) => {
                const hits = await api.search(e.key, title, 1);
                const hit = hits.find((h) => h.image && sameTitle(h.title, title));
                return hit ? hit.image : null;
              })
          );
          resolve(found);
        });
        pumpPosters();
      })
    );
  }
  return posterCache.get(key);
}

function pumpPosters() {
  while (posterWorkers < 3 && posterQueue.length) {
    posterWorkers += 1;
    const job = posterQueue.shift();
    job().finally(() => {
      posterWorkers -= 1;
      pumpPosters();
    });
  }
}

function posterFallback(img, item) {
  let triedAlternate = false;
  img.addEventListener('error', async () => {
    const failed = img.getAttribute('src');
    img.alt = '';
    img.classList.add('art-missing');
    if (failed && failed.endsWith(DEFAULT_COVER)) return;
    if (triedAlternate || !item || !item.title) {
      img.src = DEFAULT_COVER;
      return;
    }
    triedAlternate = true;
    const alt = await lookupPoster(item.title, item.source || state.source);
    if (alt && alt !== failed) {
      img.classList.remove('art-missing');
      img.src = alt;
    } else {
      img.src = DEFAULT_COVER;
    }
  });
}

function skeleton(count = 6) {
  const wrap = el('div', 'skeleton-row');
  for (let i = 0; i < count; i += 1) wrap.appendChild(el('div', 'skeleton-card'));
  return wrap;
}

const PLANET_ART = {
  bj: { body: '#e8a33d', band: '#c4762a', ring: null },
  nextmovie: { body: '#d9c27a', band: '#b39a4e', ring: '#e6dcb0' },
  almasmovie: { body: '#7fd4e0', band: '#4da3b8', ring: '#bfe9f0' },
  rezflix: { body: '#5b7de8', band: '#3c53b0', ring: null },
  simba: { body: '#e06a32', band: '#8d351e', ring: '#f4b05d' }
};

function planetSvg(key) {
  const art = PLANET_ART[key] || PLANET_ART.bj;
  const ring = art.ring
    ? `<ellipse cx="16" cy="16" rx="15" ry="4.5" fill="none" stroke="${art.ring}" stroke-width="1.8" transform="rotate(-18 16 16)" opacity="0.9"/>`
    : '';
  return `<svg viewBox="0 0 32 32" width="22" height="22" aria-hidden="true">
    <circle cx="16" cy="16" r="10" fill="${art.body}"/>
    <path d="M6.6 13.5h18.8M7.4 19h17.2" stroke="${art.band}" stroke-width="2" stroke-linecap="round" opacity="0.65"/>
    <circle cx="12" cy="12.5" r="2.4" fill="${art.band}" opacity="0.5"/>
    ${ring}
  </svg>`;
}


/* ─── Multi-engine helpers ─────────────────────────────── */
function activeKeys() {
  const known = new Set(state.engines.map((e) => e.key));
  const keys = state.activeSources.filter((k) => known.has(k));
  return keys.length ? keys : [state.source];
}

function tagSource(items, key) {
  return (items || []).map((item) => ({ ...item, source: item.source || key }));
}

function interleave(lists) {
  const out = [];
  const max = Math.max(0, ...lists.map((l) => l.length));
  for (let i = 0; i < max; i += 1) lists.forEach((l) => l[i] && out.push(l[i]));
  return out;
}

async function searchActive(query, page) {
  const keys = activeKeys();
  const lists = await Promise.all(
    keys.map((key) =>
      api
        .search(key, query, page)
        .then((items) => tagSource(items, key))
        .catch(() => [])
    )
  );
  return interleave(lists);
}

function itemKey(item) {
  return `${item.source}:${item.id}`;
}

async function saveStore(key, value) {
  state.store[key] = value;
  await api.storeSet(key, value);
}

function isFavorite(item) {
  return state.store.favorites.some((f) => itemKey(f) === itemKey(item));
}

async function toggleFavorite(item) {
  const list = state.store.favorites.filter((f) => itemKey(f) !== itemKey(item));
  const added = list.length === state.store.favorites.length;
  if (added) list.unshift(item);
  await saveStore('favorites', list.slice(0, 200));
  toast(added ? 'به علاقه‌مندی‌ها اضافه شد' : 'از علاقه‌مندی‌ها حذف شد');
  return added;
}

async function pushHistory(item) {
  const list = state.store.history.filter((h) => itemKey(h) !== itemKey(item));
  list.unshift({ ...item, watchedAt: Date.now() });
  await saveStore('history', list.slice(0, 60));
}

/* ─── Movie card ────────────────────────────────────────── */
function movieCard(item) {
  const card = el('div', 'card');

  const chip = el('div', 'card-name-chip', item.title);
  chip.title = item.title;
  card.appendChild(chip);

  const img = el('img', 'card-poster');
  img.loading = 'lazy';
  img.alt = item.title;
  if (item.image) img.src = item.image;
  else lookupPoster(item.title, item.source || state.source).then((alt) => {
    img.src = alt || DEFAULT_COVER;
  });
  posterFallback(img, item);
  card.appendChild(img);

  const tagText = item.type === 1 ? 'سریال' : item.hasDub ? 'دوبله' : item.hasSub ? 'زیرنویس' : 'فیلم';
  card.appendChild(el('div', 'card-tag', tagText));

  const foot = el('div', 'card-foot');
  if (item.rating) {
    const rate = el('span', 'imdb', `IMDb ${fa(item.rating)}`);
    foot.appendChild(rate);
  } else {
    foot.appendChild(el('span', 'card-year', item.year ? fa(item.year) : 'سینما'));
  }
  foot.appendChild(el('span', 'card-play', '▶'));
  card.appendChild(foot);

  card.addEventListener('click', () => openDetail(item));
  return card;
}

function sectionRow(title, items) {
  const section = el('section', 'section');
  const head = el('div', 'section-head');
  head.appendChild(el('h2', 'section-title', title));
  section.appendChild(head);

  const row = el('div', 'row');
  items.forEach((item) => row.appendChild(movieCard(item)));
  section.appendChild(row);

  row.addEventListener(
    'wheel',
    (event) => {
      if (Math.abs(event.deltaY) < Math.abs(event.deltaX)) return;
      event.preventDefault();
      row.scrollLeft -= event.deltaY;
    },
    { passive: false }
  );

  return section;
}

/* ─── Hero ──────────────────────────────────────────────── */
function renderHero(container) {
  if (!state.heroItems.length) return;
  const hero = el('div', 'hero');
  const img = el('img');
  posterFallback(img);
  hero.appendChild(img);
  hero.appendChild(el('div', 'hero-shade'));

  const body = el('div', 'hero-body');
  const chip = el('span', 'hero-chip');
  const title = el('h1', 'hero-title');
  const meta = el('div', 'hero-meta');
  const playBtn = el('button', 'primary-btn', '▶  تماشا');
  body.append(chip, title, meta, playBtn);
  hero.appendChild(body);

  const dots = el('div', 'hero-dots');
  hero.appendChild(dots);

  function paint() {
    const item = state.heroItems[state.heroIndex];
    if (!item) return;
    if (item.image) img.src = item.image;
    chip.textContent = item.type === 1 ? 'سریال' : 'فیلم';
    title.textContent = item.title;
    meta.textContent = [item.year ? fa(item.year) : '', item.hasDub ? 'دوبله فارسی' : item.hasSub ? 'زیرنویس فارسی' : '']
      .filter(Boolean)
      .join('  •  ');
    dots.textContent = '';
    state.heroItems.forEach((_, i) => {
      const dot = el('span', `hero-dot${i === state.heroIndex ? ' on' : ''}`);
      dot.addEventListener('click', (event) => {
        event.stopPropagation();
        state.heroIndex = i;
        paint();
      });
      dots.appendChild(dot);
    });
  }

  playBtn.addEventListener('click', (event) => {
    event.stopPropagation();
    openDetail(state.heroItems[state.heroIndex]);
  });
  hero.addEventListener('click', () => openDetail(state.heroItems[state.heroIndex]));

  paint();
  clearInterval(state.heroTimer);
  state.heroTimer = setInterval(() => {
    state.heroIndex = (state.heroIndex + 1) % state.heroItems.length;
    paint();
  }, 6000);

  container.appendChild(hero);
}

/* ─── Tabs ──────────────────────────────────────────────── */
async function renderHome() {
  view.textContent = '';
  view.appendChild(skeleton());

  let sections = [];
  let heroPool = [];
  try {
    const keys = activeKeys();
    const perEngine = await Promise.all(
      keys.map((key) =>
        api
          .home(key)
          .then((list) =>
            list.filter((section) => Array.isArray(section.items) && section.items.length).map((section, index) => ({
              ...section,
              title: `${['منتخب', 'تازه‌ها', 'محبوب‌ها', 'پیشنهادها'][index] || 'تماشایی‌ها'} سیاره ${engineLabel(key)}`,
              items: tagSource(section.items, key)
            }))
          )
          .catch(() => [])
      )
    );
    sections = perEngine.flat();
    heroPool = interleave(perEngine.map((list) => (list[0] ? list[0].items : [])));
  } catch (err) {
    toast(`خطا در دریافت ویترین: ${err.message}`);
  }

  view.textContent = '';
  state.heroItems = heroPool.slice(0, 6);
  renderHero(view);

  const progressItems = Object.values(state.store.progress || {})
    .filter((p) => p.item && p.seconds > 30)
    .sort((a, b) => b.updatedAt - a.updatedAt)
    .slice(0, 12)
    .map((p) => p.item);
  if (progressItems.length) view.appendChild(sectionRow('ادامه تماشا', progressItems));

  if (!sections.length) {
    view.appendChild(el('div', 'empty', 'محتوایی دریافت نشد. موتور دیگری را از نوار بالا انتخاب کنید.'));
    return;
  }
  sections.forEach((section) => view.appendChild(sectionRow(section.title, section.items)));
}

async function renderListing(kind, title) {
  state.listing = { kind, page: 1, items: [], loading: true, query: '' };
  view.textContent = '';
  const head = el('div', 'page-head');
  head.appendChild(el('h1', 'page-title', title));
  view.appendChild(head);
  const grid = el('div', 'grid');
  view.appendChild(grid);
  const loaderWrap = el('div', 'load-more');
  const moreBtn = el('button', 'primary-btn', 'موارد بیشتر');
  view.appendChild(loaderWrap);

  async function loadPage() {
    state.listing.loading = true;
    moreBtn.disabled = true;
    try {
      const lists = await Promise.all(
        activeKeys().map((key) =>
          api
            .listing(key, kind, state.listing.page)
            .then((rows) => tagSource(rows, key))
            .catch(() => [])
        )
      );
      const items = interleave(lists);
      items.forEach((item) => {
        state.listing.items.push(item);
        grid.appendChild(movieCard(item));
      });
      if (!items.length && state.listing.page === 1) {
        view.appendChild(el('div', 'empty', 'موردی یافت نشد.'));
      }
      loaderWrap.textContent = '';
      if (items.length) loaderWrap.appendChild(moreBtn);
    } catch (err) {
      toast(`خطا: ${err.message}`);
    }
    moreBtn.disabled = false;
    state.listing.loading = false;
  }

  moreBtn.addEventListener('click', () => {
    state.listing.page += 1;
    loadPage();
  });

  loaderWrap.appendChild(skeleton(4));
  await loadPage();
}

async function renderSearch(query) {
  state.tab = 'search';
  document.querySelectorAll('.nav-item').forEach((btn) => btn.classList.remove('active'));
  view.textContent = '';
  const head = el('div', 'page-head');
  head.appendChild(el('h1', 'page-title', `نتایج جستجو: ${query}`));
  view.appendChild(head);
  view.appendChild(skeleton(5));

  let items = [];
  try {
    items = await searchActive(query, 1);
  } catch (err) {
    toast(`خطا در جستجو: ${err.message}`);
  }

  if (!items.length) {
    const current = activeKeys();
    toast(`در ${current.map(engineLabel).join('، ')} یافت نشد؛ در حال بررسی سایر موتورها...`, 3200);
    const alt = await firstResult(
      state.engines
        .filter((e) => !current.includes(e.key))
        .map(async (e) => {
          const hits = await api.search(e.key, query, 1);
          return hits.length ? { key: e.key, hits } : null;
        })
    );
    if (alt) {
      items = alt.hits.map((h) => ({ ...h, source: alt.key }));
      toast(`نتیجه از موتور ${engineLabel(alt.key)} بارگذاری شد`);
    }
  }

  view.textContent = '';
  view.appendChild(head);
  if (!items.length) {
    view.appendChild(el('div', 'empty', 'نتیجه‌ای پیدا نشد. موتور دیگری را امتحان کنید.'));
    return;
  }
  const grid = el('div', 'grid');
  items.forEach((item) => grid.appendChild(movieCard(item)));
  view.appendChild(grid);

  const moreWrap = el('div', 'load-more');
  const moreBtn = el('button', 'primary-btn', 'بارگذاری نتایج بیشتر از همه موتورها');
  moreWrap.appendChild(moreBtn);
  view.appendChild(moreWrap);

  const seen = new Set(items.map((i) => `${i.source || state.source}:${i.id}`));
  moreBtn.addEventListener('click', async () => {
    moreBtn.disabled = true;
    moreBtn.textContent = 'در حال جستجو در همه موتورها...';
    const keys = state.engines.map((e) => e.key);
    const batches = await Promise.all(
      keys.map((key) =>
        Promise.all([api.search(key, query, 1), api.search(key, query, 2)])
          .then(([a, b]) => [...a, ...b].map((h) => ({ ...h, source: key })))
          .catch(() => [])
      )
    );
    let added = 0;
    batches.flat().forEach((hit) => {
      const id = `${hit.source}:${hit.id}`;
      if (seen.has(id)) return;
      seen.add(id);
      grid.appendChild(movieCard(hit));
      added += 1;
    });
    moreBtn.disabled = false;
    moreBtn.textContent = 'بارگذاری نتایج بیشتر از همه موتورها';
    toast(added ? `${fa(added)} نتیجه جدید اضافه شد` : 'نتیجه جدیدی پیدا نشد');
  });

  const history = [query, ...state.store.searchHistory.filter((q) => q !== query)].slice(0, 12);
  saveStore('searchHistory', history);
}

function renderLibrary() {
  view.textContent = '';
  const head = el('div', 'page-head');
  head.appendChild(el('h1', 'page-title', 'کتابخانه من'));
  view.appendChild(head);

  const favorites = state.store.favorites || [];
  const history = state.store.history || [];

  if (favorites.length) view.appendChild(sectionRow('علاقه‌مندی‌ها', favorites));
  if (history.length) view.appendChild(sectionRow('تماشا شده‌ها', history));
  if (!favorites.length && !history.length) {
    view.appendChild(el('div', 'empty', 'کتابخانه خالی است. از صفحه جزییات هر فیلم آن را به علاقه‌مندی‌ها اضافه کنید.'));
  }
}

async function renderNews() {
  view.textContent = '';
  const head = el('div', 'page-head');
  head.appendChild(el('h1', 'page-title', 'اعلان‌های فیلیگرام'));
  view.appendChild(head);

  let list = [];
  try {
    list = await api.announcements();
  } catch (err) {
    toast(`خطا در دریافت اعلان‌ها: ${err.message}`);
  }

  if (!list.length) {
    view.appendChild(el('div', 'empty', 'اعلانی موجود نیست.'));
    return;
  }

  list.forEach((n) => {
    const item = el('div', 'news-item');
    item.appendChild(el('h3', 'news-title', n.title));
    item.appendChild(el('p', 'news-text', n.text));
    view.appendChild(item);
  });

  document.getElementById('newsDot').classList.remove('on');
  saveStore('readAnnouncements', list.map((n) => n.id));
}

function switchTab(tab) {
  state.tab = tab;
  document.querySelectorAll('.nav-item').forEach((btn) => btn.classList.toggle('active', btn.dataset.tab === tab));
  clearInterval(state.heroTimer);
  if (tab === 'home') renderHome();
  else if (tab === 'movies') renderListing('movies', 'فیلم‌ها');
  else if (tab === 'series') renderListing('series', 'سریال‌ها');
  else if (tab === 'library') renderLibrary();
  else if (tab === 'downloads') renderDownloads();
  else if (tab === 'news') renderNews();
}


/* ─── Links export, quality pick and cross-engine fallback ─ */
const QUALITY_PREFS = ['1080', '720', '480'];

function pickQuality(qualities, pref) {
  if (!qualities || !qualities.length) return null;
  return qualities.find((q) => `${q.title || ''} ${q.type || ''}`.includes(pref)) || qualities[0];
}

function normTitle(text) {
  return String(text || '')
    .toLowerCase()
    .replace(/سریال|فیلم|دوبله|زیرنویس|فارسی|کامل|فصل/g, '')
    .replace(/[^\p{L}\p{N}]/gu, '');
}

function sameTitle(a, b) {
  const x = normTitle(a);
  const y = normTitle(b);
  if (x.length < 3 || y.length < 3) return false;
  return x === y || x.includes(y) || y.includes(x);
}

function engineLabel(key) {
  const found = state.engines.find((e) => e.key === key);
  return found ? found.label : key;
}

function firstResult(promises) {
  return new Promise((resolve) => {
    let pending = promises.length;
    if (!pending) resolve(null);
    promises.forEach((p) =>
      p
        .then((value) => {
          if (value) resolve(value);
        })
        .catch(() => {})
        .finally(() => {
          pending -= 1;
          if (pending === 0) resolve(null);
        })
    );
  });
}

/** Search the same title in every other engine at once; first engine whose fetcher returns data wins. */
function findInOtherEngines(title, type, fetcher, exclude) {
  const keys = state.engines.map((e) => e.key).filter((k) => k !== (exclude || state.source));
  return firstResult(
    keys.map(async (key) => {
      const hits = await api.search(key, title, 1);
      const match = hits.find((h) => sameTitle(h.title, title) && (type !== 1 || h.type === 1));
      if (!match) return null;
      const data = await fetcher(key, match);
      return data && data.length ? { source: key, item: match, data } : null;
    })
  );
}


const fallbackMatches = new Map();

/**
 * Runs the primary request; if it is slow (> delay) or empty, other engines are queried in the
 * background so the first usable answer shows up immediately.
 */
async function withFallback(primary, startFallback, isEmpty, delay = 700) {
  let fallback = null;
  const timer = setTimeout(() => {
    fallback = startFallback();
  }, delay);
  let value = null;
  try {
    value = await primary;
  } catch (_) {
    value = null;
  }
  clearTimeout(timer);
  if (!isEmpty(value)) return { value, from: null };
  if (!fallback) fallback = startFallback();
  const alt = await fallback;
  return alt ? { value: alt.data, from: alt.source } : { value: value || [], from: null };
}

/** Same as findInOtherEngines, but remembers which engine matched a title so repeat lookups are instant. */
function findInOtherEnginesCached(title, type, fetcher, exclude) {
  const cacheKey = `${type}:${normTitle(title)}`;
  const known = fallbackMatches.get(cacheKey);
  if (known && known.source !== exclude) {
    return fetcher(known.source, known.item)
      .then((data) => (data && data.length ? { source: known.source, item: known.item, data } : null))
      .catch(() => null)
      .then((hit) => hit || findInOtherEngines(title, type, fetcher, exclude))
      .then((hit) => {
        if (hit) fallbackMatches.set(cacheKey, { source: hit.source, item: hit.item });
        return hit;
      });
  }
  return findInOtherEngines(title, type, fetcher, exclude).then((hit) => {
    if (hit) fallbackMatches.set(cacheKey, { source: hit.source, item: hit.item });
    return hit;
  });
}

async function fetchQualitiesFrom(key, item) {
  const detail = await api.details(key, item.id, item.type);
  if (detail && detail.directQualities && detail.directQualities.length) return detail.directQualities;
  return api.qualities(key, item.id);
}

function pad2(n) {
  return String(n).padStart(2, '0');
}

function episodeNumber(episode, index) {
  return Number.isFinite(Number(episode.episode)) && Number(episode.episode) > 0 ? Number(episode.episode) : index + 1;
}

function linkRows(detail, seasonNumber, episodes, pref) {
  const rows = [];
  episodes.forEach((episode, index) => {
    const q = pickQuality(episode.qualities, pref);
    if (!q || !q.directUrl) return;
    rows.push({
      series: detail.title,
      season: seasonNumber,
      episode: episodeNumber(episode, index),
      quality: q.title || q.type || '',
      size: q.size || '',
      url: q.directUrl
    });
  });
  return rows;
}

async function exportRows(format, rows, baseName) {
  if (!rows.length) {
    toast('لینکی برای خروجی گرفتن موجود نیست');
    return;
  }
  const saved = await api.exportLinks(format, rows, baseName);
  if (saved) toast(`${fa(rows.length)} لینک ذخیره شد`);
}

async function downloadRows(rows) {
  if (!rows.length) {
    toast('لینکی برای دانلود موجود نیست');
    return;
  }
  const items = rows.map((r) => ({
    title: r.season
      ? `${r.series} S${pad2(r.season)}E${pad2(r.episode)} ${r.quality}`.trim()
      : `${r.series} ${r.quality}`.trim(),
    url: r.url
  }));
  await api.dlEnqueue(items);
  toast(`${fa(items.length)} مورد به صف دانلود اضافه شد`);
}

/** Shift-click aware multi-select shared by episode and download lists. */
function createSelection(onChange) {
  const picked = new Set();
  let anchor = -1;
  return {
    picked,
    toggle(index, shift) {
      if (shift && anchor >= 0) {
        const [a, b] = anchor < index ? [anchor, index] : [index, anchor];
        for (let i = a; i <= b; i += 1) picked.add(i);
      } else if (picked.has(index)) picked.delete(index);
      else picked.add(index);
      anchor = index;
      onChange();
    },
    all(count) {
      for (let i = 0; i < count; i += 1) picked.add(i);
      onChange();
    },
    clear() {
      picked.clear();
      anchor = -1;
      onChange();
    }
  };
}


/* ─── Downloads manager ─────────────────────────────────── */
function bytesText(n) {
  if (!n || n < 0) return '۰';
  const units = ['B', 'KB', 'MB', 'GB'];
  let value = n;
  let u = 0;
  while (value >= 1024 && u < units.length - 1) {
    value /= 1024;
    u += 1;
  }
  return fa(`${value.toFixed(u > 1 ? 1 : 0)} ${units[u]}`);
}

const DL_STATUS = {
  queued: 'در صف',
  connecting: 'اتصال...',
  downloading: 'در حال دریافت',
  paused: 'متوقف',
  completed: 'تکمیل شد',
  failed: 'خطا',
  cancelled: 'لغو شد'
};

const dlView = { selection: null, rows: new Map(), ids: '', list: null, summary: null };

function updateDownloadBadge() {
  const active = state.downloads.filter((t) => ['queued', 'connecting', 'downloading'].includes(t.status)).length;
  const badge = document.getElementById('dlBadge');
  badge.textContent = active ? fa(active) : '';
  badge.classList.toggle('on', active > 0);
}

function renderDownloads() {
  state.tab = 'downloads';
  view.textContent = '';
  const head = el('div', 'page-head');
  head.appendChild(el('h1', 'page-title', 'مدیریت دانلود'));
  view.appendChild(head);

  dlView.summary = el('div', 'dl-summary');
  const bar = el('div', 'bulk-bar');
  const allBtn = el('button', 'ghost-btn', 'انتخاب همه');
  const resumeBtn = el('button', 'ghost-btn', 'شروع');
  const pauseBtn = el('button', 'ghost-btn', 'توقف');
  const removeBtn = el('button', 'ghost-btn danger', 'حذف');
  const clearBtn = el('button', 'ghost-btn', 'پاکسازی تکمیل‌شده‌ها');
  const folderBtn = el('button', 'ghost-btn', 'باز کردن پوشه');
  bar.append(allBtn, resumeBtn, pauseBtn, removeBtn, clearBtn, folderBtn);
  view.append(dlView.summary, bar);

  dlView.list = el('div', 'episode-list');
  view.appendChild(dlView.list);
  dlView.rows.clear();
  dlView.ids = '';
  dlView.selection = createSelection(paintDownloads);

  const picked = () => [...dlView.selection.picked].map((i) => state.downloads[i]).filter(Boolean);
  const act = (fn) => {
    const targets = picked();
    (targets.length ? targets : state.downloads).forEach(fn);
  };

  allBtn.addEventListener('click', () => {
    if (dlView.selection.picked.size === state.downloads.length) dlView.selection.clear();
    else dlView.selection.all(state.downloads.length);
  });
  resumeBtn.addEventListener('click', () => act((t) => api.dlResume(t.id)));
  pauseBtn.addEventListener('click', () => act((t) => api.dlPause(t.id)));
  removeBtn.addEventListener('click', () => {
    const targets = picked();
    if (!targets.length) {
      toast('ابتدا موارد را انتخاب کنید (Shift برای انتخاب بازه‌ای)');
      return;
    }
    if (!window.confirm(`${fa(targets.length)} مورد و فایل‌های آن‌ها حذف شوند؟`)) return;
    targets.forEach((t) => api.dlRemove(t.id, true));
    dlView.selection.clear();
  });
  clearBtn.addEventListener('click', () => api.dlClearCompleted());
  folderBtn.addEventListener('click', () => api.dlOpenFolder());

  paintDownloads();
}

function paintDownloads() {
  if (state.tab !== 'downloads' || !dlView.list) return;
  const list = state.downloads;
  const ids = list.map((t) => t.id).join('|');

  if (ids !== dlView.ids) {
    dlView.ids = ids;
    dlView.list.textContent = '';
    dlView.rows.clear();
    if (!list.length) {
      dlView.list.appendChild(el('div', 'empty', 'دانلودی وجود ندارد. از صفحه سریال، قسمت‌ها را انتخاب و دانلود کنید.'));
    }
    list.forEach((task, index) => {
      const row = el('div', 'episode-row dl-row');
      const box = el('span', 'check');
      const body = el('div', 'dl-body');
      const title = el('div', 'ep-title', task.title);
      const track = el('div', 'dl-track');
      const fill = el('div', 'dl-fill');
      track.appendChild(fill);
      const info = el('div', 'dl-info');
      body.append(title, track, info);

      const toggle = el('button', 'ghost-btn');
      const show = el('button', 'ghost-btn', 'پوشه');
      const del = el('button', 'ghost-btn danger', '✕');
      toggle.addEventListener('click', (event) => {
        event.stopPropagation();
        const current = state.downloads.find((t) => t.id === task.id);
        if (!current) return;
        if (['downloading', 'connecting', 'queued'].includes(current.status)) api.dlPause(task.id);
        else if (current.status !== 'completed') api.dlResume(task.id);
      });
      show.addEventListener('click', (event) => {
        event.stopPropagation();
        api.dlShow(task.id);
      });
      del.addEventListener('click', (event) => {
        event.stopPropagation();
        api.dlRemove(task.id, task.status !== 'completed');
      });
      row.append(box, body, toggle, show, del);
      row.addEventListener('click', (event) => dlView.selection.toggle(index, event.shiftKey));
      dlView.list.appendChild(row);
      dlView.rows.set(task.id, { row, box, fill, info, toggle });
    });
  }

  let speed = 0;
  list.forEach((task, index) => {
    const node = dlView.rows.get(task.id);
    if (!node) return;
    const percent = task.total ? Math.min(100, (task.downloaded / task.total) * 100) : 0;
    node.fill.style.width = `${percent}%`;
    node.fill.classList.toggle('done', task.status === 'completed');
    node.row.classList.toggle('picked', dlView.selection.picked.has(index));
    node.box.textContent = dlView.selection.picked.has(index) ? '✓' : '';
    const parts = [DL_STATUS[task.status] || task.status];
    if (task.total) parts.push(`${bytesText(task.downloaded)} / ${bytesText(task.total)} (${fa(Math.floor(percent))}٪)`);
    if (task.status === 'downloading' && task.speed) parts.push(`${bytesText(task.speed)}/s`);
    if (task.status === 'failed' && task.error) parts.push(task.error);
    node.info.textContent = parts.join('  •  ');
    node.toggle.textContent = ['downloading', 'connecting', 'queued'].includes(task.status) ? '❚❚' : '▶';
    node.toggle.hidden = task.status === 'completed';
    if (task.status === 'downloading') speed += task.speed || 0;
  });

  const picked = dlView.selection.picked.size;
  dlView.summary.textContent = `سرعت کل: ${bytesText(speed)}/s  •  ${fa(list.length)} مورد${picked ? `  •  ${fa(picked)} انتخاب‌شده` : ''}`;
}

/* ─── Detail ────────────────────────────────────────────── */
function qualityRow(detail, quality, episodeLabel) {
  const row = el('div', 'quality-row');
  row.appendChild(el('span', 'q-title', quality.title || quality.type));
  if (quality.size) row.appendChild(el('span', 'q-size', quality.size));

  const playBtn = el('button', 'primary-btn', '▶  پخش');
  playBtn.addEventListener('click', () => openPlayer(detail, quality, episodeLabel));
  row.appendChild(playBtn);

  const copyBtn = el('button', 'ghost-btn', 'کپی لینک');
  copyBtn.addEventListener('click', async () => {
    await api.copyText(quality.directUrl);
    toast('لینک کپی شد');
  });
  row.appendChild(copyBtn);

  const dlBtn = el('button', 'ghost-btn', 'دانلود');
  dlBtn.addEventListener('click', () => api.openExternal(quality.directUrl));
  row.appendChild(dlBtn);

  return row;
}

async function openDetail(item) {
  if (!item) return;
  if (state.tab !== 'detail') state.lastTab = state.tab;
  state.tab = 'detail';
  clearInterval(state.heroTimer);
  document.querySelectorAll('.nav-item').forEach((btn) => btn.classList.remove('active'));

  const detailCard = el('div', 'detail-page');
  view.textContent = '';
  view.appendChild(detailCard);
  view.scrollTop = 0;
  detailCard.appendChild(skeleton(3));

  let detail = null;
  try {
    detail = await api.details(item.source || state.source, item.id, item.type);
  } catch (err) {
    toast(`خطا در دریافت جزییات: ${err.message}`);
  }

  if (!detail) {
    detailCard.textContent = '';
    detailCard.appendChild(el('div', 'empty', 'اطلاعات این عنوان دریافت نشد.'));
    const close = el('button', 'ghost-btn', 'بازگشت');
    close.style.margin = '0 auto 24px';
    close.style.display = 'block';
    close.addEventListener('click', closeDetail);
    detailCard.appendChild(close);
    return;
  }

  state.detail = detail;
  detailCard.textContent = '';

  const banner = el('div', 'detail-banner');
  const bannerImg = el('img');
  bannerImg.addEventListener('error', () => {
    if (detail.image && bannerImg.src !== detail.image) {
      bannerImg.src = detail.image;
      return;
    }
    bannerImg.src = DEFAULT_COVER;
  });
  bannerImg.src = detail.banner || detail.image || DEFAULT_COVER;
  banner.appendChild(bannerImg);
  banner.appendChild(el('div', 'detail-banner-shade'));
  const closeBtn = el('button', 'ghost-btn detail-close', '→  بازگشت');
  closeBtn.addEventListener('click', closeDetail);
  banner.appendChild(closeBtn);
  detailCard.appendChild(banner);

  const body = el('div', 'detail-body');

  const head = el('div', 'detail-head');
  {
    const poster = el('img', 'detail-poster');
    poster.src = detail.image || DEFAULT_COVER;
    poster.alt = detail.title;
    posterFallback(poster);
    head.appendChild(poster);
  }
  const headText = el('div', 'detail-head-text');
  headText.appendChild(el('h2', 'detail-title', detail.title));
  head.appendChild(headText);
  body.appendChild(head);

  const meta = el('div', 'detail-meta');
  if (detail.imdbRate) meta.appendChild(el('span', 'imdb', `IMDb ${fa(detail.imdbRate)}`));
  [detail.year && fa(detail.year), detail.duration && fa(detail.duration), detail.type === 1 ? 'سریال' : 'فیلم']
    .filter(Boolean)
    .forEach((text) => meta.appendChild(el('span', null, text)));
  headText.appendChild(meta);

  if (detail.description) body.appendChild(el('div', 'detail-desc', detail.description));

  const actions = el('div', 'detail-actions');
  const favBtn = el('button', 'ghost-btn', isFavorite(item) ? '★  در علاقه‌مندی‌ها' : '☆  افزودن به علاقه‌مندی');
  favBtn.addEventListener('click', async () => {
    const added = await toggleFavorite({ ...item, title: detail.title, image: detail.image || item.image });
    favBtn.textContent = added ? '★  در علاقه‌مندی‌ها' : '☆  افزودن به علاقه‌مندی';
  });
  actions.appendChild(favBtn);
  body.appendChild(actions);

  if (detail.type === 1) {
    body.appendChild(el('h3', 'block-title', 'فصل‌ها'));
    const seasonChips = el('div', 'chips');
    const episodesWrap = el('div');
    body.appendChild(seasonChips);
    body.appendChild(episodesWrap);

    const seasons = detail.seasons.length ? detail.seasons : [{ season: 1, title: 'فصل ۱' }];
    seasons.forEach((season, index) => {
      const chip = el('button', `chip${index === 0 ? ' on' : ''}`, season.title);
      chip.addEventListener('click', () => {
        seasonChips.querySelectorAll('.chip').forEach((c) => c.classList.remove('on'));
        chip.classList.add('on');
        loadEpisodes(detail, season.season, episodesWrap, seasons);
      });
      seasonChips.appendChild(chip);
    });
    loadEpisodes(detail, seasons[0].season, episodesWrap, seasons);
  } else {
    body.appendChild(el('h3', 'block-title', 'کیفیت‌های پخش'));
    const list = el('div', 'quality-list');
    body.appendChild(list);

    let qualities = detail.directQualities || [];
    if (!qualities.length) {
      list.appendChild(el('div', 'empty', 'در حال دریافت لینک‌ها...'));
      const primaryQualities = api.qualities(detail.source, detail.id).catch((err) => {
        toast(`خطا در دریافت لینک‌ها: ${err.message}`);
        return [];
      });
      const result = await withFallback(
        primaryQualities,
        () => findInOtherEnginesCached(detail.title, detail.type, fetchQualitiesFrom, detail.source),
        (q) => !q || !q.length
      );
      qualities = result.value;
      if (result.from) toast(`لینک از موتور ${engineLabel(result.from)} پیدا شد`);
      list.textContent = '';
    }

    if (!qualities.length) list.appendChild(el('div', 'empty', 'لینک پخشی برای این عنوان پیدا نشد.'));
    qualities.forEach((q) => list.appendChild(qualityRow(detail, q, '')));
    if (qualities.length) {
      const bar = el('div', 'bulk-bar');
      const movieRows = () =>
        qualities
          .filter((q) => q.directUrl)
          .map((q) => ({ series: detail.title, season: 0, episode: 0, quality: q.title || q.type, size: q.size, url: q.directUrl }));
      const txt = el('button', 'ghost-btn', 'خروجی TXT');
      txt.addEventListener('click', () => exportRows('txt', movieRows(), detail.title));
      const csv = el('button', 'ghost-btn', 'خروجی CSV');
      csv.addEventListener('click', () => exportRows('csv', movieRows(), detail.title));
      bar.append(txt, csv);
      body.appendChild(bar);
    }
  }

  detailCard.appendChild(body);
  detailCard.scrollTop = 0;
}

async function loadEpisodes(detail, seasonNumber, container, seasons) {
  container.textContent = '';
  container.appendChild(el('div', 'empty', 'در حال دریافت قسمت‌ها...'));

  const hasLinks = (list) => Array.isArray(list) && list.some((e) => e.qualities && e.qualities.length);
  const primaryEpisodes = api.episodes(detail.source, detail.id, seasonNumber).catch((err) => {
    toast(`خطا در دریافت قسمت‌ها: ${err.message}`);
    return [];
  });
  const result = await withFallback(
    primaryEpisodes,
    () =>
      findInOtherEnginesCached(detail.title, 1, async (key, match) => {
        const list = await api.episodes(key, match.id, seasonNumber);
        return list.filter((e) => e.qualities && e.qualities.length);
      }, detail.source),
    (list) => !hasLinks(list)
  );
  const episodes = result.value;
  if (result.from) toast(`لینک قسمت‌ها از موتور ${engineLabel(result.from)} پیدا شد`);

  container.textContent = '';
  if (!episodes.length) {
    container.appendChild(el('div', 'empty', 'قسمتی برای این فصل پیدا نشد.'));
    return;
  }

  let pref = state.store.qualityPref || '720';
  const rowEls = [];
  const selection = createSelection(refresh);

  container.appendChild(el('h3', 'block-title', 'قسمت‌ها'));

  const bar = el('div', 'bulk-bar');
  const prefWrap = el('div', 'chips');
  const prefChips = QUALITY_PREFS.map((value) => {
    const chip = el('button', `chip${value === pref ? ' on' : ''}`, `${fa(value)}p`);
    chip.addEventListener('click', () => {
      pref = value;
      saveStore('qualityPref', value);
      prefChips.forEach((c, i) => c.classList.toggle('on', QUALITY_PREFS[i] === value));
      refresh();
    });
    prefWrap.appendChild(chip);
    return chip;
  });
  const allBtn = el('button', 'ghost-btn', 'انتخاب همه');
  const dlBtn = el('button', 'primary-btn', 'دانلود');
  const txtBtn = el('button', 'ghost-btn', 'TXT');
  const csvBtn = el('button', 'ghost-btn', 'CSV');
  const allTxtBtn = el('button', 'ghost-btn', 'همه فصل‌ها TXT');
  const allCsvBtn = el('button', 'ghost-btn', 'همه فصل‌ها CSV');
  bar.append(prefWrap, allBtn, dlBtn, txtBtn, csvBtn, allTxtBtn, allCsvBtn);
  container.appendChild(bar);

  const list = el('div', 'episode-list');
  container.appendChild(list);

  function refresh() {
    rowEls.forEach((node, index) => {
      node.row.classList.toggle('picked', selection.picked.has(index));
      node.box.textContent = selection.picked.has(index) ? '✓' : '';
      const q = pickQuality(episodes[index].qualities, pref);
      node.quality.textContent = q ? [q.title || q.type, q.size].filter(Boolean).join(' • ') : 'بدون لینک';
    });
    const n = selection.picked.size;
    dlBtn.textContent = n ? `دانلود انتخاب‌شده (${fa(n)})` : 'دانلود همه';
    allBtn.textContent = n === episodes.length ? 'لغو انتخاب' : 'انتخاب همه';
  }

  episodes.forEach((episode, index) => {
    const row = el('div', 'episode-row');
    const box = el('span', 'check');
    const title = el('span', 'ep-title', episode.title);
    const quality = el('span', 'ep-quality');
    const play = el('button', 'ghost-btn', '▶');
    const copy = el('button', 'ghost-btn', 'لینک');
    play.addEventListener('click', (event) => {
      event.stopPropagation();
      const q = pickQuality(episode.qualities, pref);
      if (q) openPlayer(detail, q, `فصل ${fa(seasonNumber)} — ${episode.title}`);
    });
    copy.addEventListener('click', async (event) => {
      event.stopPropagation();
      const q = pickQuality(episode.qualities, pref);
      if (!q) return;
      await api.copyText(q.directUrl);
      toast('لینک کپی شد');
    });
    row.append(box, title, quality, play, copy);
    row.addEventListener('click', (event) => selection.toggle(index, event.shiftKey));
    list.appendChild(row);
    rowEls.push({ row, box, quality });
  });

  allBtn.addEventListener('click', () => {
    if (selection.picked.size === episodes.length) selection.clear();
    else selection.all(episodes.length);
  });

  // Episode numbers must come from the original index, so build rows per selected index.
  function rowsFromPicked() {
    const indexes = selection.picked.size ? [...selection.picked].sort((a, b) => a - b) : episodes.map((_, i) => i);
    const out = [];
    indexes.forEach((i) => {
      const q = pickQuality(episodes[i].qualities, pref);
      if (!q || !q.directUrl) return;
      out.push({
        series: detail.title,
        season: seasonNumber,
        episode: episodeNumber(episodes[i], i),
        quality: q.title || q.type || '',
        size: q.size || '',
        url: q.directUrl
      });
    });
    return out;
  }

  dlBtn.addEventListener('click', () => downloadRows(rowsFromPicked()));
  txtBtn.addEventListener('click', () => exportRows('txt', rowsFromPicked(), `${detail.title} S${pad2(seasonNumber)}`));
  csvBtn.addEventListener('click', () => exportRows('csv', rowsFromPicked(), `${detail.title} S${pad2(seasonNumber)}`));

  async function allSeasonRows() {
    const all = [];
    const list = seasons && seasons.length ? seasons : [{ season: seasonNumber }];
    toast('در حال جمع‌آوری لینک همه فصل‌ها...', 4000);
    for (const season of list) {
      try {
        const eps = await api.episodes(detail.source, detail.id, season.season);
        all.push(...linkRows(detail, season.season, eps, pref));
      } catch (_) {
        /* a failed season is skipped */
      }
    }
    return all;
  }
  allTxtBtn.addEventListener('click', async () => exportRows('txt', await allSeasonRows(), `${detail.title} all-seasons`));
  allCsvBtn.addEventListener('click', async () => exportRows('csv', await allSeasonRows(), `${detail.title} all-seasons`));

  refresh();
}

function closeDetail() {
  switchTab(state.lastTab || 'home');
}

/* ─── Player ────────────────────────────────────────────── */
const playerShell = document.querySelector('.player-shell');
const playerTitle = document.getElementById('playerTitle');
const playerLoader = document.getElementById('playerLoader');
const seek = document.getElementById('seek');
const timeLabel = document.getElementById('timeLabel');
const btnPlayPause = document.getElementById('btnPlayPause');
const volumeInput = document.getElementById('volume');
const rateSelect = document.getElementById('rateSelect');

let idleTimer = null;
let seeking = false;

function progressKey(detail, quality, episodeLabel) {
  return `${detail.source}:${detail.id}:${episodeLabel || quality.id}`;
}

async function openPlayer(detail, quality, episodeLabel) {
  if (!quality || !quality.directUrl) {
    toast('لینک پخش موجود نیست');
    return;
  }

  state.player = { item: detail, quality, key: progressKey(detail, quality, episodeLabel) };
  playerTitle.textContent = [detail.title, episodeLabel].filter(Boolean).join('  •  ');
  playerOverlay.classList.remove('hidden');
  playerLoader.classList.remove('hidden');

  video.src = quality.directUrl;
  video.volume = state.store.volume ?? 1;
  volumeInput.value = video.volume;
  video.playbackRate = Number(rateSelect.value);

  const saved = state.store.progress[state.player.key];
  video.addEventListener(
    'loadedmetadata',
    () => {
      if (saved && saved.seconds > 20 && saved.seconds < video.duration - 30) {
        video.currentTime = saved.seconds;
        toast(`ادامه از ${clockText(saved.seconds)}`);
      }
    },
    { once: true }
  );

  try {
    await video.play();
  } catch (_) {
    /* autoplay can fail before user gesture */
  }

  pushHistory({
    source: detail.source,
    id: detail.id,
    title: detail.title,
    image: detail.image,
    type: detail.type,
    year: detail.year,
    rating: detail.imdbRate
  });
}

function closePlayer() {
  saveProgress();
  video.pause();
  video.removeAttribute('src');
  video.load();
  [...video.querySelectorAll('track')].forEach((t) => t.remove());
  playerOverlay.classList.add('hidden');
}

function saveProgress() {
  const { key, item } = state.player;
  if (!key || !item || !Number.isFinite(video.currentTime) || video.currentTime < 10) return;
  const progress = { ...state.store.progress };
  progress[key] = {
    seconds: video.currentTime,
    duration: video.duration || 0,
    updatedAt: Date.now(),
    item: {
      source: item.source,
      id: item.id,
      title: item.title,
      image: item.image,
      type: item.type,
      year: item.year,
      rating: item.imdbRate
    }
  };
  saveStore('progress', progress);
}

function srtToVtt(text) {
  return `WEBVTT\n\n${text.replace(/\r/g, '').replace(/(\d{2}:\d{2}:\d{2}),(\d{3})/g, '$1.$2')}`;
}

function bindPlayer() {
  document.getElementById('btnClosePlayer').addEventListener('click', closePlayer);
  document.getElementById('btnBack10').addEventListener('click', () => {
    video.currentTime = Math.max(0, video.currentTime - 10);
  });
  document.getElementById('btnFwd10').addEventListener('click', () => {
    video.currentTime = Math.min(video.duration || Infinity, video.currentTime + 10);
  });
  btnPlayPause.addEventListener('click', () => (video.paused ? video.play() : video.pause()));
  document.getElementById('btnFullscreen').addEventListener('click', () => {
    if (document.fullscreenElement) document.exitFullscreen();
    else playerOverlay.requestFullscreen();
  });
  document.getElementById('btnCopyLink').addEventListener('click', async () => {
    if (!state.player.quality) return;
    await api.copyText(state.player.quality.directUrl);
    toast('لینک کپی شد');
  });
  document.getElementById('btnExternal').addEventListener('click', async () => {
    if (!state.player.quality) return;
    const ok = await api.playExternal(state.player.quality.directUrl);
    if (ok) {
      toast('پخش در پلیر خارجی آغاز شد');
      video.pause();
    } else {
      toast('پلیر خارجی پیدا نشد. لینک کپی شد.');
      await api.copyText(state.player.quality.directUrl);
    }
  });

  document.getElementById('subInput').addEventListener('change', async (event) => {
    const file = event.target.files && event.target.files[0];
    if (!file) return;
    const raw = await file.text();
    const vtt = file.name.toLowerCase().endsWith('.srt') ? srtToVtt(raw) : raw;
    const url = URL.createObjectURL(new Blob([vtt], { type: 'text/vtt' }));
    [...video.querySelectorAll('track')].forEach((t) => t.remove());
    const track = document.createElement('track');
    track.kind = 'subtitles';
    track.label = 'فارسی';
    track.srclang = 'fa';
    track.src = url;
    track.default = true;
    video.appendChild(track);
    video.textTracks[0].mode = 'showing';
    toast('زیرنویس بارگذاری شد');
  });

  rateSelect.addEventListener('change', () => {
    video.playbackRate = Number(rateSelect.value);
  });
  volumeInput.addEventListener('input', () => {
    video.volume = Number(volumeInput.value);
    saveStore('volume', video.volume);
  });

  video.addEventListener('play', () => {
    btnPlayPause.textContent = '❚❚';
  });
  video.addEventListener('pause', () => {
    btnPlayPause.textContent = '▶';
  });
  video.addEventListener('waiting', () => playerLoader.classList.remove('hidden'));
  video.addEventListener('playing', () => playerLoader.classList.add('hidden'));
  video.addEventListener('canplay', () => playerLoader.classList.add('hidden'));
  video.addEventListener('error', () => {
    playerLoader.classList.add('hidden');
    toast('پخش این لینک ممکن نشد. دکمه VLC یا کپی لینک را امتحان کنید.', 4200);
  });
  video.addEventListener('timeupdate', () => {
    if (!seeking && video.duration) {
      seek.value = String((video.currentTime / video.duration) * 1000);
    }
    timeLabel.textContent = `${clockText(video.currentTime)} / ${clockText(video.duration)}`;
  });
  video.addEventListener('ended', saveProgress);

  seek.addEventListener('input', () => {
    seeking = true;
  });
  seek.addEventListener('change', () => {
    if (video.duration) video.currentTime = (Number(seek.value) / 1000) * video.duration;
    seeking = false;
  });

  setInterval(() => {
    if (!playerOverlay.classList.contains('hidden') && !video.paused) saveProgress();
  }, 10000);

  playerOverlay.addEventListener('mousemove', () => {
    playerShell.classList.remove('idle');
    clearTimeout(idleTimer);
    idleTimer = setTimeout(() => {
      if (!video.paused) playerShell.classList.add('idle');
    }, 2800);
  });

  video.addEventListener('click', () => (video.paused ? video.play() : video.pause()));
}

/* ─── Global bindings ───────────────────────────────────── */
function bindShell() {
  document.getElementById('nav').addEventListener('click', (event) => {
    const btn = event.target.closest('.nav-item');
    if (btn) switchTab(btn.dataset.tab);
  });

  searchInput.addEventListener('keydown', (event) => {
    if (event.key !== 'Enter') return;
    const query = searchInput.value.trim();
    if (query.length < 2) return;
    renderSearch(query);
  });

  document.addEventListener('keydown', (event) => {
    const playerOpen = !playerOverlay.classList.contains('hidden');
    if (event.key === 'Escape') {
      if (playerOpen) closePlayer();
      else if (state.tab === 'detail') closeDetail();
      return;
    }
    if (!playerOpen) return;
    if (event.key === ' ') {
      event.preventDefault();
      if (video.paused) video.play();
      else video.pause();
    }
    if (event.key === 'ArrowRight') video.currentTime = Math.max(0, video.currentTime - 5);
    if (event.key === 'ArrowLeft') video.currentTime = video.currentTime + 5;
    if (event.key === 'f') document.getElementById('btnFullscreen').click();
  });

  window.addEventListener('beforeunload', saveProgress);
}


/* ─── Engine drawer (multi-select) ─────────────────────── */
function buildEngineDrawer() {
  engineBar.textContent = '';
  const trigger = el('button', 'planet-btn on');
  const panel = el('div', 'engine-panel hidden');
  engineBar.append(trigger, panel);
  let dirty = false;

  function paintTrigger() {
    const keys = activeKeys();
    trigger.textContent = '';
    trigger.insertAdjacentHTML('beforeend', planetSvg(keys[0]));
    trigger.appendChild(el('span', 'planet-label', keys.length > 1 ? `${fa(keys.length)} موتور فعال` : engineLabel(keys[0])));
    trigger.appendChild(el('span', 'planet-caret', '▾'));
  }

  function paintPanel() {
    panel.textContent = '';
    panel.appendChild(el('div', 'engine-panel-title', 'موتورهای فعال (چند انتخاب)'));
    const active = new Set(activeKeys());
    state.engines.forEach((engine) => {
      const row = el('button', `engine-row${active.has(engine.key) ? ' on' : ''}`);
      row.insertAdjacentHTML('beforeend', planetSvg(engine.key));
      row.appendChild(el('span', 'engine-row-label', engine.label));
      row.appendChild(el('span', 'check', active.has(engine.key) ? '✓' : ''));
      row.addEventListener('click', async (event) => {
        event.stopPropagation();
        const next = new Set(activeKeys());
        if (next.has(engine.key)) {
          if (next.size === 1) {
            toast('حداقل یک موتور باید فعال بماند');
            return;
          }
          next.delete(engine.key);
        } else next.add(engine.key);
        state.activeSources = state.engines.map((e) => e.key).filter((k) => next.has(k));
        state.source = state.activeSources[0];
        dirty = true;
        await saveStore('activeSources', state.activeSources);
        await saveStore('source', state.source);
        paintTrigger();
        paintPanel();
      });
      panel.appendChild(row);
    });
  }

  function close() {
    if (panel.classList.contains('hidden')) return;
    panel.classList.add('hidden');
    if (dirty) {
      dirty = false;
      toast(`${fa(activeKeys().length)} موتور فعال شد`);
      if (['home', 'movies', 'series'].includes(state.tab)) switchTab(state.tab);
    }
  }

  trigger.addEventListener('click', (event) => {
    event.stopPropagation();
    if (panel.classList.contains('hidden')) {
      paintPanel();
      panel.classList.remove('hidden');
    } else close();
  });
  document.addEventListener('click', (event) => {
    if (!panel.contains(event.target)) close();
  });
  paintTrigger();
}

async function boot() {
  state.store = await api.storeGet();
  state.source = state.store.source || 'almasmovie';

  const engines = await api.engines();
  state.engines = engines;
  const saved = Array.isArray(state.store.activeSources) ? state.store.activeSources : [];
  if (!state.store.allEnginesDefaultV1) {
    state.activeSources = engines.map((engine) => engine.key);
    await saveStore('activeSources', state.activeSources);
    await saveStore('allEnginesDefaultV1', true);
  } else {
    state.activeSources = saved.filter((k) => engines.some((e) => e.key === k));
    if (!state.activeSources.length) state.activeSources = engines.map((engine) => engine.key);
  }
  state.source = state.activeSources[0];
  await saveStore('source', state.source);
  buildEngineDrawer();

  state.downloads = await api.dlList().catch(() => []);
  api.onDownloads((list) => {
    state.downloads = list;
    updateDownloadBadge();
    paintDownloads();
  });
  updateDownloadBadge();

  bindShell();
  bindPlayer();
  switchTab('home');

  api
    .announcements()
    .then((list) => {
      const read = new Set(state.store.readAnnouncements || []);
      if (list.some((n) => !read.has(n.id))) document.getElementById('newsDot').classList.add('on');
    })
    .catch(() => {});
}

boot();

const themeSelect = document.getElementById('themeSelect');
const themeQuery = window.matchMedia('(prefers-color-scheme: dark)');

function applyTheme(mode) {
  const resolved = mode === 'system' ? (themeQuery.matches ? 'dark' : 'light') : mode;
  document.documentElement.dataset.resolvedTheme = resolved;
}

function loadThemeMode() {
  try {
    return localStorage.getItem('themeMode') || 'system';
  } catch {
    return 'system';
  }
}

themeSelect.value = loadThemeMode();
applyTheme(themeSelect.value);
themeSelect.addEventListener('change', () => {
  try {
    localStorage.setItem('themeMode', themeSelect.value);
  } catch {}
  applyTheme(themeSelect.value);
});
themeQuery.addEventListener('change', () => {
  if (themeSelect.value === 'system') applyTheme('system');
});

document.querySelectorAll('.win-controls [data-win]').forEach((btn) => {
  btn.addEventListener('click', () => api.winControl(btn.dataset.win));
});
