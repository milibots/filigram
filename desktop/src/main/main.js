const path = require('path');
const fs = require('fs');
const { spawn } = require('child_process');
const { app, BrowserWindow, ipcMain, shell, clipboard, session, Menu, dialog } = require('electron');

const api = require('./api');
const store = require('./store');
const downloader = require('./downloader');
const updater = require('./updater');

const VLC_CANDIDATES = [
  'C:\\Program Files\\VideoLAN\\VLC\\vlc.exe',
  'C:\\Program Files (x86)\\VideoLAN\\VLC\\vlc.exe',
  'C:\\Program Files\\MPV\\mpv.exe'
];

let mainWindow = null;

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 820,
    minWidth: 980,
    minHeight: 640,
    backgroundColor: '#000000',
    title: 'Filigram Cinema',
    icon: path.join(__dirname, '..', '..', 'assets', 'icon.png'),
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false
    }
  });

  Menu.setApplicationMenu(null);
  mainWindow.loadFile(path.join(__dirname, '..', 'renderer', 'index.html'));

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url);
    return { action: 'deny' };
  });
}

function findExternalPlayer() {
  const configured = store.get('externalPlayerPath');
  if (configured && fs.existsSync(configured)) return configured;
  return VLC_CANDIDATES.find((p) => fs.existsSync(p)) || null;
}

function handle(channel, fn) {
  ipcMain.handle(channel, async (_event, ...args) => {
    try {
      return { ok: true, data: await fn(...args) };
    } catch (err) {
      return { ok: false, error: String(err && err.message ? err.message : err) };
    }
  });
}

app.whenReady().then(async () => {
  // Some CDNs reject requests without a referer or with an Electron user agent.
  session.defaultSession.webRequest.onBeforeSendHeaders((details, callback) => {
    const headers = { ...details.requestHeaders };
    if (details.resourceType === 'media' || details.resourceType === 'xhr') {
      headers['User-Agent'] = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36';
    }
    callback({ requestHeaders: headers });
  });

  handle('engines', () => api.engineList());
  handle('reloadSources', () => api.loadSources());
  handle('home', (source) => api.homeSections(source));
  handle('listing', (source, kind, page) => api.listing(source, kind, page));
  handle('search', (source, query, page) => api.search(source, query, page));
  handle('details', (source, id, type) => api.details(source, id, type));
  handle('episodes', (source, id, season) => api.episodes(source, id, season));
  handle('qualities', (source, id) => api.qualities(source, id));
  handle('genres', () => api.genres());
  handle('genrePosts', (slug, page) => api.postsByGenre(slug, page));
  handle('comments', (source, id, page) => api.comments(source, id, page));
  handle('banners', () => api.banners());
  handle('announcements', () => api.announcements());
  handle('checkForUpdates', (manual = true) => updater.check(mainWindow, !!manual));

  handle('storeGet', () => store.getAll());
  handle('storeSet', (key, value) => store.set(key, value));

  downloader.init((list) => {
    if (mainWindow && !mainWindow.isDestroyed()) mainWindow.webContents.send('dl:update', list);
  });

  function downloadDir() {
    return store.get('downloadDir') || path.join(app.getPath('downloads'), 'Filigram');
  }

  handle('dlList', () => downloader.list());
  handle('dlEnqueue', (items) => downloader.enqueue(downloadDir(), items));
  handle('dlPause', (id) => downloader.pause(id));
  handle('dlResume', (id) => downloader.resume(id));
  handle('dlRemove', (id, deleteFile) => downloader.remove(id, !!deleteFile));
  handle('dlPauseAll', () => downloader.pauseAll());
  handle('dlResumeAll', () => downloader.resumeAll());
  handle('dlClearCompleted', () => downloader.clearCompleted());
  handle('dlShow', (id) => {
    const t = downloader.list().find((x) => x.id === id);
    if (t) shell.showItemInFolder(fs.existsSync(t.filePath) ? t.filePath : `${t.filePath}.part`);
  });
  handle('dlOpenFolder', () => {
    fs.mkdirSync(downloadDir(), { recursive: true });
    return shell.openPath(downloadDir());
  });
  handle('exportLinks', async (format, rows, baseName) => {
    const cell = (v) => `"${String(v === undefined || v === null ? '' : v).replace(/"/g, '""')}"`;
    const csv = format === 'csv';
    const header = ['series', 'season', 'episode', 'quality', 'size', 'url'].join(',');
    const body = rows.map((r) => [r.series, r.season, r.episode, r.quality, r.size, r.url].map(cell).join(','));
    const text = csv ? `\uFEFF${[header, ...body].join('\r\n')}` : rows.map((r) => r.url).join('\r\n');
    const safe = String(baseName || 'links').replace(/[\\/:*?"<>|]/g, '_');
    const res = await dialog.showSaveDialog(mainWindow, {
      defaultPath: path.join(app.getPath('documents'), `${safe}.${csv ? 'csv' : 'txt'}`),
      filters: [csv ? { name: 'CSV', extensions: ['csv'] } : { name: 'Text', extensions: ['txt'] }]
    });
    if (res.canceled || !res.filePath) return null;
    fs.writeFileSync(res.filePath, text, 'utf8');
    return res.filePath;
  });

  handle('openExternal', (url) => shell.openExternal(url));
  handle('copyText', (text) => clipboard.writeText(String(text || '')));
  handle('externalPlayer', () => findExternalPlayer());
  handle('playExternal', (url) => {
    const player = findExternalPlayer();
    if (!player) return false;
    spawn(player, [url], { detached: true, stdio: 'ignore' }).unref();
    return true;
  });

  // Source config decides base urls, headers and which planets are shown, so give it a
  // short head start; the bundled fallbacks take over if the servers are slow.
  await Promise.race([
    api.loadSources().catch(() => null),
    new Promise((resolve) => setTimeout(resolve, 3500))
  ]);

  createWindow();
  mainWindow.webContents.once('did-finish-load', () => {
    setTimeout(() => updater.check(mainWindow, false), 2500);
  });

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
