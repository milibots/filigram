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
  player: { item: null, quality: null, key: '' }
};

const view = document.getElementById('view');
const toastEl = document.getElementById('toast');
const engineBar = document.getElementById('engineBar');
const searchInput = document.getElementById('searchInput');
const playerOverlay = document.getElementById('playerOverlay');
const video = document.getElementById('video');

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

function posterFallback(img) {
  img.addEventListener('error', () => {
    img.removeAttribute('src');
    img.alt = '';
    img.classList.add('art-missing');
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
  rezflix: { body: '#5b7de8', band: '#3c53b0', ring: null }
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
  posterFallback(img);
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
  try {
    sections = await api.home(state.source);
  } catch (err) {
    toast(`خطا در دریافت ویترین: ${err.message}`);
  }

  view.textContent = '';
  state.heroItems = (sections[0] && sections[0].items ? sections[0].items : []).slice(0, 6);
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
      const items = await api.listing(state.source, kind, state.listing.page);
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
    items = await api.search(state.source, query, 1);
  } catch (err) {
    toast(`خطا در جستجو: ${err.message}`);
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
  else if (tab === 'news') renderNews();
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
    bannerImg.remove();
    banner.classList.add('no-art');
  });
  if (detail.banner || detail.image) bannerImg.src = detail.banner || detail.image;
  else banner.classList.add('no-art');
  banner.appendChild(bannerImg);
  banner.appendChild(el('div', 'detail-banner-shade'));
  const closeBtn = el('button', 'ghost-btn detail-close', '→  بازگشت');
  closeBtn.addEventListener('click', closeDetail);
  banner.appendChild(closeBtn);
  detailCard.appendChild(banner);

  const body = el('div', 'detail-body');

  const head = el('div', 'detail-head');
  if (detail.image) {
    const poster = el('img', 'detail-poster');
    poster.src = detail.image;
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
        loadEpisodes(detail, season.season, episodesWrap);
      });
      seasonChips.appendChild(chip);
    });
    loadEpisodes(detail, seasons[0].season, episodesWrap);
  } else {
    body.appendChild(el('h3', 'block-title', 'کیفیت‌های پخش'));
    const list = el('div', 'quality-list');
    body.appendChild(list);

    let qualities = detail.directQualities || [];
    if (!qualities.length) {
      list.appendChild(el('div', 'empty', 'در حال دریافت لینک‌ها...'));
      try {
        qualities = await api.qualities(detail.source, detail.id);
      } catch (err) {
        toast(`خطا در دریافت لینک‌ها: ${err.message}`);
      }
      list.textContent = '';
    }

    if (!qualities.length) list.appendChild(el('div', 'empty', 'لینک پخشی برای این عنوان پیدا نشد.'));
    qualities.forEach((q) => list.appendChild(qualityRow(detail, q, '')));
  }

  detailCard.appendChild(body);
  detailCard.scrollTop = 0;
}

async function loadEpisodes(detail, seasonNumber, container) {
  container.textContent = '';
  container.appendChild(el('div', 'empty', 'در حال دریافت قسمت‌ها...'));

  let episodes = [];
  try {
    episodes = await api.episodes(detail.source, detail.id, seasonNumber);
  } catch (err) {
    toast(`خطا در دریافت قسمت‌ها: ${err.message}`);
  }

  container.textContent = '';
  if (!episodes.length) {
    container.appendChild(el('div', 'empty', 'قسمتی برای این فصل پیدا نشد.'));
    return;
  }

  const chips = el('div', 'chips');
  const qualityWrap = el('div', 'quality-list');
  container.appendChild(el('h3', 'block-title', 'قسمت‌ها'));
  container.appendChild(chips);
  container.appendChild(qualityWrap);

  function showEpisode(episode) {
    qualityWrap.textContent = '';
    if (!episode.qualities.length) {
      qualityWrap.appendChild(el('div', 'empty', 'لینکی برای این قسمت موجود نیست.'));
      return;
    }
    episode.qualities.forEach((q) =>
      qualityWrap.appendChild(qualityRow(detail, q, `فصل ${fa(seasonNumber)} — ${episode.title}`))
    );
  }

  episodes.forEach((episode, index) => {
    const chip = el('button', `chip${index === 0 ? ' on' : ''}`, episode.title);
    chip.addEventListener('click', () => {
      chips.querySelectorAll('.chip').forEach((c) => c.classList.remove('on'));
      chip.classList.add('on');
      showEpisode(episode);
    });
    chips.appendChild(chip);
  });
  showEpisode(episodes[0]);
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

async function boot() {
  state.store = await api.storeGet();
  state.source = state.store.source || 'almasmovie';

  const engines = await api.engines();
  engines.forEach((engine) => {
    const btn = el('button', `planet-btn${engine.key === state.source ? ' on' : ''}`);
    btn.dataset.key = engine.key;
    btn.title = `سیاره ${engine.label}`;
    btn.innerHTML = planetSvg(engine.key);
    btn.appendChild(el('span', 'planet-label', engine.label));
    btn.addEventListener('click', async () => {
      if (state.source === engine.key) return;
      state.source = engine.key;
      engineBar.querySelectorAll('.planet-btn').forEach((b) => b.classList.toggle('on', b.dataset.key === engine.key));
      await saveStore('source', state.source);
      toast(`سیاره ${engine.label} انتخاب شد`);
      switchTab('home');
    });
    engineBar.appendChild(btn);
  });

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
