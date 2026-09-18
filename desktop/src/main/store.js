const fs = require('fs');
const path = require('path');
const { app } = require('electron');

const FILE = path.join(app.getPath('userData'), 'filigram-store.json');

const DEFAULTS = {
  source: 'bj',
  favorites: [],
  history: [],
  playlists: [],
  progress: {},
  readAnnouncements: [],
  searchHistory: [],
  volume: 1,
  externalPlayerPath: ''
};

let state = null;

function load() {
  if (state) return state;
  try {
    state = { ...DEFAULTS, ...JSON.parse(fs.readFileSync(FILE, 'utf8')) };
  } catch (_) {
    state = { ...DEFAULTS };
  }
  return state;
}

function persist() {
  try {
    fs.writeFileSync(FILE, JSON.stringify(state, null, 2), 'utf8');
  } catch (_) {}
}

function getAll() {
  return load();
}

function set(key, value) {
  load();
  state[key] = value;
  persist();
  return state[key];
}

function get(key) {
  return load()[key];
}

module.exports = { getAll, get, set };
