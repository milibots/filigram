// Filigram remote config worker (Cloudflare).
//
// Secrets are read from worker bindings, never from this file:
//   wrangler secret put TG_BOT_TOKEN
//   wrangler secret put TG_CHAT_ID
//   wrangler secret put BALE_BOT_TOKEN
//   wrangler secret put BALE_CHAT_ID
//   wrangler secret put ADMIN_USERNAME
//   wrangler secret put ADMIN_PASSWORD
//   wrangler secret put ADMIN_SESSION_TOKEN
//
// KV binding: FILIGRMA
// Public endpoints: /api/data /api/sources /api/config /api/banners /chekhabar

const CONFIG_VERSION = 2;

// Seeded into KV the first time /api/sources or the dashboard is hit.
const DEFAULT_SOURCES = [
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

// --- Helper Functions ---
function getCookie(request, name) {
  const cookieStr = request.headers.get("Cookie");
  if (!cookieStr) return null;
  const cookies = Object.fromEntries(
    cookieStr.split(";").map((c) => {
      const [k, ...v] = c.trim().split("=");
      return [k, decodeURIComponent(v.join("="))];
    })
  );
  return cookies[name] || null;
}

function escapeHtml(str) {
  return String(str || "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}

function jsonResponse(data, status = 200) {
  return Response.json(data, {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Access-Control-Allow-Origin": "*",
      "Cache-Control": "public, max-age=60",
    },
  });
}

async function readList(env, key, fallback = []) {
  const raw = await env.FILIGRMA.get(key);
  if (!raw) return fallback;
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : fallback;
  } catch (_) {
    return fallback;
  }
}

async function readSources(env) {
  const raw = await env.FILIGRMA.get("app_sources");
  if (!raw) {
    await env.FILIGRMA.put("app_sources", JSON.stringify(DEFAULT_SOURCES));
    return DEFAULT_SOURCES;
  }
  try {
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed) || parsed.length === 0) return DEFAULT_SOURCES;
    return parsed.sort((a, b) => (a.order || 0) - (b.order || 0));
  } catch (_) {
    return DEFAULT_SOURCES;
  }
}

function configsToObject(list) {
  return list.reduce((acc, curr) => {
    acc[curr.key] = curr.value;
    return acc;
  }, {});
}

export default {
  async fetch(request, env, ctx) {
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

    // -------------------------------------------------------------
    // 1. PUBLIC APIS & NOTIFICATIONS
    // -------------------------------------------------------------

    // NOTIFICATION: New App Installation (Supports both Telegram & Bale)
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
          `<b>🔔 New App Installation!</b>\n\n` +
          `<b>📱 Device:</b> ${brand} ${model} (${name})\n` +
          `<b>🖥️ Platform:</b> ${platform}\n` +
          `<b>🤖 OS:</b> ${osVer} (SDK ${sdkInt})\n` +
          `<b>📦 App:</b> ${appName} v${appVer}\n` +
          `<b>⚙️ Hardware:</b> CPU: ${cpu} (${cores} cores)\n` +
          `<b>📺 Display:</b> ${resolution}\n` +
          `<b>🆔 Device ID:</b> <code>${deviceId}</code>\n` +
          `<b>🕒 Time:</b> ${installTime}`;

        const deliveries = [];

        if (env.TG_BOT_TOKEN && env.TG_CHAT_ID) {
          deliveries.push(
            fetch(`https://api.telegram.org/bot${env.TG_BOT_TOKEN}/sendMessage`, {
              method: "POST",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify({ chat_id: env.TG_CHAT_ID, text: messageText, parse_mode: "HTML" }),
            })
          );
        }

        if (env.BALE_BOT_TOKEN && env.BALE_CHAT_ID) {
          deliveries.push(
            fetch(`https://tapi.bale.ai/bot${env.BALE_BOT_TOKEN}/sendMessage`, {
              method: "POST",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify({ chat_id: env.BALE_CHAT_ID, text: messageText, parse_mode: "HTML" }),
            })
          );
        }

        ctx.waitUntil(Promise.allSettled(deliveries));

        return jsonResponse({ success: true, message: "Notification triggered", delivered: deliveries.length });
      } catch (err) {
        return jsonResponse({ error: "Invalid payload format" }, 400);
      }
    }

    // Full config bundle: one call for clients (same shape as the GitHub raw mirror)
    if (url.pathname === "/api/data" && request.method === "GET") {
      const [configs, banners, announcements, sources] = await Promise.all([
        readList(env, "app_configs"),
        readList(env, "app_banners"),
        readList(env, "announcements"),
        readSources(env),
      ]);

      banners.sort((a, b) => (a.order || 0) - (b.order || 0));

      return jsonResponse({
        config_version: CONFIG_VERSION,
        configs: configsToObject(configs),
        banners,
        announcements,
        sources,
      });
    }

    // Sources API (base urls, headers, cookies, tokens, per-source kill switch)
    if (url.pathname === "/api/sources" && request.method === "GET") {
      const sources = await readSources(env);
      const onlyEnabled = url.searchParams.get("all") !== "1";
      return jsonResponse(onlyEnabled ? sources.filter((s) => s.enabled !== false) : sources);
    }

    // Banners API (Returns ordered banners list)
    if (url.pathname === "/api/banners" && request.method === "GET") {
      const banners = await readList(env, "app_banners");
      banners.sort((a, b) => (a.order || 0) - (b.order || 0));
      return jsonResponse(banners);
    }

    // Configs API (Returns clean key-value object)
    if (url.pathname === "/api/config" && request.method === "GET") {
      const configs = await readList(env, "app_configs");
      return jsonResponse(configsToObject(configs));
    }

    // Announcements API
    if (url.pathname === "/chekhabar" && request.method === "GET") {
      let items = await readList(env, "announcements");

      const targetId = url.searchParams.get("id");
      if (targetId) {
        const item = items.find((i) => i.id === targetId);
        if (item) {
          item.views = (item.views || 0) + 1;
          ctx.waitUntil(env.FILIGRMA.put("announcements", JSON.stringify(items)));
          return jsonResponse(item);
        }
        return jsonResponse({ error: "Not found" }, 404);
      }

      items = items.map((item) => ({ ...item, views: (item.views || 0) + 1 }));
      ctx.waitUntil(env.FILIGRMA.put("announcements", JSON.stringify(items)));

      return jsonResponse(items);
    }

    // -------------------------------------------------------------
    // 2. AUTHENTICATION & LOGIN / LOGOUT
    // -------------------------------------------------------------
    const sessionValue = env.ADMIN_SESSION_TOKEN || "milad_auth_active";
    const sessionToken = getCookie(request, "auth_session");
    const isAuthenticated = sessionToken === sessionValue;

    if (url.pathname === "/admin/login" && request.method === "POST") {
      const form = await request.formData();
      const u = form.get("username")?.toString().trim();
      const p = form.get("password")?.toString().trim();

      if (u === (env.ADMIN_USERNAME || "milad") && p === (env.ADMIN_PASSWORD || "milad")) {
        return new Response(null, {
          status: 303,
          headers: {
            Location: "/admin",
            "Set-Cookie": `auth_session=${sessionValue}; Path=/; HttpOnly; Secure; Max-Age=86400; SameSite=Lax`,
          },
        });
      }
      return Response.redirect(`${url.origin}/admin?error=1`, 303);
    }

    if (url.pathname === "/admin/logout") {
      return new Response(null, {
        status: 303,
        headers: {
          Location: "/admin",
          "Set-Cookie": "auth_session=; Path=/; HttpOnly; Secure; Max-Age=0",
        },
      });
    }

    if (!isAuthenticated && url.pathname.startsWith("/admin")) {
      const hasError = url.searchParams.get("error") === "1";
      return new Response(renderLoginPage(hasError), {
        headers: { "Content-Type": "text/html; charset=utf-8" },
      });
    }

    // -------------------------------------------------------------
    // 3. ADMIN CRUD: SOURCES (planets)
    // -------------------------------------------------------------
    if (url.pathname === "/admin/source/save" && request.method === "POST") {
      const form = await request.formData();
      const payload = form.get("sources")?.toString() || "";
      try {
        const parsed = JSON.parse(payload);
        if (Array.isArray(parsed)) {
          parsed.sort((a, b) => (a.order || 0) - (b.order || 0));
          await env.FILIGRMA.put("app_sources", JSON.stringify(parsed));
        }
      } catch (_) {
        return Response.redirect(`${url.origin}/admin?source_error=1`, 303);
      }
      return Response.redirect(`${url.origin}/admin`, 303);
    }

    if (url.pathname === "/admin/source/toggle" && request.method === "POST") {
      const form = await request.formData();
      const key = form.get("key")?.toString();
      const sources = await readSources(env);
      const updated = sources.map((s) => (s.key === key ? { ...s, enabled: s.enabled === false } : s));
      await env.FILIGRMA.put("app_sources", JSON.stringify(updated));
      return Response.redirect(`${url.origin}/admin`, 303);
    }

    if (url.pathname === "/admin/source/reset" && request.method === "POST") {
      await env.FILIGRMA.put("app_sources", JSON.stringify(DEFAULT_SOURCES));
      return Response.redirect(`${url.origin}/admin`, 303);
    }

    // -------------------------------------------------------------
    // 4. ADMIN CRUD: BANNERS
    // -------------------------------------------------------------
    if (url.pathname === "/admin/banner/add" && request.method === "POST") {
      const form = await request.formData();
      const imageUrl = form.get("imageUrl")?.toString().trim();
      const targetUrl = form.get("targetUrl")?.toString().trim() || "";
      const order = parseInt(form.get("order")?.toString().trim() || "0", 10);

      if (imageUrl) {
        const list = await readList(env, "app_banners");
        list.push({
          id: crypto.randomUUID(),
          imageUrl,
          targetUrl,
          order: isNaN(order) ? list.length + 1 : order,
          createdAt: new Date().toISOString(),
        });
        list.sort((a, b) => a.order - b.order);
        await env.FILIGRMA.put("app_banners", JSON.stringify(list));
      }
      return Response.redirect(`${url.origin}/admin`, 303);
    }

    if (url.pathname === "/admin/banner/update" && request.method === "POST") {
      const form = await request.formData();
      const id = form.get("id")?.toString();
      const imageUrl = form.get("imageUrl")?.toString().trim();
      const targetUrl = form.get("targetUrl")?.toString().trim() || "";
      const order = parseInt(form.get("order")?.toString().trim() || "0", 10);

      if (id && imageUrl) {
        let list = await readList(env, "app_banners");
        list = list.map((b) =>
          b.id === id ? { ...b, imageUrl, targetUrl, order: isNaN(order) ? b.order : order } : b
        );
        list.sort((a, b) => a.order - b.order);
        await env.FILIGRMA.put("app_banners", JSON.stringify(list));
      }
      return Response.redirect(`${url.origin}/admin`, 303);
    }

    if (url.pathname === "/admin/banner/reorder" && request.method === "POST") {
      const form = await request.formData();
      const id = form.get("id")?.toString();
      const direction = form.get("direction")?.toString(); // "up" or "down"

      const list = await readList(env, "app_banners");
      list.sort((a, b) => a.order - b.order);

      const index = list.findIndex((b) => b.id === id);
      if (index !== -1) {
        if (direction === "up" && index > 0) {
          const temp = list[index].order;
          list[index].order = list[index - 1].order;
          list[index - 1].order = temp;
        } else if (direction === "down" && index < list.length - 1) {
          const temp = list[index].order;
          list[index].order = list[index + 1].order;
          list[index + 1].order = temp;
        }
        list.sort((a, b) => a.order - b.order);
        await env.FILIGRMA.put("app_banners", JSON.stringify(list));
      }
      return Response.redirect(`${url.origin}/admin`, 303);
    }

    if (url.pathname === "/admin/banner/delete" && request.method === "POST") {
      const form = await request.formData();
      const id = form.get("id")?.toString();

      if (id) {
        let list = await readList(env, "app_banners");
        list = list.filter((b) => b.id !== id);
        await env.FILIGRMA.put("app_banners", JSON.stringify(list));
      }
      return Response.redirect(`${url.origin}/admin`, 303);
    }

    // -------------------------------------------------------------
    // 5. ADMIN CRUD: CONFIGURATIONS
    // -------------------------------------------------------------
    if (url.pathname === "/admin/config/set" && request.method === "POST") {
      const form = await request.formData();
      const key = form.get("key")?.toString().trim();
      const value = form.get("value")?.toString().trim();

      if (key && value !== undefined) {
        const list = await readList(env, "app_configs");
        const index = list.findIndex((c) => c.key === key);

        if (index > -1) {
          list[index].value = value;
          list[index].updatedAt = new Date().toISOString();
        } else {
          list.push({
            key,
            value,
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
          });
        }
        await env.FILIGRMA.put("app_configs", JSON.stringify(list));
      }
      return Response.redirect(`${url.origin}/admin`, 303);
    }

    if (url.pathname === "/admin/config/delete" && request.method === "POST") {
      const form = await request.formData();
      const key = form.get("key")?.toString().trim();

      if (key) {
        let list = await readList(env, "app_configs");
        list = list.filter((c) => c.key !== key);
        await env.FILIGRMA.put("app_configs", JSON.stringify(list));
      }
      return Response.redirect(`${url.origin}/admin`, 303);
    }

    // -------------------------------------------------------------
    // 6. ADMIN CRUD: ANNOUNCEMENTS
    // -------------------------------------------------------------
    if (url.pathname === "/admin/create" && request.method === "POST") {
      const form = await request.formData();
      const title = form.get("title")?.toString().trim();
      const text = form.get("text")?.toString().trim();

      if (title && text) {
        const list = await readList(env, "announcements");
        list.unshift({
          id: crypto.randomUUID(),
          title,
          text,
          views: 0,
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        });
        await env.FILIGRMA.put("announcements", JSON.stringify(list));
      }
      return Response.redirect(`${url.origin}/admin`, 303);
    }

    if (url.pathname === "/admin/update" && request.method === "POST") {
      const form = await request.formData();
      const id = form.get("id")?.toString();
      const title = form.get("title")?.toString().trim();
      const text = form.get("text")?.toString().trim();

      if (id && title && text) {
        let list = await readList(env, "announcements");
        list = list.map((item) =>
          item.id === id ? { ...item, title, text, updatedAt: new Date().toISOString() } : item
        );
        await env.FILIGRMA.put("announcements", JSON.stringify(list));
      }
      return Response.redirect(`${url.origin}/admin`, 303);
    }

    if (url.pathname === "/admin/delete" && request.method === "POST") {
      const form = await request.formData();
      const id = form.get("id")?.toString();

      if (id) {
        let list = await readList(env, "announcements");
        list = list.filter((item) => item.id !== id);
        await env.FILIGRMA.put("announcements", JSON.stringify(list));
      }
      return Response.redirect(`${url.origin}/admin`, 303);
    }

    // -------------------------------------------------------------
    // 7. ADMIN DASHBOARD VIEW
    // -------------------------------------------------------------
    if (url.pathname === "/admin") {
      const [announcements, configs, banners, sources] = await Promise.all([
        readList(env, "announcements"),
        readList(env, "app_configs"),
        readList(env, "app_banners"),
        readSources(env),
      ]);

      banners.sort((a, b) => (a.order || 0) - (b.order || 0));
      const sourceError = url.searchParams.get("source_error") === "1";

      return new Response(
        renderDashboardPage(announcements, configs, banners, sources, sourceError),
        { headers: { "Content-Type": "text/html; charset=utf-8" } }
      );
    }

    return new Response("Not Found", { status: 404 });
  },
};

// -------------------------------------------------------------
// 8. HTML TEMPLATES (Login & Modern CRM Dashboard)
// -------------------------------------------------------------
function renderLoginPage(hasError) {
  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Admin Login</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
    body { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: #0f172a; color: #f8fafc; }
    .login-card { background: #1e293b; padding: 36px; border-radius: 16px; width: 100%; max-width: 380px; box-shadow: 0 20px 25px -5px rgba(0,0,0,0.5); border: 1px solid #334155; }
    h1 { font-size: 24px; font-weight: 700; margin-bottom: 8px; text-align: center; color: #38bdf8; }
    p.desc { font-size: 13px; color: #94a3b8; text-align: center; margin-bottom: 24px; }
    .input-group { margin-bottom: 16px; }
    label { display: block; font-size: 13px; color: #cbd5e1; margin-bottom: 6px; font-weight: 500; }
    input { width: 100%; padding: 12px; background: #0f172a; border: 1px solid #334155; border-radius: 8px; color: #fff; font-size: 14px; outline: none; }
    input:focus { border-color: #38bdf8; }
    button { width: 100%; padding: 12px; background: #0284c7; border: none; border-radius: 8px; color: #fff; font-weight: 600; font-size: 15px; cursor: pointer; transition: background 0.2s; margin-top: 8px; }
    button:hover { background: #0369a1; }
    .alert { background: #ef444420; color: #f87171; border: 1px solid #ef444450; padding: 10px; border-radius: 8px; font-size: 13px; margin-bottom: 16px; text-align: center; }
  </style>
</head>
<body>
  <div class="login-card">
    <h1>FILIGRMA CRM</h1>
    <p class="desc">Sign in to manage system configurations</p>
    ${hasError ? `<div class="alert">Invalid username or password</div>` : ""}
    <form action="/admin/login" method="POST">
      <div class="input-group">
        <label>Username</label>
        <input type="text" name="username" placeholder="Username" required autofocus>
      </div>
      <div class="input-group">
        <label>Password</label>
        <input type="password" name="password" placeholder="Password" required>
      </div>
      <button type="submit">Sign In</button>
    </form>
  </div>
</body>
</html>`;
}

function renderDashboardPage(items, configs, banners, sources, sourceError) {
  const totalViews = items.reduce((acc, item) => acc + (item.views || 0), 0);
  const enabledSources = sources.filter((s) => s.enabled !== false).length;

  const sourceRows = sources
    .map(
      (s) => `
    <tr>
      <td>
        <strong style="color: #f8fafc;">${escapeHtml(s.label || s.key)}</strong>
        <div style="font-size: 12px; color: #64748b; margin-top: 2px;">${escapeHtml(s.key)} · ${escapeHtml(s.planet || "")}</div>
      </td>
      <td><div style="font-family: monospace; font-size: 12px; color: #38bdf8; word-break: break-all;">${escapeHtml(s.base_url)}</div></td>
      <td style="font-size: 12px; color: #94a3b8;">
        ${Object.keys(s.headers || {}).length} headers${s.cookies ? " · cookies" : ""}${Object.keys(s.auth || {}).length ? " · auth" : ""}
      </td>
      <td>
        <span class="badge ${s.enabled === false ? "badge-off" : "badge-seen"}">${s.enabled === false ? "disabled" : "enabled"}</span>
      </td>
      <td>
        <form action="/admin/source/toggle" method="POST" style="margin: 0;">
          <input type="hidden" name="key" value="${escapeHtml(s.key)}">
          <button class="btn btn-edit" type="submit">${s.enabled === false ? "Enable" : "Disable"}</button>
        </form>
      </td>
    </tr>`
    )
    .join("");

  const bannerRows = banners
    .map(
      (b, idx) => `
    <tr>
      <td style="width: 50px;">
        <span class="badge badge-seen">#${b.order || idx + 1}</span>
      </td>
      <td style="width: 90px;">
        <a href="${escapeHtml(b.imageUrl)}" target="_blank">
          <img src="${escapeHtml(b.imageUrl)}" style="width: 80px; height: 45px; object-fit: cover; border-radius: 6px; border: 1px solid #334155;"/>
        </a>
      </td>
      <td>
        <div style="font-family: monospace; font-size: 12px; color: #38bdf8; word-break: break-all;">${escapeHtml(b.imageUrl)}</div>
        ${
          b.targetUrl
            ? `<div style="font-size: 11px; color: #94a3b8; margin-top: 4px;">Target: <a href="${escapeHtml(b.targetUrl)}" target="_blank" style="color: #cbd5e1;">${escapeHtml(b.targetUrl)}</a></div>`
            : ""
        }
      </td>
      <td>
        <div style="display: flex; gap: 4px; align-items: center;">
          <form action="/admin/banner/reorder" method="POST" style="margin: 0;">
            <input type="hidden" name="id" value="${b.id}">
            <input type="hidden" name="direction" value="up">
            <button class="btn btn-sm" type="submit" ${idx === 0 ? "disabled style='opacity:0.3;cursor:default;'" : ""}>▲</button>
          </form>
          <form action="/admin/banner/reorder" method="POST" style="margin: 0;">
            <input type="hidden" name="id" value="${b.id}">
            <input type="hidden" name="direction" value="down">
            <button class="btn btn-sm" type="submit" ${idx === banners.length - 1 ? "disabled style='opacity:0.3;cursor:default;'" : ""}>▼</button>
          </form>
          <button class="btn btn-edit" onclick="openBannerModal('${b.id}', \`${escapeHtml(b.imageUrl).replace(/`/g, "\\`")}\`, \`${escapeHtml(b.targetUrl || "").replace(/`/g, "\\`")}\`, ${b.order || 0})">Edit</button>
          <form action="/admin/banner/delete" method="POST" onsubmit="return confirm('Delete this banner?')" style="margin: 0;">
            <input type="hidden" name="id" value="${b.id}">
            <button class="btn btn-delete" type="submit">Delete</button>
          </form>
        </div>
      </td>
    </tr>`
    )
    .join("");

  const announcementRows = items
    .map(
      (item) => `
    <tr>
      <td>
        <strong style="color: #f8fafc; font-size: 15px;">${escapeHtml(item.title)}</strong>
        <div style="font-size: 13px; color: #94a3b8; margin-top: 4px; white-space: pre-wrap;">${escapeHtml(item.text)}</div>
      </td>
      <td>
        <span class="badge badge-seen">👁️ ${item.views || 0}</span>
      </td>
      <td style="font-size: 12px; color: #64748b;">
        ${new Date(item.createdAt).toLocaleDateString()}
      </td>
      <td>
        <div style="display: flex; gap: 8px;">
          <button class="btn btn-edit" onclick="openEditModal('${item.id}', \`${escapeHtml(item.title).replace(/`/g, "\\`")}\`, \`${escapeHtml(item.text).replace(/`/g, "\\`")}\`)">Edit</button>
          <form action="/admin/delete" method="POST" onsubmit="return confirm('Delete this announcement?')" style="margin: 0;">
            <input type="hidden" name="id" value="${item.id}">
            <button class="btn btn-delete" type="submit">Delete</button>
          </form>
        </div>
      </td>
    </tr>`
    )
    .join("");

  const configRows = configs
    .map(
      (c) => `
    <tr>
      <td><code class="code-badge">${escapeHtml(c.key)}</code></td>
      <td><div style="font-family: monospace; font-size: 13px; color: #38bdf8; word-break: break-all;">${escapeHtml(c.value)}</div></td>
      <td style="font-size: 12px; color: #64748b;">
        ${new Date(c.updatedAt || c.createdAt).toLocaleDateString()}
      </td>
      <td>
        <div style="display: flex; gap: 8px;">
          <button class="btn btn-edit" onclick="openConfigModal(\`${escapeHtml(c.key).replace(/`/g, "\\`")}\`, \`${escapeHtml(c.value).replace(/`/g, "\\`")}\`)">Edit</button>
          <form action="/admin/config/delete" method="POST" onsubmit="return confirm('Delete key ${escapeHtml(c.key)}?')" style="margin: 0;">
            <input type="hidden" name="key" value="${escapeHtml(c.key)}">
            <button class="btn btn-delete" type="submit">Delete</button>
          </form>
        </div>
      </td>
    </tr>`
    )
    .join("");

  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>CRM Dashboard - FILIGRMA</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
    body { background: #0f172a; color: #f8fafc; padding: 30px 20px; }
    .container { max-width: 1050px; margin: 0 auto; }
    header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; padding-bottom: 16px; border-bottom: 1px solid #1e293b; }
    header h1 { font-size: 24px; color: #38bdf8; font-weight: 700; }
    .api-links { display: flex; gap: 16px; font-size: 13px; margin-top: 6px; flex-wrap: wrap; }
    .stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 16px; margin-bottom: 24px; }
    .stat-card { background: #1e293b; padding: 20px; border-radius: 12px; border: 1px solid #334155; }
    .stat-card span { font-size: 13px; color: #94a3b8; font-weight: 500; }
    .stat-card strong { display: block; font-size: 28px; color: #f8fafc; margin-top: 4px; }
    .card { background: #1e293b; border-radius: 12px; padding: 24px; border: 1px solid #334155; margin-bottom: 24px; }
    h2 { font-size: 18px; margin-bottom: 16px; color: #f8fafc; display: flex; align-items: center; justify-content: space-between; }
    input[type="text"], input[type="number"], textarea { width: 100%; padding: 12px; background: #0f172a; border: 1px solid #334155; border-radius: 8px; color: #fff; font-size: 14px; margin-bottom: 12px; outline: none; }
    input[type="text"]:focus, input[type="number"]:focus, textarea:focus { border-color: #38bdf8; }
    textarea.code { font-family: monospace; font-size: 12px; line-height: 1.6; }
    .grid-form { display: grid; grid-template-columns: 1fr 2fr auto; gap: 12px; align-items: start; }
    .grid-banner-form { display: grid; grid-template-columns: 2fr 2fr 80px auto; gap: 12px; align-items: start; }
    .btn { padding: 8px 16px; border: none; border-radius: 6px; font-weight: 600; cursor: pointer; font-size: 13px; transition: opacity 0.2s; white-space: nowrap; }
    .btn:hover { opacity: 0.85; }
    .btn-sm { padding: 4px 8px; font-size: 11px; background: #334155; color: #cbd5e1; }
    .btn-primary { background: #0284c7; color: #fff; padding: 12px 20px; font-size: 14px; }
    .btn-edit { background: #334155; color: #38bdf8; }
    .btn-delete { background: #ef444420; color: #f87171; border: 1px solid #ef444440; }
    .btn-logout { background: #334155; color: #cbd5e1; text-decoration: none; display: inline-block; padding: 8px 16px; border-radius: 6px; }
    table { width: 100%; border-collapse: collapse; margin-top: 10px; }
    th, td { text-align: left; padding: 14px; border-bottom: 1px solid #334155; }
    th { font-size: 13px; color: #94a3b8; text-transform: uppercase; letter-spacing: 0.05em; }
    .badge { padding: 4px 10px; border-radius: 999px; font-size: 12px; font-weight: bold; }
    .badge-seen { background: #0284c725; color: #38bdf8; border: 1px solid #0284c750; }
    .badge-off { background: #ef444420; color: #f87171; border: 1px solid #ef444440; }
    .code-badge { background: #0f172a; padding: 4px 8px; border-radius: 6px; border: 1px solid #334155; color: #facc15; font-size: 13px; }
    .alert { background: #ef444420; color: #f87171; border: 1px solid #ef444450; padding: 10px; border-radius: 8px; font-size: 13px; margin-bottom: 16px; }
    .hint { font-size: 12px; color: #64748b; margin-bottom: 10px; }

    .modal { display: none; position: fixed; inset: 0; background: rgba(0,0,0,0.7); align-items: center; justify-content: center; z-index: 50; }
    .modal.active { display: flex; }
    .modal-content { background: #1e293b; padding: 24px; border-radius: 12px; width: 100%; max-width: 500px; border: 1px solid #334155; }
  </style>
</head>
<body>
  <div class="container">
    <header>
      <div>
        <h1>FILIGRMA CRM</h1>
        <div class="api-links">
          <span>Bundle: <a href="/api/data" target="_blank" style="color: #38bdf8;">/api/data</a></span>
          <span>Sources: <a href="/api/sources" target="_blank" style="color: #38bdf8;">/api/sources</a></span>
          <span>Feed: <a href="/chekhabar" target="_blank" style="color: #38bdf8;">/chekhabar</a></span>
          <span>Configs: <a href="/api/config" target="_blank" style="color: #38bdf8;">/api/config</a></span>
          <span>Banners: <a href="/api/banners" target="_blank" style="color: #38bdf8;">/api/banners</a></span>
        </div>
      </div>
      <a href="/admin/logout" class="btn btn-logout">Logout</a>
    </header>

    <div class="stats">
      <div class="stat-card">
        <span>Active Sources</span>
        <strong>${enabledSources}/${sources.length}</strong>
      </div>
      <div class="stat-card">
        <span>Banners</span>
        <strong>${banners.length}</strong>
      </div>
      <div class="stat-card">
        <span>Configurations</span>
        <strong>${configs.length}</strong>
      </div>
      <div class="stat-card">
        <span>Announcements</span>
        <strong>${items.length}</strong>
      </div>
      <div class="stat-card">
        <span>Total Seen</span>
        <strong>${totalViews}</strong>
      </div>
    </div>

    <!-- SOURCES SECTION -->
    <div class="card">
      <h2>Content Sources (Planets)</h2>
      ${sourceError ? `<div class="alert">Invalid JSON. Nothing was saved.</div>` : ""}
      <div class="hint">Base URLs, headers, cookies and tokens the apps use. Disabling a source hides it in every client.</div>

      <div style="overflow-x: auto;">
        <table>
          <thead>
            <tr>
              <th>Planet</th>
              <th>Base URL</th>
              <th>Credentials</th>
              <th>State</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>${sourceRows}</tbody>
        </table>
      </div>

      <form action="/admin/source/save" method="POST" style="margin-top: 20px;">
        <label style="font-size: 12px; color: #94a3b8;">Raw sources JSON</label>
        <textarea name="sources" rows="16" class="code" required>${escapeHtml(JSON.stringify(sources, null, 2))}</textarea>
        <div style="display: flex; gap: 8px;">
          <button type="submit" class="btn btn-primary">Save Sources</button>
          <button type="submit" class="btn btn-delete" formaction="/admin/source/reset" onclick="return confirm('Reset every source to the built-in defaults?')">Reset to defaults</button>
        </div>
      </form>
    </div>

    <!-- BANNER MANAGER SECTION -->
    <div class="card">
      <h2>App Banners (Images & Reordering)</h2>
      <form action="/admin/banner/add" method="POST" class="grid-banner-form">
        <input type="text" name="imageUrl" placeholder="Image URL (https://...)" required>
        <input type="text" name="targetUrl" placeholder="Target Link (optional)">
        <input type="number" name="order" placeholder="Sort" value="${banners.length + 1}">
        <button type="submit" class="btn btn-primary" style="height: 44px;">+ Add</button>
      </form>

      <div style="overflow-x: auto; margin-top: 10px;">
        <table>
          <thead>
            <tr>
              <th>Order</th>
              <th>Preview</th>
              <th>URLs</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            ${
              banners.length === 0
                ? `<tr><td colspan="4" style="text-align: center; color: #64748b;">No banners created yet.</td></tr>`
                : bannerRows
            }
          </tbody>
        </table>
      </div>
    </div>

    <!-- CONFIGURATION SECTION -->
    <div class="card">
      <h2>App Configurations (Key / Value)</h2>
      <form action="/admin/config/set" method="POST" class="grid-form">
        <input type="text" name="key" placeholder="Key (e.g. app_version)" required>
        <input type="text" name="value" placeholder="Value (e.g. 2.4.0, true, https://...)" required>
        <button type="submit" class="btn btn-primary" style="height: 44px;">Save Config</button>
      </form>

      <div style="overflow-x: auto; margin-top: 10px;">
        <table>
          <thead>
            <tr>
              <th style="width: 25%;">Key</th>
              <th style="width: 45%;">Value</th>
              <th style="width: 15%;">Updated</th>
              <th style="width: 15%;">Actions</th>
            </tr>
          </thead>
          <tbody>
            ${
              configs.length === 0
                ? `<tr><td colspan="4" style="text-align: center; color: #64748b;">No configs configured yet.</td></tr>`
                : configRows
            }
          </tbody>
        </table>
      </div>
    </div>

    <!-- ANNOUNCEMENT SECTION -->
    <div class="card">
      <h2>Add New Announcement</h2>
      <form action="/admin/create" method="POST">
        <input type="text" name="title" placeholder="Title..." required>
        <textarea name="text" rows="3" placeholder="Description / Message content..." required></textarea>
        <button type="submit" class="btn btn-primary">+ Create Announcement</button>
      </form>
    </div>

    <div class="card">
      <h2>Announcements List</h2>
      <div style="overflow-x: auto;">
        <table>
          <thead>
            <tr>
              <th>Content</th>
              <th>Seen</th>
              <th>Date</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            ${
              items.length === 0
                ? `<tr><td colspan="4" style="text-align: center; color: #64748b;">No announcements created yet.</td></tr>`
                : announcementRows
            }
          </tbody>
        </table>
      </div>
    </div>
  </div>

  <!-- Banner Edit Modal -->
  <div class="modal" id="bannerModal">
    <div class="modal-content">
      <h2 style="margin-bottom: 12px;">Edit Banner</h2>
      <form action="/admin/banner/update" method="POST">
        <input type="hidden" name="id" id="edit-banner-id">
        <label style="font-size: 12px; color: #94a3b8;">Image URL</label>
        <input type="text" name="imageUrl" id="edit-banner-image" required>
        <label style="font-size: 12px; color: #94a3b8;">Target Link URL (Optional)</label>
        <input type="text" name="targetUrl" id="edit-banner-target">
        <label style="font-size: 12px; color: #94a3b8;">Order Index</label>
        <input type="number" name="order" id="edit-banner-order" required>
        <div style="display: flex; gap: 8px; justify-content: flex-end; margin-top: 8px;">
          <button type="button" class="btn" style="background:#475569; color:#fff;" onclick="closeModal('bannerModal')">Cancel</button>
          <button type="submit" class="btn btn-primary">Save Changes</button>
        </div>
      </form>
    </div>
  </div>

  <!-- Announcement Edit Modal -->
  <div class="modal" id="editModal">
    <div class="modal-content">
      <h2 style="margin-bottom: 12px;">Edit Announcement</h2>
      <form action="/admin/update" method="POST">
        <input type="hidden" name="id" id="edit-id">
        <label style="font-size: 12px; color: #94a3b8;">Title</label>
        <input type="text" name="title" id="edit-title" required>
        <label style="font-size: 12px; color: #94a3b8;">Text</label>
        <textarea name="text" id="edit-text" rows="4" required></textarea>
        <div style="display: flex; gap: 8px; justify-content: flex-end; margin-top: 8px;">
          <button type="button" class="btn" style="background:#475569; color:#fff;" onclick="closeModal('editModal')">Cancel</button>
          <button type="submit" class="btn btn-primary">Save Changes</button>
        </div>
      </form>
    </div>
  </div>

  <!-- Config Edit Modal -->
  <div class="modal" id="configModal">
    <div class="modal-content">
      <h2 style="margin-bottom: 12px;">Edit Configuration</h2>
      <form action="/admin/config/set" method="POST">
        <label style="font-size: 12px; color: #94a3b8;">Key (Read-Only)</label>
        <input type="text" name="key" id="edit-config-key" readonly style="opacity: 0.6; cursor: not-allowed;">
        <label style="font-size: 12px; color: #94a3b8;">Value</label>
        <textarea name="value" id="edit-config-value" rows="3" required></textarea>
        <div style="display: flex; gap: 8px; justify-content: flex-end; margin-top: 8px;">
          <button type="button" class="btn" style="background:#475569; color:#fff;" onclick="closeModal('configModal')">Cancel</button>
          <button type="submit" class="btn btn-primary">Update Value</button>
        </div>
      </form>
    </div>
  </div>

  <script>
    function openBannerModal(id, imageUrl, targetUrl, order) {
      document.getElementById('edit-banner-id').value = id;
      document.getElementById('edit-banner-image').value = imageUrl;
      document.getElementById('edit-banner-target').value = targetUrl;
      document.getElementById('edit-banner-order').value = order;
      document.getElementById('bannerModal').classList.add('active');
    }
    function openEditModal(id, title, text) {
      document.getElementById('edit-id').value = id;
      document.getElementById('edit-title').value = title;
      document.getElementById('edit-text').value = text;
      document.getElementById('editModal').classList.add('active');
    }
    function openConfigModal(key, value) {
      document.getElementById('edit-config-key').value = key;
      document.getElementById('edit-config-value').value = value;
      document.getElementById('configModal').classList.add('active');
    }
    function closeModal(modalId) {
      document.getElementById(modalId).classList.remove('active');
    }
  </script>
</body>
</html>`;
}
