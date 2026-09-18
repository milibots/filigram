const { getJson, nonEmpty } = require('./http');

const CF_BASE_URL = 'https://filigramv1.miladjobs22.workers.dev';
const GITHUB_RAW_URL = 'https://raw.githubusercontent.com/milibots/filigram_config/refs/heads/main/data.json';
const ARVAN_BASE_URL = 'https://filmapi1.milaadfarzian-tnljt.arvanedge.ir';

const HEADERS = { 'User-Agent': 'Filigram-Desktop-Client/1.0', Accept: 'application/json' };

function pickArray(payload, field) {
  if (Array.isArray(payload)) return payload;
  if (!payload || typeof payload !== 'object') return null;
  const candidates = [payload[field], payload.value, payload.data];
  const found = candidates.find((c) => Array.isArray(c));
  return found || null;
}

async function fetchWithFallback(path, field) {
  const tiers = [
    { url: `${CF_BASE_URL}${path}`, timeoutMs: 3000 },
    { url: GITHUB_RAW_URL, timeoutMs: 3000 },
    { url: `${ARVAN_BASE_URL}${path}`, timeoutMs: 5000 }
  ];
  for (const tier of tiers) {
    const { json } = await getJson(tier.url, { headers: HEADERS, timeoutMs: tier.timeoutMs });
    const arr = pickArray(json, field);
    if (arr && arr.length) return arr;
  }
  return [];
}

async function getBanners() {
  const arr = await fetchWithFallback('/api/banners', 'banners');
  return arr
    .map((item, i) => {
      const image = nonEmpty(item.imageUrl, item.image);
      if (!image) return null;
      return {
        id: nonEmpty(item.id) || `banner-${i}`,
        image,
        targetUrl: nonEmpty(item.targetUrl, item.url) || null,
        title: nonEmpty(item.title) || null,
        order: Number.isFinite(item.order) ? item.order : i
      };
    })
    .filter(Boolean)
    .sort((a, b) => a.order - b.order);
}

async function getAnnouncements() {
  const arr = await fetchWithFallback('/chekhabar', 'announcements');
  const list = arr.map((item, i) => ({
    id: nonEmpty(item.id) || `announcement-${i}`,
    title: nonEmpty(item.title) || 'اعلان رسمی فیلیگرام',
    text: nonEmpty(item.text),
    createdAt: nonEmpty(item.createdAt),
    views: Number.isFinite(item.views) ? item.views : 0
  }));

  if (!list.length) {
    return [
      {
        id: 'filigram_welcome_desktop_v1',
        title: 'خوش‌آمدید به فیلیگرام نسخه ویندوز 🎬',
        text:
          'نسخه دسکتاپ فیلیگرام برای ویندوز آماده است.\n\n' +
          '• هرگونه نظر، پیشنهاد یا گزارش باگ را در تلگرام به @kiorcode ارسال کنید.\n' +
          '• برای دریافت آخرین به‌روزرسانی‌ها در کانال @filigramapp عضو شوید.',
        createdAt: '',
        views: 0
      }
    ];
  }
  return list;
}

module.exports = { getBanners, getAnnouncements };
