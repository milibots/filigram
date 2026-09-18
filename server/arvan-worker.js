// Filigram remote config mirror (ArvanCloud edge, no KV/database).
//
// Third tier of the client fallback chain: Cloudflare worker -> GitHub raw -> here.
// Keep the values below in sync with server/data.json and the Cloudflare worker.
//
// Fill these two before deploying if you want install notifications from this tier.
// They are intentionally empty in the repository so the bot tokens stay out of git.
const BALE_BOT_TOKEN = "";
const BALE_CHAT_ID = "";

const CONFIG_VERSION = 2;

const APP_CONFIGS = {
  app_version: "1.2.0",
  force_update: "false",
  update_url: "https://github.com/milibots/filigram/releases/latest",
  maintenance_mode: "false",
  support_telegram: "@filigramapp",
  desktop_min_version: "1.2.0",
  android_min_version_code: "6",
};

const APP_BANNERS = [];

const ANNOUNCEMENTS = [
  {
    id: "announce-desktop-1",
    title: "نسخه ویندوز فیلیگرام منتشر شد",
    text: "حالا می‌توانید فیلم‌ها و سریال‌ها را روی ویندوز هم تماشا کنید. نسخه پرتابل و نصبی از بخش ریلیزهای گیت‌هاب در دسترس است.",
    views: 0,
    createdAt: "2026-09-18T00:00:00.000Z",
  },
];

const APP_SOURCES = [
  {
    key: "bj",
    label: "مشتری",
    planet: "jupiter",
    enabled: true,
    order: 1,
    base_url: "https://forooshonline20.ir/wp-json/mapi/v1",
    headers: { "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36" },
    cookies: "",
    auth: {},
    image_rewrite: [["https://forooshonline20.ir/wp-content/", "https://seo2024.ir/wp-content/"]],
    notes: "Iran-only IPs. Upload host answers 403, posters come from the CDN mirror.",
  },
  {
    key: "nextmovie",
    label: "زحل",
    planet: "saturn",
    enabled: true,
    order: 2,
    base_url: "https://mihan-cdn.com",
    headers: {
      Authorization: "Bearer __SET_IN_DASHBOARD__",
      Platform: "android/6.4",
      "User-Agent": "okhttp/5.5.0",
    },
    cookies: "",
    auth: {},
    image_rewrite: [],
    notes: "Search rejects type=all and rejects title combined with type.",
  },
  {
    key: "almasmovie",
    label: "اورانوس",
    planet: "uranus",
    enabled: true,
    order: 3,
    base_url: "https://almasandroid.com/api/almas/v1",
    headers: {
      "User-Agent": "Dalvik/2.1.0 (Linux; U; Android 9; G576D Build/PQ3B.190801.04221524)",
      "X-Almas-App-Version-Code": "8",
      "X-Almas-App-Version-Name": "3.0.0",
      "X-Almas-Client": "android",
      "X-Almas-Device-Fingerprint-Hash": "06073e34483010815f52e0b9cc1bb0378a2592cb8d4163759f51f513ab74f972",
      "X-Almas-Device-Id": "00d04fe2-dd3b-4d14-b2ba-88469cb8a01f",
      "X-Almas-Device-Manufacturer": "Google Phone",
      "X-Almas-Device-Model": "G576D",
      "X-Almas-Device-Name": "Google Phone G576D",
      "X-Almas-OS-Version": "9",
      "X-Almas-Platform": "android_mobile",
    },
    cookies: "",
    auth: {
      access_token: "__SET_IN_DASHBOARD__",
      refresh_token: "__SET_IN_DASHBOARD__",
    },
    image_rewrite: [],
    notes: "Play links need a live member token. Replace both tokens when they expire.",
  },
  {
    key: "rezflix",
    label: "نپتون",
    planet: "neptune",
    enabled: true,
    order: 4,
    base_url: "http://server-win-iran.info",
    headers: { "User-Agent": "okhttp/4.12.0" },
    cookies: "",
    auth: { token: "__SET_IN_DASHBOARD__" },
    image_rewrite: [],
    notes: "Browse only. Detail payload carries no playable links.",
  },
  {
    key: "movielix",
    label: "زهره",
    planet: "venus",
    enabled: true,
    order: 5,
    base_url: "https://expertappmedia.org/api-v1",
    headers: {},
    cookies: "",
    auth: {
      token: "__SET_IN_DASHBOARD__",
      register_url: "https://global-api2.expertmedias.org/apiMovielix.php",
    },
    image_rewrite: [],
    notes: "Android only. Registers its own device token at runtime.",
  },
];

addEventListener("fetch", (event) => {
  event.respondWith(handleRequest(event.request, event));
});

function jsonResponse(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, POST, HEAD, OPTIONS",
      "Access-Control-Allow-Headers": "*",
      "Cache-Control": "public, max-age=60",
    },
  });
}

async function handleRequest(request, event) {
  const url = new URL(request.url);

  if (request.method === "OPTIONS") {
    return new Response(null, {
      status: 204,
      headers: {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Methods": "GET, POST, HEAD, OPTIONS",
        "Access-Control-Allow-Headers": "Content-Type",
      },
    });
  }

  if ((url.pathname === "/new_installiotn" || url.pathname === "/new_installation") && request.method === "POST") {
    try {
      const data = await request.json();

      const brand = data.mobile?.manufacturer || "Unknown";
      const model = data.mobile?.model || "Unknown";
      const name = data.mobile?.name || "";
      const osVer = data.os?.android_version || "Unknown";
      const sdkInt = data.os?.sdk_int || "";
      const appName = data.app?.app_name || "Unknown App";
      const appVer = data.app?.version_name || "";
      const platform = data.app?.platform || data.os?.platform || "android";
      const cpu = data.cpu?.soc_model || "Unknown";
      const cores = data.cpu?.cores_count || "";
      const resolution = data.display?.resolution || "Unknown";
      const deviceId = data.hardware?.android_id || data.hardware?.device_id || "N/A";
      const installTime = data.installed_at || new Date().toISOString();

      const messageText =
        `<b>🔔 نصب جدید اپلیکیشن!</b>\n\n` +
        `<b>📱 دستگاه:</b> ${brand} ${model} (${name})\n` +
        `<b>🖥️ پلتفرم:</b> ${platform}\n` +
        `<b>🤖 سیستم‌عامل:</b> ${osVer} (SDK ${sdkInt})\n` +
        `<b>📦 اپلیکیشن:</b> ${appName} v${appVer}\n` +
        `<b>⚙️ پردازنده:</b> CPU: ${cpu} (${cores} cores)\n` +
        `<b>📺 صفحه نمایش:</b> ${resolution}\n` +
        `<b>🆔 شناسه دستگاه:</b> <code>${deviceId}</code>\n` +
        `<b>🕒 زمان نصب:</b> ${installTime}`;

      if (!BALE_BOT_TOKEN || !BALE_CHAT_ID) {
        return jsonResponse({ success: true, message: "Notification skipped (no bot configured)" });
      }

      const baleRequest = fetch(`https://tapi.bale.ai/bot${BALE_BOT_TOKEN}/sendMessage`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ chat_id: BALE_CHAT_ID, text: messageText, parse_mode: "HTML" }),
      }).catch((err) => console.error("Bale Notification Failed:", err));

      if (event && event.waitUntil) {
        event.waitUntil(baleRequest);
      } else {
        await baleRequest;
      }

      return jsonResponse({ success: true, message: "Notification triggered" });
    } catch (err) {
      return jsonResponse({ error: "Invalid payload format" }, 400);
    }
  }

  if (url.pathname === "/api/data" && request.method === "GET") {
    return jsonResponse({
      config_version: CONFIG_VERSION,
      configs: APP_CONFIGS,
      banners: [...APP_BANNERS].sort((a, b) => (a.order || 0) - (b.order || 0)),
      announcements: ANNOUNCEMENTS,
      sources: APP_SOURCES,
    });
  }

  if (url.pathname === "/api/sources" && request.method === "GET") {
    const onlyEnabled = url.searchParams.get("all") !== "1";
    const list = [...APP_SOURCES].sort((a, b) => (a.order || 0) - (b.order || 0));
    return jsonResponse(onlyEnabled ? list.filter((s) => s.enabled !== false) : list);
  }

  if (url.pathname === "/api/banners" && request.method === "GET") {
    return jsonResponse([...APP_BANNERS].sort((a, b) => (a.order || 0) - (b.order || 0)));
  }

  if (url.pathname === "/api/config" && request.method === "GET") {
    return jsonResponse(APP_CONFIGS);
  }

  if (url.pathname === "/chekhabar" && request.method === "GET") {
    const targetId = url.searchParams.get("id");
    if (targetId) {
      const item = ANNOUNCEMENTS.find((i) => i.id === targetId);
      return item ? jsonResponse(item) : jsonResponse({ error: "Not found" }, 404);
    }
    return jsonResponse(ANNOUNCEMENTS);
  }

  return new Response("Not Found", { status: 404 });
}
