const fs = require('fs');
const path = require('path');
const http = require('http');
const https = require('https');
const { app } = require('electron');

const STATE_FILE = path.join(app.getPath('userData'), 'filigram-downloads.json');
const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36';
const MAX_REDIRECTS = 6;

const settings = { maxConcurrent: 3, segments: 6 };
const tasks = new Map();
const runtime = new Map(); // id -> { reqs:Set, fd, aborted }
let notify = () => {};
let lastBroadcast = 0;
let saveTimer = null;

function sanitize(name) {
  return String(name || 'file').replace(/[\\/:*?"<>|]/g, '_').replace(/\s+/g, ' ').trim().slice(0, 150) || 'file';
}

function publicTask(t) {
  const { segs, ...rest } = t;
  return rest;
}

function broadcast(force) {
  const now = Date.now();
  if (!force && now - lastBroadcast < 350) return;
  lastBroadcast = now;
  notify([...tasks.values()].map(publicTask));
}

function persist() {
  clearTimeout(saveTimer);
  saveTimer = setTimeout(() => {
    try {
      fs.writeFileSync(STATE_FILE, JSON.stringify([...tasks.values()]), 'utf8');
    } catch (_) {
      /* disk errors must not crash the app */
    }
  }, 400);
}

function open(url, headers, redirects = 0) {
  return new Promise((resolve, reject) => {
    const lib = url.startsWith('https') ? https : http;
    const req = lib.get(url, { headers: { 'User-Agent': UA, ...headers } }, (res) => {
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        res.resume();
        if (redirects >= MAX_REDIRECTS) return reject(new Error('too many redirects'));
        const next = new URL(res.headers.location, url).toString();
        return open(next, headers, redirects + 1).then(resolve, reject);
      }
      resolve({ res, req, url });
    });
    req.on('error', reject);
    req.setTimeout(30000, () => req.destroy(new Error('timeout')));
  });
}

async function probe(task) {
  const { res, req, url } = await open(task.url, { Range: 'bytes=0-0' });
  req.destroy();
  res.destroy();
  if (res.statusCode === 206) {
    const m = /\/(\d+)$/.exec(res.headers['content-range'] || '');
    if (m) return { total: Number(m[1]), ranged: true, url };
  }
  if (res.statusCode >= 400) throw new Error(`HTTP ${res.statusCode}`);
  return { total: Number(res.headers['content-length']) || 0, ranged: false, url };
}

function writeAt(fd, buf, position) {
  return new Promise((resolve, reject) => {
    fs.write(fd, buf, 0, buf.length, position, (err) => (err ? reject(err) : resolve()));
  });
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function runSegment(task, rt, seg, url, ranged) {
  let failures = 0;
  while (!rt.aborted && seg.pos <= seg.end) {
    const headers = ranged ? { Range: `bytes=${seg.pos}-${seg.end}` } : {};
    const before = seg.pos;
    try {
      const { res, req } = await open(url, headers);
      if (res.statusCode >= 400) {
        res.resume();
        throw new Error(`HTTP ${res.statusCode}`);
      }
      rt.reqs.add(req);
      await new Promise((resolve, reject) => {
        res.on('data', (chunk) => {
          if (rt.aborted) return;
          res.pause();
          const limit = seg.end - seg.pos + 1;
          const buf = chunk.length > limit ? chunk.subarray(0, limit) : chunk;
          writeAt(rt.fd, buf, seg.pos)
            .then(() => {
              seg.pos += buf.length;
              task.downloaded += buf.length;
              rt.window += buf.length;
              if (!rt.aborted) res.resume();
            })
            .catch(reject);
        });
        res.on('end', resolve);
        res.on('close', resolve);
        res.on('error', reject);
      });
      rt.reqs.delete(req);
      if (!ranged) break;
      if (!rt.aborted && seg.pos === before) throw new Error('server returned no data');
      failures = 0;
    } catch (err) {
      if (rt.aborted) return;
      // Servers often drop or throttle parallel connections: back off and retry this segment.
      failures += 1;
      if (!ranged || failures > 8) throw err;
      await sleep(Math.min(1000 * failures, 5000));
    }
  }
}

async function execute(task) {
  const rt = { reqs: new Set(), fd: null, aborted: false, window: 0, timer: null };
  runtime.set(task.id, rt);
  task.status = 'connecting';
  task.error = '';
  broadcast(true);

  try {
    if (!task.segs || !task.segs.length) {
      const info = await probe(task);
      task.total = info.total;
      task.ranged = info.ranged;
      task.resolvedUrl = info.url;
      const count = info.ranged && info.total > 4 * 1024 * 1024 ? settings.segments : 1;
      const size = info.total ? Math.ceil(info.total / count) : 0;
      task.segs = [];
      for (let i = 0; i < count; i += 1) {
        const start = i * size;
        const end = info.total ? Math.min(info.total - 1, start + size - 1) : Number.MAX_SAFE_INTEGER;
        task.segs.push({ start, end, pos: start });
      }
      task.downloaded = 0;
    }

    fs.mkdirSync(path.dirname(task.filePath), { recursive: true });
    const partPath = `${task.filePath}.part`;
    rt.fd = fs.openSync(partPath, fs.existsSync(partPath) ? 'r+' : 'w+');

    task.status = 'downloading';
    rt.timer = setInterval(() => {
      task.speed = rt.window * 2;
      rt.window = 0;
      broadcast(true);
      persist();
    }, 500);

    const url = task.resolvedUrl || task.url;
    const open_ = task.segs.filter((s) => s.pos <= s.end);
    await Promise.all(open_.map((seg) => runSegment(task, rt, seg, url, task.ranged)));

    if (rt.aborted) return;
    if (task.total && task.downloaded < task.total) throw new Error('incomplete download');
    fs.closeSync(rt.fd);
    rt.fd = null;
    fs.renameSync(partPath, task.filePath);
    task.status = 'completed';
    task.speed = 0;
    task.segs = [];
  } catch (err) {
    if (!rt.aborted) {
      task.status = 'failed';
      task.error = String(err && err.message ? err.message : err);
    }
  } finally {
    clearInterval(rt.timer);
    rt.reqs.forEach((r) => r.destroy());
    if (rt.fd !== null) {
      try {
        fs.closeSync(rt.fd);
      } catch (_) {
        /* already closed */
      }
    }
    runtime.delete(task.id);
    if (task.status === 'downloading' || task.status === 'connecting') task.status = 'paused';
    task.speed = 0;
    broadcast(true);
    persist();
    pump();
  }
}

function pump() {
  const active = [...tasks.values()].filter((t) => t.status === 'connecting' || t.status === 'downloading').length;
  let free = settings.maxConcurrent - active;
  for (const task of tasks.values()) {
    if (free <= 0) break;
    if (task.status === 'queued' && !runtime.has(task.id)) {
      free -= 1;
      execute(task);
    }
  }
}

function enqueue(dir, items) {
  fs.mkdirSync(dir, { recursive: true });
  const added = [];
  items.forEach((item) => {
    if (!item || !item.url) return;
    const ext = path.extname(new URL(item.url).pathname) || '.mp4';
    let base = sanitize(item.title);
    let filePath = path.join(dir, `${base}${ext}`);
    for (let n = 2; fs.existsSync(filePath) || [...tasks.values()].some((t) => t.filePath === filePath); n += 1) {
      filePath = path.join(dir, `${base} (${n})${ext}`);
    }
    const task = {
      id: `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`,
      title: item.title,
      url: item.url,
      filePath,
      total: 0,
      downloaded: 0,
      speed: 0,
      status: 'queued',
      error: '',
      createdAt: Date.now(),
      segs: []
    };
    tasks.set(task.id, task);
    added.push(task.id);
  });
  broadcast(true);
  persist();
  pump();
  return added;
}

function stopRuntime(id) {
  const rt = runtime.get(id);
  if (!rt) return;
  rt.aborted = true;
  rt.reqs.forEach((r) => r.destroy());
}

function pause(id) {
  const t = tasks.get(id);
  if (!t) return;
  if (t.status === 'queued') t.status = 'paused';
  else if (runtime.has(id)) {
    t.status = 'paused';
    stopRuntime(id);
  }
  broadcast(true);
  persist();
}

function resume(id) {
  const t = tasks.get(id);
  if (!t || !['paused', 'failed', 'cancelled'].includes(t.status)) return;
  t.status = 'queued';
  t.error = '';
  broadcast(true);
  pump();
}

function remove(id, deleteFile) {
  const t = tasks.get(id);
  if (!t) return;
  stopRuntime(id);
  tasks.delete(id);
  if (deleteFile) {
    [t.filePath, `${t.filePath}.part`].forEach((f) => {
      try {
        fs.unlinkSync(f);
      } catch (_) {
        /* missing file is fine */
      }
    });
  }
  broadcast(true);
  persist();
  pump();
}

function clearCompleted() {
  [...tasks.values()].filter((t) => t.status === 'completed').forEach((t) => tasks.delete(t.id));
  broadcast(true);
  persist();
}

function init(onUpdate) {
  notify = onUpdate;
  try {
    const saved = JSON.parse(fs.readFileSync(STATE_FILE, 'utf8'));
    saved.forEach((t) => {
      if (['connecting', 'downloading', 'queued'].includes(t.status)) t.status = 'paused';
      t.speed = 0;
      tasks.set(t.id, t);
    });
  } catch (_) {
    /* first run */
  }
}

module.exports = {
  init,
  settings,
  list: () => [...tasks.values()].map(publicTask),
  enqueue,
  pause,
  resume,
  remove,
  clearCompleted,
  pauseAll: () => [...tasks.keys()].forEach(pause),
  resumeAll: () => [...tasks.keys()].forEach(resume)
};
