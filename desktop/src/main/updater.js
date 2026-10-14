const fs = require('fs');
const https = require('https');
const path = require('path');
const { app, dialog } = require('electron');
const { spawn } = require('child_process');

const API_URL = 'https://api.github.com/repos/milibots/filigram/releases/latest';
const USER_AGENT = 'Filigram-Windows-Updater';

function requestJson(url) {
  return new Promise((resolve, reject) => {
    https.get(url, { headers: { 'User-Agent': USER_AGENT, Accept: 'application/vnd.github+json' } }, (res) => {
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        res.resume();
        return requestJson(res.headers.location).then(resolve, reject);
      }
      let body = '';
      res.setEncoding('utf8');
      res.on('data', (chunk) => { body += chunk; });
      res.on('end', () => {
        if (res.statusCode < 200 || res.statusCode >= 300) return reject(new Error(`GitHub HTTP ${res.statusCode}`));
        try { resolve(JSON.parse(body)); } catch (error) { reject(error); }
      });
    }).on('error', reject);
  });
}

function download(url, target, onProgress) {
  return new Promise((resolve, reject) => {
    const get = (currentUrl) => {
      https.get(currentUrl, { headers: { 'User-Agent': USER_AGENT } }, (res) => {
        if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
          res.resume();
          return get(res.headers.location);
        }
        if (res.statusCode < 200 || res.statusCode >= 300) {
          res.resume();
          return reject(new Error(`Download HTTP ${res.statusCode}`));
        }
        const total = Number(res.headers['content-length']) || 0;
        let received = 0;
        const file = fs.createWriteStream(target);
        res.on('data', (chunk) => {
          received += chunk.length;
          if (total) onProgress(received / total);
        });
        res.pipe(file);
        file.on('finish', () => file.close(() => resolve(target)));
        file.on('error', reject);
      }).on('error', reject);
    };
    get(url);
  });
}

function parts(version) {
  return String(version).replace(/^v/, '').split('-')[0].split('.').map((n) => Number(n) || 0);
}

function isNewer(remote, local) {
  const a = parts(remote);
  const b = parts(local);
  for (let i = 0; i < Math.max(a.length, b.length); i += 1) {
    if ((a[i] || 0) !== (b[i] || 0)) return (a[i] || 0) > (b[i] || 0);
  }
  return false;
}

async function check(mainWindow, manual = false) {
  try {
    const release = await requestJson(API_URL);
    const version = String(release.tag_name || '').replace(/^v/, '');
    const asset = (release.assets || []).find((item) => item.name === `Filigram-Cinema-${version}-Windows-Setup.exe`);
    if (!version || !asset || !asset.browser_download_url) throw new Error('Windows Setup asset not found');
    if (!isNewer(version, app.getVersion())) {
      if (manual) await dialog.showMessageBox(mainWindow, {
        type: 'info', title: 'بروزرسانی فیلیگرام', message: 'آخرین نسخه نصب است', detail: `نسخه ${app.getVersion()}`
      });
      return { available: false, version: app.getVersion() };
    }
    const answer = await dialog.showMessageBox(mainWindow, {
      type: 'info',
      title: 'نسخه جدید فیلیگرام',
      message: `نسخه ${version} آماده نصب است`,
      detail: `نسخه فعلی ${app.getVersion()} است. فایل رسمی GitHub دانلود و نصب می‌شود.`,
      buttons: ['دانلود و نصب', 'بعداً'],
      defaultId: 0,
      cancelId: 1
    });
    if (answer.response !== 0) return { available: true, version, accepted: false };
    const target = path.join(app.getPath('temp'), `Filigram-Cinema-${version}-Windows-Setup.exe`);
    mainWindow.setProgressBar(0.01);
    await download(asset.browser_download_url, target, (value) => mainWindow.setProgressBar(value));
    mainWindow.setProgressBar(-1);
    spawn(target, [], { detached: true, stdio: 'ignore', windowsHide: false }).unref();
    setTimeout(() => app.quit(), 500);
    return { available: true, version, accepted: true };
  } catch (error) {
    if (mainWindow && !mainWindow.isDestroyed()) mainWindow.setProgressBar(-1);
    if (manual) await dialog.showMessageBox(mainWindow, {
      type: 'error', title: 'خطای بروزرسانی', message: 'بررسی یا دانلود نسخه جدید انجام نشد', detail: String(error.message || error)
    });
    return { available: false, error: String(error.message || error) };
  }
}

module.exports = { check, isNewer };
