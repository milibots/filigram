const { contextBridge, ipcRenderer } = require('electron');

async function invoke(channel, ...args) {
  const res = await ipcRenderer.invoke(channel, ...args);
  if (!res || res.ok !== true) throw new Error((res && res.error) || 'unknown error');
  return res.data;
}

contextBridge.exposeInMainWorld('filigram', {
  winControl: (action) => ipcRenderer.send('winControl', action),
  engines: () => invoke('engines'),
  reloadSources: () => invoke('reloadSources'),
  home: (source) => invoke('home', source),
  listing: (source, kind, page) => invoke('listing', source, kind, page),
  search: (source, query, page) => invoke('search', source, query, page),
  details: (source, id, type) => invoke('details', source, id, type),
  episodes: (source, id, season) => invoke('episodes', source, id, season),
  qualities: (source, id) => invoke('qualities', source, id),
  genres: () => invoke('genres'),
  genrePosts: (slug, page) => invoke('genrePosts', slug, page),
  comments: (source, id, page) => invoke('comments', source, id, page),
  banners: () => invoke('banners'),
  announcements: () => invoke('announcements'),
  checkForUpdates: (manual = true) => invoke('checkForUpdates', manual),
  storeGet: () => invoke('storeGet'),
  storeSet: (key, value) => invoke('storeSet', key, value),
  openExternal: (url) => invoke('openExternal', url),
  copyText: (text) => invoke('copyText', text),
  externalPlayer: () => invoke('externalPlayer'),
  playExternal: (url) => invoke('playExternal', url),
  dlList: () => invoke('dlList'),
  dlEnqueue: (items) => invoke('dlEnqueue', items),
  dlPause: (id) => invoke('dlPause', id),
  dlResume: (id) => invoke('dlResume', id),
  dlRemove: (id, deleteFile) => invoke('dlRemove', id, deleteFile),
  dlPauseAll: () => invoke('dlPauseAll'),
  dlResumeAll: () => invoke('dlResumeAll'),
  dlClearCompleted: () => invoke('dlClearCompleted'),
  dlShow: (id) => invoke('dlShow', id),
  dlOpenFolder: () => invoke('dlOpenFolder'),
  exportLinks: (format, rows, baseName) => invoke('exportLinks', format, rows, baseName),
  onDownloads: (cb) => ipcRenderer.on('dl:update', (_e, list) => cb(list))
});
