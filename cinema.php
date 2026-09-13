<?php
// cinema.php - Frontend Cinema App (Dark Edition + Playlists)

function buildSkeletonCards($count = 6) {
  $html = '';
  for ($i = 0; $i < $count; $i++) {
    $html .= '
    <div class="skel-card">
      <div class="skel skel-poster"></div>
      <div class="skel-card-body">
        <div class="skel skel-text" style="width:85%"></div>
        <div class="skel skel-text sm"></div>
      </div>
    </div>';
  }
  return $html;
}

function buildDetailSkeleton() {
  return '
  <button class="btn-back detail-back" style="pointer-events:none; opacity:0.5"><i class="fa-solid fa-arrow-right"></i> بازگشت</button>
  <div class="detail-hero">
    <div class="skel" style="width:100%;height:100%"></div>
  </div>
  <div class="detail-layout">
    <div class="detail-poster" style="background:var(--card);border-radius:20px;overflow:hidden;">
      <div class="skel" style="width:140px;height:210px;border-radius:0"></div>
    </div>
    <div class="detail-info" style="padding-top:80px">
      <div class="skel skel-text" style="width:60%;height:30px;margin-bottom:14px"></div>
      <div class="skel skel-text" style="width:35%;height:12px;margin-bottom:18px"></div>
      <div style="display:flex;gap:8px;margin-bottom:20px">
        <div class="skel" style="width:60px;height:24px;border-radius:12px"></div>
        <div class="skel" style="width:70px;height:24px;border-radius:12px"></div>
      </div>
      <div class="skel skel-text" style="width:100%;margin-bottom:8px"></div>
      <div class="skel skel-text" style="width:92%;margin-bottom:8px"></div>
      <div class="skel skel-text" style="width:76%;margin-bottom:28px"></div>
    </div>
  </div>';
}

function buildEpisodeSkeleton($count = 5) {
  $html = '';
  for ($i = 0; $i < $count; $i++) {
    $html .= '
    <div class="skel-ep-row">
      <div class="skel" style="width:28px;height:24px;border-radius:8px"></div>
      <div style="flex:1">
        <div class="skel skel-text" style="width:60%;margin-bottom:6px"></div>
        <div class="skel skel-text sm"></div>
      </div>
    </div>';
  }
  return $html;
}
?>
<!DOCTYPE html>
<html lang="fa" dir="rtl">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<title>فیلیگرام | movies — فیلم و سریال</title>
<link rel="stylesheet" href="https://lib.arvancloud.ir/font-awesome/6.3.0/css/all.css">
<style>
@font-face {
  font-family: 'YekanBakh';
  src: url('https://test.deborahmorgan.ir/YekanBakh-Regular-CtIiwT9t.woff') format('woff');
  font-weight: normal;
  font-style: normal;
  font-display: swap;
}

:root {
  --black:      #000000;
  --deep:       #050505;
  --surface:    #0a0a0a;
  --card:       #111111;
  --border:     #1a1a1a;
  --gold:       #d4af37;
  --gold-dim:   #7a6128;
  --gold-glow:  rgba(212,175,55,0.15);
  --text:       #e0e0e0;
  --muted:      #888888;
  --sub:        #444444;
  --blue:       #007bff;
  --red:        #cc0000;
  --skel:       #1a1a1a;
  --skel-hi:    #2a2a2a;
}

*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
html { scroll-behavior: smooth; }

body, button, input, select, textarea, .btn, .nav-tab {
  font-family: 'YekanBakh', sans-serif !important;
}

body {
  background: #000000;
  color: var(--text);
  font-weight: 400;
  font-size: 13px;
  line-height: 1.6;
  direction: rtl;
  min-height: 100vh;
  overflow-x: hidden;
  -webkit-font-smoothing: antialiased;
}

body, .view, .section, .sheet-backdrop, .sheet {
  background-color: #000000;
}

::-webkit-scrollbar { width: 0px; height: 0px; background: transparent; display: none; }
* { scrollbar-width: none; -ms-overflow-style: none; }

button, .card, .ep-row, .sheet-item {
  touch-action: manipulation;
  user-select: none;
  -webkit-user-select: none;
  -webkit-tap-highlight-color: transparent;
}

/* --- SUPER SMOOTH SKELETON ANIMATIONS --- */
@keyframes shimmer {
  0% { background-position: -200% 0; }
  100% { background-position: 200% 0; }
}
.skel {
  background: linear-gradient(90deg, var(--skel) 25%, var(--skel-hi) 50%, var(--skel) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite linear;
  border-radius: 8px;
}
.skel-card { width: 125px; flex-shrink: 0; border-radius: 14px; overflow: hidden; background: var(--card); border: 1px solid var(--border); }
.cards-grid .skel-card { width: auto; }
.skel-poster { width: 100%; aspect-ratio: 2/3; border-radius: 0; }
.skel-card-body { padding: 10px; }
.skel-text { height: 12px; margin-bottom: 6px; border-radius: 6px; }
.skel-text.sm { width: 60%; height: 10px; margin-bottom: 0; }
.skel-ep-row { display: flex; align-items: center; gap: 14px; padding: 12px 16px; background: var(--surface); border-radius: 16px; margin-bottom: 10px; }

.header {
  position: fixed;
  top: 12px;
  left: 50%;
  transform: translateX(-50%);
  width: calc(100% - 24px);
  max-width: 1200px;
  z-index: 2000;
  background: rgba(0, 0, 0, 0.75);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid var(--border);
  border-radius: 20px;
  padding: 0 20px;
  box-shadow: 0 10px 40px rgba(0,0,0,0.8);
  transition: all 0.3s ease;
}

.header-inner { display: flex; align-items: center; justify-content: space-between; height: 60px; position: relative; }
.header-right, .header-left { display: flex; align-items: center; gap: 12px; flex: 1; }
.header-right { justify-content: flex-start; }
.header-left { justify-content: flex-end; }
.logo-center { position: absolute; left: 50%; transform: translateX(-50%); height: 35px; cursor: pointer; transition: transform 0.2s; }
.logo-center:active { transform: translateX(-50%) scale(0.95); }
.logo-center img { height: 100%; object-fit: contain; border-radius: 8px; }
.search-trigger { background: var(--surface); border: 1px solid var(--border); color: var(--text); font-size: 14px; cursor: pointer; display: flex; align-items: center; justify-content: center; width: 36px; height: 36px; border-radius: 50%; transition: all 0.2s; }
.search-trigger:hover { background: var(--border); color: var(--gold); }
.search-trigger:active { transform: scale(0.9); }
.nav-tabs { display: flex; gap: 4px; }
.nav-tab { font-weight: 600; font-size: 12px; padding: 6px 14px; border-radius: 20px; border: 1px solid transparent; cursor: pointer; background: none; color: var(--muted); transition: all 0.2s ease; display: flex; align-items: center; gap: 6px; }
.nav-tab:hover { color: var(--text); background: var(--surface); }
.nav-tab:active { transform: scale(0.95); }
.nav-tab.active { color: var(--gold); border-color: var(--gold-dim); background: var(--gold-glow); }

.bottom-nav { display: none; }

.fab-genre {
  position: fixed;
  bottom: 80px;
  right: 20px;
  background: var(--gold);
  color: var(--black);
  width: 55px;
  height: 55px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  box-shadow: 0 8px 25px rgba(212,175,55,0.4);
  z-index: 1900;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
  transform: scale(0);
  opacity: 0;
  pointer-events: none;
}
.fab-genre.visible { transform: scale(1); opacity: 1; pointer-events: auto; }
.fab-genre:hover { transform: scale(1.1); box-shadow: 0 10px 30px rgba(212,175,55,0.6); }
.fab-genre:active { transform: scale(0.9); }

.main { max-width: 1600px; margin: 0 auto; padding: 0 clamp(12px, 3vw, 40px) 100px; background: #000000; }

.view { display: none; opacity: 0; background: #000000; }
.view.active { display: block; animation: smoothFadeIn 0.3s cubic-bezier(0.2, 0.8, 0.2, 1) forwards; }
@keyframes smoothFadeIn { 0% { opacity: 0; transform: translateY(10px); } 100% { opacity: 1; transform: translateY(0); } }

.padded-view { padding-top: 100px; }

.hero, .detail-hero { 
  margin: 0 calc(-1 * clamp(12px, 3vw, 40px)) 0;
  position: relative; overflow: hidden; background: #000000; border-radius: 0 0 30px 30px;
}
.hero { height: clamp(400px, 60vw, 680px); box-shadow: 0 10px 50px rgba(0,0,0,0.8); }
.detail-hero { height: clamp(320px, 50vw, 500px); }

.hero-slides { display: flex; height: 100%; transition: transform 0.6s cubic-bezier(0.25, 1, 0.5, 1); }
.hero-slide { min-width: 100%; position: relative; overflow: hidden; cursor: pointer; }
.hero-slide-bg { 
  position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; object-position: top center; 
  filter: brightness(0.4) blur(1px); transform: scale(1.08); transition: transform 8s ease, filter 0.6s ease;
}
.hero-slide.is-active .hero-slide-bg { transform: scale(1.02); filter: brightness(0.65) blur(0); }
.hero-gradient { 
  position: absolute; inset: 0; 
  background: linear-gradient(270deg, rgba(0,0,0,0.95) 0%, rgba(0,0,0,0.3) 50%, transparent 100%), 
              linear-gradient(0deg, rgba(0,0,0,0.95) 0%, rgba(0,0,0,0.1) 60%, transparent 100%); 
}
.hero-content { position: absolute; bottom: 50px; right: clamp(20px, 4vw, 60px); max-width: 450px; z-index: 2; }
.hero-badge { font-weight: 700; font-size: 10px; color: var(--gold); border: 1px solid var(--gold-dim); padding: 4px 12px; border-radius: 20px; display: inline-flex; align-items: center; gap: 6px; margin-bottom: 12px; background: rgba(0,0,0,0.5); backdrop-filter: blur(8px); }
.hero-title { font-weight: 800; font-size: clamp(24px, 5vw, 48px); line-height: 1.2; color: var(--text); margin-bottom: 10px; text-shadow: 0 4px 20px rgba(0,0,0,0.9); }
.hero-cta { background: var(--gold); color: #000; border: none; padding: 12px 28px; border-radius: 30px; font-weight: 800; font-size: 14px; cursor: pointer; display: inline-flex; align-items: center; gap: 8px; margin-top: 8px; transition: all 0.2s ease; }
.hero-cta:hover { transform: scale(1.05); background: #fff; box-shadow: 0 0 20px rgba(255,255,255,0.4); }
.hero-cta:active { transform: scale(0.95); }

.section { margin-top: 36px; background: transparent; }
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.section-title { font-weight: 800; font-size: 16px; color: var(--text); display: flex; align-items: center; gap: 8px; }
.section-title i { color: var(--gold); font-size: 14px; }

.cards-scroll { display: flex; gap: 12px; overflow-x: auto; padding-bottom: 14px; scrollbar-width: none; }
.cards-scroll::-webkit-scrollbar { display: none; }
.cards-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(110px, 1fr)); gap: 14px; }

.card { background: var(--card); border-radius: 14px; overflow: hidden; cursor: pointer; transition: all 0.3s ease; border: 1px solid transparent; flex-shrink: 0; width: 125px; position: relative; will-change: transform; }
.cards-grid .card { width: auto; }
.card:hover { transform: translateY(-6px); border-color: var(--border); box-shadow: 0 12px 24px rgba(0,0,0,0.7); }
.card:active { transform: scale(0.94); }
.card-poster { width: 100%; aspect-ratio: 2/3; object-fit: cover; display: block; background: var(--surface); }
.card-badges { position: absolute; top: 6px; right: 6px; display: flex; flex-direction: column; gap: 4px; align-items: flex-end; }
.badge { font-weight: 700; font-size: 8px; padding: 3px 8px; border-radius: 8px; display: inline-flex; align-items: center; gap: 3px; box-shadow: 0 2px 10px rgba(0,0,0,0.5); backdrop-filter: blur(4px); }
.badge-sub { background: rgba(212,175,55,0.9); color: var(--black); }
.badge-dub { background: rgba(204,0,0,0.9); color: #fff; }
.badge-series { background: rgba(0,179,0,0.9); color: #fff; }

.card-body { padding: 10px; }
.card-title { font-weight: 700; font-size: 11px; color: var(--text); line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; margin-bottom: 3px; }
.card-year { font-size: 10px; color: var(--muted); font-weight: 600;}

/* DETAIL & ACTOR UI */
#view-detail { position: relative; } 
.detail-hero-bg { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; filter: brightness(0.35) blur(6px); transform: scale(1.1); }
.detail-hero-grad { position: absolute; inset: 0; background: linear-gradient(0deg, #000000 0%, rgba(0,0,0,0.3) 60%, transparent 100%); }
.detail-layout { display: flex; gap: 24px; position: relative; margin-top: -120px; align-items: flex-start; flex-wrap: wrap; z-index: 10; }
.detail-poster { width: 150px; border-radius: 20px; overflow: hidden; box-shadow: 0 20px 50px rgba(0,0,0,0.9); border: 1px solid var(--border); flex-shrink: 0; background: #000; transition: transform 0.3s; }
.detail-poster:hover { transform: translateY(-5px); }
.detail-poster img { width: 100%; display: block; }
.detail-info { flex: 1; min-width: 260px; padding-top: 50px; }
.detail-title { font-weight: 800; font-size: clamp(24px, 4vw, 40px); color: var(--text); line-height: 1.2; margin-bottom: 12px; }
.detail-tags { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 20px; }
.tag { font-weight: 700; font-size: 11px; padding: 4px 12px; border: 1px solid var(--border); border-radius: 20px; color: var(--muted); display: flex; align-items: center; gap: 6px; background: rgba(255,255,255,0.03); }
.tag-gold { border-color: var(--gold-dim); color: var(--gold); background: var(--gold-glow); }
.detail-plot { font-size: 13px; color: rgba(240,240,240,0.85); line-height: 1.9; margin-bottom: 20px; text-align: justify; }

.btn { font-weight: 700; font-size: 13px; padding: 12px 24px; border-radius: 24px; cursor: pointer; transition: all 0.2s ease; display: inline-flex; align-items: center; justify-content: center; gap: 8px; border: none; }
.btn:active { transform: scale(0.95); }
.btn-primary { background: var(--gold); color: var(--black); box-shadow: 0 4px 15px rgba(212,175,55,0.2); }
.btn-primary:hover { background: #fff; box-shadow: 0 4px 20px rgba(255,255,255,0.4); }
.btn-sec { background: rgba(255,255,255,0.05); border: 1px solid var(--border); color: var(--text); backdrop-filter: blur(8px); }
.btn-sec:hover { background: rgba(255,255,255,0.1); border-color: var(--muted); }

.detail-back { position: absolute; top: 85px; right: clamp(12px, 3vw, 40px); z-index: 50; background: rgba(10, 10, 10, 0.7); backdrop-filter: blur(12px); border: 1px solid rgba(255,255,255,0.1); color: var(--text); padding: 8px 16px; border-radius: 20px; cursor: pointer; font-size: 12px; font-weight: 600; display: inline-flex; align-items: center; gap: 6px; transition: all 0.2s ease; box-shadow: 0 4px 15px rgba(0,0,0,0.5); }
.detail-back:hover { background: rgba(255,255,255,0.15); color: #fff; transform: translateY(-2px); }
.detail-back:active { transform: scale(0.92); }
.btn-back-std { background: var(--surface); color: var(--text); border: 1px solid var(--border); padding: 8px 16px; border-radius: 20px; cursor: pointer; font-size: 12px; font-weight: 600; display: inline-flex; align-items: center; gap: 6px; margin-bottom: 20px; transition: all 0.2s; }
.btn-back-std:active { transform: scale(0.92); }

.actor-header { display: flex; align-items: center; gap: 20px; margin-bottom: 24px; background: var(--surface); padding: 20px; border-radius: 24px; border: 1px solid var(--border); box-shadow: 0 10px 30px rgba(0,0,0,0.5); }
.actor-avatar-wrap { width: 90px; height: 90px; border-radius: 50%; overflow: hidden; border: 2px solid var(--gold); flex-shrink: 0; background: var(--card); }
.actor-avatar-wrap img { width: 100%; height: 100%; object-fit: cover; }
.actor-info { flex: 1; }
.actor-name { font-weight: 800; font-size: 24px; color: var(--text); margin-bottom: 6px; line-height: 1.2; }
.actor-role { font-size: 13px; color: var(--gold); font-weight: 700; background: var(--gold-glow); display: inline-block; padding: 4px 12px; border-radius: 12px; border: 1px solid var(--gold-dim); }

.cast-list, .shot-list { display: flex; gap: 14px; overflow-x: auto; padding-bottom: 12px; scrollbar-width: none; }
.cast-list::-webkit-scrollbar, .shot-list::-webkit-scrollbar { display: none; }
.cast-card { flex-shrink: 0; width: 75px; text-align: center; cursor: pointer; }
.cast-avatar { width: 64px; height: 64px; border-radius: 50%; background: var(--surface); margin: 0 auto 8px; overflow: hidden; border: 1px solid var(--border); transition: transform 0.2s; }
.cast-card:hover .cast-avatar { transform: scale(1.05); border-color: var(--gold-dim); }
.cast-avatar img { width: 100%; height: 100%; object-fit: cover; }
.cast-name { font-weight: 700; font-size: 10px; color: var(--text); }
.cast-role { font-size: 9px; color: var(--muted); }
.shot-card { flex-shrink: 0; width: 220px; height: 124px; border-radius: 14px; overflow: hidden; border: 1px solid var(--border); background: #000; transition: transform 0.2s; cursor: pointer; }
.shot-card:hover { transform: scale(1.02); border-color: var(--gold-dim); }
.shot-card img { width: 100%; height: 100%; object-fit: cover; }

.meta-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(130px, 1fr)); gap: 10px; margin-bottom: 24px; }
.meta-item { background: var(--surface); padding: 12px 14px; border-radius: 14px; border: 1px solid var(--border); transition: transform 0.2s; }
.meta-item:hover { transform: translateY(-3px); border-color: rgba(255,255,255,0.1); }
.meta-label { font-size: 10px; color: var(--muted); margin-bottom: 4px; font-weight: 600;}
.meta-val { font-weight: 800; font-size: 12px; color: var(--text); }

.stat-box { background: linear-gradient(135deg, var(--card), var(--surface)); border: 1px solid var(--border); border-radius: 16px; padding: 16px; margin-bottom: 24px; display: flex; align-items: center; gap: 12px; justify-content: space-around; flex-wrap: wrap; box-shadow: 0 10px 30px rgba(0,0,0,0.3); }
.stat-item { text-align: center; }
.stat-val { font-size: 16px; font-weight: 800; color: var(--gold); }
.stat-lbl { font-size: 11px; color: var(--muted); font-weight: 600; mt: 2px;}

.seasons-tabs { display: flex; gap: 8px; margin-bottom: 16px; flex-wrap: wrap; }
.season-tab { font-weight: 700; font-size: 12px; padding: 8px 18px; border-radius: 20px; border: 1px solid var(--border); background: var(--surface); color: var(--muted); cursor: pointer; transition: all 0.2s; }
.season-tab:active { transform: scale(0.95); }
.season-tab.active { background: var(--gold-glow); border-color: var(--gold-dim); color: var(--gold); }
.episode-row { display: flex; align-items: center; gap: 14px; padding: 12px 16px; background: var(--surface); border-radius: 16px; border: 1px solid transparent; cursor: pointer; transition: all 0.2s; margin-bottom: 10px; }
.episode-row:hover { border-color: var(--gold-dim); background: var(--card); transform: translateX(-4px); }
.episode-row:active { transform: scale(0.98); }
.ep-num { font-weight: 800; font-size: 18px; color: var(--sub); min-width: 26px; text-align: center; }
.ep-title { font-weight: 700; font-size: 12px; color: var(--text); }
.ep-play { margin-right: auto; color: var(--gold); font-size: 18px; }

/* --- CUSTOM PLAYER UI (UPDATED DESIGN) --- */
.custom-player-wrapper { position: relative; background: #000; border-radius: 24px; overflow: hidden; border: 1px solid var(--border); margin-bottom: 16px; box-shadow: 0 10px 40px rgba(0,0,0,0.8); display: flex; align-items: center; justify-content: center; aspect-ratio: 16/9; max-height: 70vh; }
.custom-player-wrapper video { width: 100%; height: 100%; display: block; background: #000; outline: none; object-fit: contain; }
.player-overlay { position: absolute; inset: 0; display: flex; flex-direction: column; justify-content: flex-end; background: linear-gradient(0deg, rgba(0,0,0,0.85) 0%, rgba(0,0,0,0.3) 25%, transparent 40%); transition: opacity 0.3s ease; z-index: 10; }
.player-overlay.hidden { opacity: 0; pointer-events: none; }
.player-center-controls { position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); font-size: 50px; color: rgba(255,255,255,0.9); cursor: pointer; transition: all 0.2s; background: rgba(0,0,0,0.4); width: 80px; height: 80px; border-radius: 50%; display: flex; align-items: center; justify-content: center; backdrop-filter: blur(4px); z-index: 11;}
.player-center-controls:hover { color: var(--gold); transform: translate(-50%, -50%) scale(1.1); background: rgba(0,0,0,0.6); }
.player-bottom-controls { padding: 0 20px 16px; width: 100%; direction: ltr; z-index: 12; display: flex; flex-direction: column; gap: 8px;}
.progress-container { width: 100%; height: 5px; background: rgba(255,255,255,0.25); border-radius: 4px; cursor: pointer; position: relative; transition: height 0.2s; display: flex; align-items: center; }
.progress-container:hover { height: 8px; }
.progress-bar { height: 100%; background: var(--gold); border-radius: 4px; width: 0%; pointer-events: none; position: relative; display: flex; align-items: center; }
.progress-thumb { position: absolute; right: 0; transform: translateX(50%); width: 14px; height: 14px; background: #fff; border-radius: 50%; pointer-events: none; box-shadow: 0 0 8px rgba(0,0,0,0.6); opacity: 0; transition: opacity 0.2s, transform 0.1s; }
.progress-container:hover .progress-thumb { opacity: 1; transform: translateX(50%) scale(1.2); }
.controls-row { display: flex; justify-content: space-between; align-items: center; margin-top: 4px; }
.controls-left, .controls-right { display: flex; align-items: center; gap: 20px; }
.ctrl-btn { background: none; border: none; color: #fff; font-size: 20px; cursor: pointer; transition: 0.2s; padding: 0; display: flex; align-items: center; justify-content: center; }
.ctrl-btn:hover { color: var(--gold); transform: scale(1.1); }
.time-display { font-size: 13px; font-weight: 600; font-family: monospace; color: #eee; user-select: none; }

.subtitles-menu { position: absolute; bottom: 80px; right: 20px; background: rgba(15,15,15,0.95); backdrop-filter: blur(16px); border: 1px solid var(--border); border-radius: 16px; padding: 12px; z-index: 20; min-width: 140px; display: none; flex-direction: column; gap: 8px; box-shadow: 0 10px 30px rgba(0,0,0,0.8); direction: rtl; }
.sub-menu-title { font-size: 12px; color: var(--muted); border-bottom: 1px solid var(--border); padding-bottom: 8px; margin-bottom: 4px; text-align: center; font-weight: 700; }
.sub-track-item { background: transparent; border: 1px solid transparent; color: #ccc; font-size: 12px; padding: 8px 12px; text-align: right; border-radius: 10px; cursor: pointer; transition: all 0.2s; font-family: inherit; font-weight: 600; }
.sub-track-item:hover { background: rgba(255,255,255,0.05); color: #fff; }
.sub-track-item.active { color: var(--gold); border-color: var(--gold-dim); background: var(--gold-glow); }

/* Fix fullscreen specific styling */
.custom-player-wrapper:fullscreen { max-height: none; border-radius: 0; border: none; }
.custom-player-wrapper:-webkit-full-screen { max-height: none; border-radius: 0; border: none; }

::cue { background: rgba(0, 0, 0, 0.8); color: #fff; font-family: 'YekanBakh', sans-serif; font-size: 18px; text-shadow: 1px 1px 2px #000; padding: 4px 8px; border-radius: 4px; }

.player-controls-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; background: var(--surface); border: 1px solid var(--border); border-radius: 20px; padding: 14px 20px; margin-bottom: 24px; flex-wrap: wrap; }
.player-title-block { flex: 1; min-width: 150px; }
.player-title { font-weight: 800; font-size: 15px; color: var(--text); }
.player-subtitle { font-size: 11px; color: var(--muted); margin-top: 4px; font-weight: 600;}
.player-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.p-btn { font-weight: 700; font-size: 12px; background: var(--card); border: 1px solid var(--border); color: var(--text); padding: 10px 16px; border-radius: 20px; cursor: pointer; display: flex; align-items: center; gap: 8px; transition: all 0.2s; }
.p-btn:hover { background: var(--border); }
.p-btn:active { transform: scale(0.92); }
.p-btn i { color: var(--gold); }

/* --- LIBRARY & PLAYLISTS UI --- */
.lib-tabs-wrapper { display: flex; gap: 8px; margin-bottom: 24px; background: var(--surface); padding: 6px; border-radius: 24px; border: 1px solid var(--border); width: max-content; max-width: 100%; overflow-x: auto; }
.lib-tab { font-weight: 700; font-size: 12px; padding: 10px 24px; border-radius: 18px; border: none; background: transparent; color: var(--muted); cursor: pointer; transition: all 0.2s; display: flex; align-items: center; gap: 8px; }
.lib-tab:active { transform: scale(0.95); }
.lib-tab.active { background: var(--gold); color: var(--black); box-shadow: 0 4px 15px rgba(212,175,55,0.2); }

.playlist-card { background: var(--surface); border: 1px solid var(--border); border-radius: 16px; padding: 20px; display: flex; align-items: center; justify-content: space-between; cursor: pointer; transition: all 0.2s; margin-bottom: 12px; }
.playlist-card:hover { border-color: var(--gold-dim); background: var(--card); transform: translateX(-4px); }
.playlist-info h3 { font-size: 16px; font-weight: 800; color: var(--text); margin-bottom: 4px; }
.playlist-info p { font-size: 11px; color: var(--muted); font-weight: 600; }
.playlist-actions { display: flex; gap: 8px; }
.pl-action-btn { background: rgba(255,255,255,0.05); color: var(--text); border: none; width: 36px; height: 36px; border-radius: 50%; display: flex; align-items: center; justify-content: center; cursor: pointer; transition: 0.2s; }
.pl-action-btn:hover { background: var(--border); color: var(--gold); }

/* BOTTOM SHEETS */
.sheet-backdrop { position: fixed; inset: 0; background: rgba(0,0,0,0.85); backdrop-filter: blur(10px); z-index: 4000; opacity: 0; pointer-events: none; transition: opacity 0.3s ease; display: flex; flex-direction: column; justify-content: flex-end; }
.sheet-backdrop.open { opacity: 1; pointer-events: auto; }
.sheet { background: var(--deep); border: 1px solid var(--border); border-bottom: none; border-radius: 30px 30px 0 0; padding: 24px; transform: translateY(120%); transition: transform 0.4s cubic-bezier(0.2, 0.8, 0.2, 1); max-height: 85vh; overflow-y: auto; display: flex; flex-direction: column; box-shadow: 0 -10px 40px rgba(0,0,0,0.8); }
.sheet-backdrop.open .sheet { transform: translateY(0); }
.sheet-drag { width: 40px; height: 5px; background: var(--sub); border-radius: 3px; margin: 0 auto 20px; }
.sheet-title { font-weight: 800; font-size: 18px; color: var(--text); margin-bottom: 6px; text-align: center; }
.sheet-subtitle { font-size: 12px; color: var(--muted); text-align: center; margin-bottom: 24px; }

.genre-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(100px, 1fr)); gap: 12px; margin-top: 10px; }
.genre-item { background: var(--card); border: 1px solid var(--border); border-radius: 16px; padding: 14px 8px; text-align: center; cursor: pointer; transition: all 0.2s; font-size: 12px; font-weight: 700; color: var(--text); }
.genre-item:hover { border-color: var(--gold-dim); background: var(--gold-glow); color: var(--gold); transform: translateY(-4px); }
.genre-item:active { transform: scale(0.95); }
.genre-item i { display: block; font-size: 24px; margin-bottom: 8px; color: var(--gold); }

.search-box { position: relative; margin-bottom: 16px; }
.search-box input { width: 100%; background: var(--card); border: 1px solid var(--border); border-radius: 24px; color: var(--text); font-size: 15px; font-weight: 600; padding: 16px 50px 16px 16px; outline: none; transition: 0.2s; }
.search-box input:focus { border-color: var(--gold); box-shadow: 0 0 15px var(--gold-glow); }
.search-box i { position: absolute; right: 20px; top: 50%; transform: translateY(-50%); color: var(--muted); font-size: 18px; }
.sheet-btn { width: 100%; background: var(--gold); color: var(--black); border: none; padding: 14px; border-radius: 24px; font-weight: 800; font-size: 14px; cursor: pointer; display: flex; align-items: center; justify-content: center; gap: 8px; margin-bottom: 10px; transition: 0.2s;}
.sheet-btn:active { transform: scale(0.96); }
.sheet-btn.txt { background: var(--sub); color: #fff; }

.sheet-list { display: flex; flex-direction: column; gap: 10px; }
.sheet-item { display: flex; align-items: center; justify-content: space-between; padding: 14px 18px; background: var(--card); border: 1px solid var(--border); border-radius: 20px; cursor: pointer; text-decoration: none; transition: all 0.2s; }
.sheet-item:hover { border-color: var(--gold-dim); background: var(--gold-glow); transform: translateX(-4px); }
.sheet-item:active { transform: scale(0.96); }
.sheet-item-left { display: flex; align-items: center; gap: 14px; }
.sheet-item-ico { font-size: 20px; color: var(--gold); }
.sheet-item-title { font-weight: 800; font-size: 13px; color: var(--text); }
.sheet-item-sub { font-size: 10px; font-weight: 600; color: var(--muted); margin-top: 3px; }
.sheet-item-right { font-size: 11px; font-weight: 700; color: var(--muted); }

/* Playlists specific sheet UI */
.pl-sheet-item { display: flex; align-items: center; justify-content: space-between; padding: 14px 18px; background: var(--card); border: 1px solid var(--border); border-radius: 16px; cursor: pointer; margin-bottom: 8px; transition: 0.2s; }
.pl-sheet-item:hover { border-color: var(--gold-dim); }
.pl-sheet-item.in-list { background: rgba(212,175,55,0.1); border-color: var(--gold-dim); }
.pl-sheet-item.in-list i.check { color: var(--gold); display: block; }
.pl-sheet-item i.check { display: none; }

.search-header { padding: 10px 0 24px; }
.search-header h2 { font-weight: 800; font-size: 22px; }
.filter-bar { display: flex; gap: 8px; margin-bottom: 24px; flex-wrap: wrap; }
.filter-btn { font-size: 12px; font-weight: 700; padding: 8px 18px; border: 1px solid var(--border); border-radius: 20px; background: var(--surface); color: var(--muted); cursor: pointer; transition: 0.2s; }
.filter-btn:active { transform: scale(0.92); }
.filter-btn.active { background: var(--gold-glow); border-color: var(--gold-dim); color: var(--gold); }

.empty-state { text-align: center; padding: 80px 20px; color: var(--muted); width: 100%; }
.empty-icon { font-size: 48px; color: var(--sub); margin-bottom: 16px; }
.empty-title { font-weight: 800; font-size: 18px; color: var(--sub); }
.spinner { display: inline-block; width: 18px; height: 18px; border: 2px solid var(--border); border-top-color: var(--gold); border-radius: 50%; animation: spin 0.7s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.loading-more { text-align: center; padding: 24px; color: var(--muted); font-size: 13px; font-weight: 700; width: 100%; display: flex; align-items: center; justify-content: center; gap: 10px; }
.toast { position: fixed; top: 80px; left: 50%; transform: translateX(-50%) translateY(-20px); background: var(--card); border: 1px solid var(--gold-dim); color: var(--text); padding: 12px 24px; border-radius: 24px; font-size: 12px; font-weight: 700; z-index: 5000; opacity: 0; pointer-events: none; transition: 0.3s cubic-bezier(0.2, 0.8, 0.2, 1); box-shadow: 0 10px 30px rgba(0,0,0,0.8); }
.toast.show { transform: translateX(-50%) translateY(0); opacity: 1; }

@media (max-width: 768px) {
  .desktop-only { display: none; }
  .main { padding-bottom: 120px; }
  .bottom-nav { display: flex; position: fixed; bottom: 12px; left: 12px; right: 12px; background: rgba(10, 10, 10, 0.9); backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px); border: 1px solid rgba(255,255,255,0.08); border-radius: 30px; padding: 8px 16px; justify-content: space-around; z-index: 2000; box-shadow: 0 -5px 30px rgba(0,0,0,0.8); }
  .bnav-item { background: none; border: none; color: var(--muted); cursor: pointer; display: flex; flex-direction: column; align-items: center; gap: 4px; font-size: 10px; font-weight: 700; padding: 6px 12px; border-radius: 16px; transition: 0.2s; }
  .bnav-item:active { transform: scale(0.9); }
  .bnav-item.active { color: var(--gold); background: var(--surface); }
  .bnav-item i { font-size: 18px; }
  .genre-grid { grid-template-columns: repeat(auto-fill, minmax(80px, 1fr)); }
  .fab-genre { bottom: 95px; width: 50px; height: 50px; font-size: 20px; } 
  .actor-header { flex-direction: column; text-align: center; padding: 24px 16px; }
  .detail-layout { margin-top: -80px; align-items: center; justify-content: center; text-align: center; }
  .detail-tags, .detail-plot { justify-content: center; }
}
</style>
</head>
<body>

<header class="header">
  <div class="header-inner">
    <div class="header-right">
      <nav class="nav-tabs desktop-only">
        <button class="nav-tab active" id="tab-home" onclick="showHome()"><i class="fa-solid fa-house"></i> خانه</button>
        <button class="nav-tab" id="tab-movies" onclick="filterType(0,'فیلم‌ها')"><i class="fa-solid fa-clapperboard"></i> فیلم</button>
        <button class="nav-tab" id="tab-series" onclick="filterType(1,'سریال‌ها')"><i class="fa-solid fa-tv"></i> سریال</button>
        <button class="nav-tab" id="tab-library" onclick="showLibrary()"><i class="fa-solid fa-bookmark"></i> کتابخانه من</button>
      </nav>
    </div>
    <div class="logo-center" onclick="showHome()">
      <img src="IMG_20260406_090531.jpg" alt="فیلیگرام" onerror="this.style.display='none'">
    </div>
    <div class="header-left">
      <button class="search-trigger" onclick="openSheet('searchSheet')"><i class="fa-solid fa-magnifying-glass"></i></button>
    </div>
  </div>
</header>

<nav class="bottom-nav">
  <button class="bnav-item active" id="bnav-home" onclick="showHome()"><i class="fa-solid fa-house"></i><span>خانه</span></button>
  <button class="bnav-item" id="bnav-movies" onclick="filterType(0,'فیلم‌ها')"><i class="fa-solid fa-clapperboard"></i><span>فیلم</span></button>
  <button class="bnav-item" id="bnav-series" onclick="filterType(1,'سریال‌ها')"><i class="fa-solid fa-tv"></i><span>سریال</span></button>
  <button class="bnav-item" id="bnav-library" onclick="showLibrary()"><i class="fa-solid fa-layer-group"></i><span>کتابخانه</span></button>
</nav>

<div class="fab-genre" id="fabGenre" onclick="openGenreMenu()">
  <i class="fa-solid fa-layer-group"></i>
</div>

<main class="main">

  <!-- HOME VIEW -->
  <div id="view-home" class="view active">
    <div class="hero" id="heroSection">
      <div class="hero-slides" id="heroSlides"></div>
    </div>
    <div id="vitrinWrap"></div>
    <div id="homeLoadingMore" style="display:none;" class="loading-more"><div class="spinner"></div> در حال بارگذاری بیشتر...</div>
  </div>

  <!-- GENRE VIEW -->
  <div id="view-genre" class="view padded-view">
    <button class="btn-back-std" onclick="goBackToTab()"><i class="fa-solid fa-arrow-right"></i> بازگشت</button>
    <div class="search-header">
      <h2 id="genreViewTitle">ژانر</h2>
    </div>
    <div id="genreResults"><div class="cards-grid"><?php echo buildSkeletonCards(12); ?></div></div>
    <div id="genreLoadingMore" style="display:none;" class="loading-more"><div class="spinner"></div> بارگذاری بیشتر...</div>
  </div>

  <!-- CATEGORY EXPANDED VIEW -->
  <div id="view-category" class="view padded-view">
    <button class="btn-back-std" onclick="showHome()"><i class="fa-solid fa-arrow-right"></i> بازگشت به خانه</button>
    <div class="search-header">
      <h2 id="catViewTitle">دسته بندی</h2>
    </div>
    <div id="catResults"><div class="cards-grid"><?php echo buildSkeletonCards(12); ?></div></div>
    <div id="catLoadingMore" style="display:none;" class="loading-more"><div class="spinner"></div> بارگذاری بیشتر...</div>
  </div>

  <!-- SEARCH VIEW -->
  <div id="view-search" class="view padded-view">
    <div class="search-header">
      <h2 id="searchViewTitle">جستجو</h2>
    </div>
    <div class="filter-bar">
      <button class="filter-btn active" id="filter-all" onclick="setFilter(2)"><i class="fa-solid fa-border-all"></i> همه</button>
      <button class="filter-btn" id="filter-movies" onclick="setFilter(0)"><i class="fa-solid fa-clapperboard"></i> فیلم</button>
      <button class="filter-btn" id="filter-series" onclick="setFilter(1)"><i class="fa-solid fa-tv"></i> سریال</button>
    </div>
    <div id="searchResults"><div class="cards-grid"><?php echo buildSkeletonCards(12); ?></div></div>
    <div id="searchLoadingMore" style="display:none;" class="loading-more"><div class="spinner"></div> بارگذاری برگه بعدی...</div>
  </div>

  <!-- LIBRARY (FAVS & PLAYLISTS) VIEW -->
  <div id="view-library" class="view padded-view">
    <div class="search-header"><h2><i class="fa-solid fa-bookmark" style="color:var(--gold)"></i> کتابخانه من</h2></div>
    
    <div class="lib-tabs-wrapper">
      <button class="lib-tab active" id="l-tab-favs" onclick="switchLibTab('favs')"><i class="fa-solid fa-heart"></i> علاقه‌مندی‌ها</button>
      <button class="lib-tab" id="l-tab-pl" onclick="switchLibTab('playlists')"><i class="fa-solid fa-list-ul"></i> پلی‌لیست‌ها</button>
    </div>

    <!-- Favs Content -->
    <div id="libContentFavs">
      <div id="favsResults"><div class="empty-state"><i class="fa-solid fa-box-open empty-icon"></i><div class="empty-title">لیست علاقه‌مندی خالی است</div></div></div>
    </div>

    <!-- Playlists Content -->
    <div id="libContentPlaylists" style="display:none;">
      <button class="btn btn-primary" style="margin-bottom:20px; width:100%;" onclick="createNewPlaylist()"><i class="fa-solid fa-plus"></i> ایجاد پلی‌لیست جدید</button>
      <div id="plResults"><div class="empty-state"><i class="fa-solid fa-box-open empty-icon"></i><div class="empty-title">پلی‌لیستی ندارید</div></div></div>
    </div>
  </div>

  <!-- SINGLE PLAYLIST DETAIL VIEW (Shared or Local) -->
  <div id="view-playlist-detail" class="view padded-view">
    <button class="btn-back-std" onclick="goBackFromPlaylist()"><i class="fa-solid fa-arrow-right"></i> بازگشت</button>
    <div class="search-header" style="display:flex; justify-content:space-between; align-items:center;">
      <div>
         <h2 id="pldTitle" style="color:var(--gold);">--</h2>
         <p id="pldCount" style="font-size:12px; color:var(--muted); margin-top:4px;">0 آیتم</p>
      </div>
      <div class="player-actions" id="pldActions"></div>
    </div>
    <div id="pldResults"><div class="cards-grid"></div></div>
    <div id="pldLoadingMore" style="display:none;" class="loading-more"><div class="spinner"></div> بارگذاری لیست...</div>
  </div>

  <!-- MOVIE/SERIES DETAIL VIEW -->
  <div id="view-detail" class="view">
    <div id="detailContent"><?php echo buildDetailSkeleton(); ?></div>
  </div>

  <!-- ACTOR DETAIL VIEW -->
  <div id="view-actor" class="view padded-view">
    <button class="btn-back-std" onclick="goBackFromActor()"><i class="fa-solid fa-arrow-right"></i> بازگشت به جزئیات</button>
    
    <div class="actor-header">
      <div class="actor-avatar-wrap">
        <img id="actorAvatar" src="ChatGPT Image Apr 9, 2026, 03_25_39 PM.png" alt="Actor" onerror="this.src='ChatGPT Image Apr 9, 2026, 03_25_39 PM.png'">
      </div>
      <div class="actor-info">
        <h2 class="actor-name" id="actorName">--</h2>
        <div class="actor-role" id="actorRole">در حال بارگذاری...</div>
      </div>
    </div>

    <div class="section-header"><h2 class="section-title"><i class="fa-solid fa-film"></i> فیلم‌ها و سریال‌ها</h2></div>
    <div id="actorResults"><div class="cards-grid"><?php echo buildSkeletonCards(12); ?></div></div>
    <div id="actorLoadingMore" style="display:none;" class="loading-more"><div class="spinner"></div> بارگذاری بیشتر...</div>
  </div>

  <!-- VIDEO PLAYER VIEW -->
  <div id="view-player" class="view padded-view">
    <button class="btn-back-std" onclick="goBackFromPlayer()"><i class="fa-solid fa-arrow-right"></i> بازگشت به جزئیات</button>
    
    <div class="custom-player-wrapper" id="customPlayerWrapper">
      <video id="mainPlayer" playsinline preload="auto"></video>
      <div class="player-overlay" id="playerOverlay">
        <div class="player-center-controls" id="centerPlayPause">
           <i class="fa-solid fa-play"></i>
        </div>
        <div class="player-bottom-controls">
          <div class="progress-container" id="progressContainer">
            <div class="progress-bar" id="progressBar">
               <div class="progress-thumb" id="progressThumb"></div>
            </div>
          </div>
          <div class="controls-row">
            <div class="controls-left">
              <button id="btnPlayPause" class="ctrl-btn"><i class="fa-solid fa-play"></i></button>
              <span id="timeDisplay" class="time-display">00:00 / 00:00</span>
            </div>
            <div class="controls-right">
              <button id="btnSubtitle" class="ctrl-btn"><i class="fa-solid fa-closed-captioning"></i></button>
              <button id="btnFullscreenCustom" class="ctrl-btn" onclick="toggleFullscreen()"><i class="fa-solid fa-expand"></i></button>
            </div>
          </div>
        </div>
      </div>
      
      <!-- Subtitles Menu -->
      <div class="subtitles-menu" id="subtitlesMenu">
        <div class="sub-menu-title">زیرنویس</div>
        <div id="subTracksList"></div>
      </div>
    </div>
    
    <div class="player-controls-row">
      <div class="player-title-block">
        <div class="player-title" id="playerTitle">—</div>
        <div class="player-subtitle" id="playerSubtitle"></div>
      </div>
      <div class="player-actions">
        <button class="p-btn" onclick="openSheet('qualitySheet')"><i class="fa-solid fa-sliders"></i> کیفیت</button>
        <button class="p-btn" onclick="openSheet('downloadSheet')"><i class="fa-solid fa-download"></i> دانلود / پخش خارجی</button>
        <button class="p-btn" onclick="toggleFullscreen()"><i class="fa-solid fa-expand"></i> تمام‌صفحه</button>
      </div>
    </div>
  </div>

</main>

<!-- BOTTOM SHEETS -->
<div class="sheet-backdrop" id="genreSheet" onclick="closeSheetOnBg(event, 'genreSheet')">
  <div class="sheet">
    <div class="sheet-drag"></div>
    <div class="sheet-title" id="genreSheetTitle">انتخاب ژانر</div>
    <div class="sheet-subtitle">انتخاب کنید تا محتوا بر اساس ژانر نمایش داده شود</div>
    <div id="genreList" class="genre-grid"></div>
  </div>
</div>

<div class="sheet-backdrop" id="searchSheet" onclick="closeSheetOnBg(event, 'searchSheet')">
  <div class="sheet">
    <div class="sheet-drag"></div>
    <div class="sheet-title">جستجو</div>
    <div class="sheet-subtitle">نام فیلم، سریال یا بازیگر را وارد کنید</div>
    <div class="search-box">
      <i class="fa-solid fa-magnifying-glass"></i>
      <input type="text" id="searchInput" placeholder="جستجو..." onkeydown="if(event.key==='Enter') triggerSearchBtn()">
    </div>
    <button class="sheet-btn" onclick="triggerSearchBtn()"><i class="fa-solid fa-search"></i> جستجو</button>
  </div>
</div>

<div class="sheet-backdrop" id="qualitySheet" onclick="closeSheetOnBg(event, 'qualitySheet')">
  <div class="sheet">
    <div class="sheet-drag"></div>
    <div class="sheet-title">انتخاب کیفیت</div>
    <div class="sheet-subtitle">کیفیت پخش را انتخاب کنید</div>
    <div class="sheet-list" id="qualityList"></div>
  </div>
</div>

<div class="sheet-backdrop" id="downloadSheet" onclick="closeSheetOnBg(event, 'downloadSheet')">
  <div class="sheet">
    <div class="sheet-drag"></div>
    <div class="sheet-title">لینک‌های دانلود و پخش</div>
    <div class="sheet-subtitle">دانلود مستقیم، ارسال به ADM یا پخش در MX Player / VLC</div>
    <div style="display:flex; gap:8px; margin-bottom:16px;">
       <button class="sheet-btn txt" style="flex:1" onclick="downloadAllLinksTXT()"><i class="fa-solid fa-file-code"></i> دانلود فایل لینک‌ها (TXT)</button>
    </div>
    <div class="sheet-list" id="downloadList"></div>
  </div>
</div>

<div class="sheet-backdrop" id="addToPlSheet" onclick="closeSheetOnBg(event, 'addToPlSheet')">
  <div class="sheet">
    <div class="sheet-drag"></div>
    <div class="sheet-title">افزودن به پلی‌لیست</div>
    <div class="sheet-subtitle">کدام لیست را انتخاب می‌کنید؟</div>
    <button class="sheet-btn" onclick="closeSheet('addToPlSheet'); createNewPlaylist();"><i class="fa-solid fa-plus"></i> ایجاد پلی‌لیست جدید</button>
    <div class="sheet-list" id="addToPlList" style="margin-top:16px;"></div>
  </div>
</div>

<div class="toast" id="toast"></div>

<script>
const API = 'movilel.php';
const FALLBACK_IMG = 'ChatGPT Image Apr 9, 2026, 03_25_39 PM.png';
const SKEL_CARDS = `<?php echo buildSkeletonCards(12); ?>`;
const SKEL_EPISODES_HTML = `<?php echo buildEpisodeSkeleton(5); ?>`;

let state = {
  currentView: 'home',
  
  vitrinPage: 1, vitrinLoading: false, vitrinHasMore: true, vitrinTotalCategories: 0,
  catId: null, catTitle: '', catPage: 1, catLoading: false, catHasMore: true,
  searchPage: 1, searchLoading: false, searchHasMore: true, currentSearchQuery: '+', currentSearchType: 2,
  genrePage: 1, genreLoading: false, genreHasMore: true, currentGenreId: null, currentGenreType: 0, currentGenreName: '',
  allMovieGenres: [], allSeriesGenres: [],
  actorId: null, actorPage: 1, actorLoading: false, actorHasMore: true,
  
  heroTimer: null, heroIndex: 0, heroData: [],
  currentDetailId: null, playerFromId: null,
  allQualities: [], currentQualityData: null,
  
  favorites: JSON.parse(localStorage.getItem('mili_favs') || '[]'),
  playlists: JSON.parse(localStorage.getItem('mili_playlists') || '[]'),
  
  sharedQueue: [], sharedLoading: false, currentViewPlaylistId: null
};

// Utils
function esc(str) { 
  if (str == null) return '';
  let s = String(str);
  s = s.replace(/موویلیکس/g, 'فیلیگرام');
  s = s.replace(/Movielix/gi, 'Filigram');
  return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;').replace(/'/g,'&#39;'); 
}
// تابعی امن‌تر برای متغیرهایی که درون جاوااسکریپت و رویدادهایی مثل onclick قرار می‌گیرند تا از خطای کوتیشن جلوگیری شود
function escJS(str) {
  if (str == null) return '';
  let s = String(str);
  s = s.replace(/موویلیکس/g, 'فیلیگرام');
  s = s.replace(/Movielix/gi, 'Filigram');
  return s.replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/"/g, '&quot;');
}

function getImg(url) {
  if (!url) return FALLBACK_IMG;
  if (url.includes('movielix.org/assets/data/logo.png')) return 'IMG_20260406_090531.jpg';
  if (url.startsWith('http')) return url;
  if (url.startsWith('//')) return 'https:' + url;
  return 'https://content.expertapp.org' + (url.startsWith('/') ? url : '/' + url);
}
function showToast(msg) { const t = document.getElementById('toast'); t.textContent = msg; t.classList.add('show'); setTimeout(()=>t.classList.remove('show'), 3000); }
async function api(params) { try { const res = await fetch(API + '?' + new URLSearchParams(params)); return await res.json(); } catch(e) { return { error: e.message }; } }
function openSheet(id) { document.getElementById(id).classList.add('open'); }
function closeSheet(id) { document.getElementById(id).classList.remove('open'); }
function closeSheetOnBg(e, id) { if(e.target.id === id) closeSheet(id); }
function closeAllSheets() { document.querySelectorAll('.sheet-backdrop').forEach(s => s.classList.remove('open')); }

function utoa(str) { return btoa(unescape(encodeURIComponent(str))); }
function atou(str) { return decodeURIComponent(escape(atob(str))); }

// Views Management
function showView(name) {
  document.querySelectorAll('.view').forEach(v => { v.classList.remove('active'); v.style.animation = 'none'; });
  const v = document.getElementById('view-' + name);
  v.classList.add('active');
  void v.offsetWidth;
  v.style.animation = 'smoothFadeIn 0.3s cubic-bezier(0.2, 0.8, 0.2, 1) forwards';
  state.currentView = name;
  updateFabVisibility();
}
function setActiveTab(id) { 
  document.querySelectorAll('.nav-tab, .bnav-item').forEach(t=>t.classList.remove('active')); 
  if(id) { document.getElementById(id)?.classList.add('active'); document.getElementById(id.replace('tab-','bnav-'))?.classList.add('active'); } 
}
function setActiveFilter(type) { 
  document.querySelectorAll('.filter-btn').forEach(b=>b.classList.remove('active')); 
  document.getElementById(type===2?'filter-all':type===0?'filter-movies':'filter-series')?.classList.add('active'); 
}
function updateFabVisibility() {
  const fab = document.getElementById('fabGenre');
  if (fab) {
    if ((state.currentView === 'search' || state.currentView === 'genre') && (state.currentSearchType === 0 || state.currentSearchType === 1)) {
      fab.classList.add('visible');
    } else { fab.classList.remove('visible'); }
  }
}

// Data fetching and UI building
async function fetchGenres(type) {
  if (type === 0 && state.allMovieGenres.length > 0) return state.allMovieGenres;
  if (type === 1 && state.allSeriesGenres.length > 0) return state.allSeriesGenres;
  const data = await api({ action: 'genres', type });
  if (!data.error && data.genres) {
    if (type === 0) state.allMovieGenres = data.genres; else state.allSeriesGenres = data.genres;
    return data.genres;
  }
  return [];
}
async function openGenreMenu() {
  const type = state.currentSearchType; 
  document.getElementById('genreSheetTitle').innerText = type === 0 ? 'ژانرهای فیلم' : 'ژانرهای سریال';
  const genres = await fetchGenres(type);
  const container = document.getElementById('genreList');
  container.innerHTML = genres.map(g => `<div class="genre-item" onclick="selectGenre(${type}, ${g.id}, '${escJS(g.name)}')"><i class="fa-solid ${type === 0 ? 'fa-film' : 'fa-tv'}"></i>${esc(g.name)}</div>`).join('');
  openSheet('genreSheet');
}
async function selectGenre(type, genreId, genreName) {
  closeSheet('genreSheet');
  state.currentGenreType = type; state.currentGenreId = genreId; state.currentGenreName = genreName; state.genrePage = 1; state.genreHasMore = true;
  showView('genre');
  document.getElementById('genreViewTitle').innerHTML = `${type === 0 ? 'فیلم‌های' : 'سریال‌های'} ژانر <span style="color:var(--gold)">${genreName}</span>`;
  document.getElementById('genreResults').innerHTML = `<div class="cards-grid">${SKEL_CARDS}</div>`;
  window.scrollTo(0,0);
  await loadGenreContent(true);
}
async function loadGenreContent(reset = true) {
  if (reset) { state.genrePage = 1; state.genreHasMore = true; }
  if (state.genreLoading || !state.genreHasMore || !state.currentGenreId) return;
  state.genreLoading = true;
  if (state.genrePage > 1) document.getElementById('genreLoadingMore').style.display = 'flex';
  const data = await api({ action: 'by_genre', type: state.currentGenreType, genre_id: state.currentGenreId, page: state.genrePage });
  state.genreLoading = false; document.getElementById('genreLoadingMore').style.display = 'none';
  if (data.error) { showToast(data.error); state.genreHasMore = false; document.getElementById('genreResults').innerHTML = `<div class="empty-state"><i class="fa-solid fa-exclamation-triangle empty-icon"></i><div class="empty-title">${data.error}</div></div>`; return; }
  let items = [];
  if (data.categories && data.categories.length) { data.categories.forEach(cat => { if (cat.items && cat.items.length) items.push(...cat.items); }); }
  state.genreHasMore = data.last_page > data.current_page;
  const html = items.map(i => buildCard(i)).join('');
  if (reset) { document.getElementById('genreResults').innerHTML = items.length ? `<div class="cards-grid">${html}</div>` : `<div class="empty-state"><i class="fa-solid fa-ghost empty-icon"></i><div class="empty-title">موردی یافت نشد</div></div>`; } 
  else if (items.length) { document.querySelector('#genreResults .cards-grid').insertAdjacentHTML('beforeend', html); }
  if (state.genreHasMore && items.length > 0) state.genrePage++;
}
function goBackToTab() { filterType(state.currentSearchType, state.currentSearchType === 0 ? 'فیلم‌ها' : 'سریال‌ها'); }

async function openCategory(id, title) {
  state.catId = id; state.catTitle = title || ''; state.catPage = 1; state.catHasMore = true;
  document.getElementById('catViewTitle').innerText = title;
  showView('category'); window.scrollTo(0,0);
  document.getElementById('catResults').innerHTML = `<div class="cards-grid">${SKEL_CARDS}</div>`;
  await loadCategoryContent(true);
}
async function loadCategoryContent(reset = true) {
  if(reset) { state.catPage = 1; state.catHasMore = true; }
  if(state.catLoading || !state.catHasMore) return;
  state.catLoading = true;
  if(state.catPage > 1) document.getElementById('catLoadingMore').style.display = 'flex';
  const data = await api({ action: 'category_items', id: state.catId, page: state.catPage });
  state.catLoading = false; document.getElementById('catLoadingMore').style.display = 'none';
  if(data.error) { showToast(data.error); state.catHasMore = false; return; }
  let items = data.detail?.data || [];
  state.catHasMore = (data.detail?.current_page < data.detail?.last_page);
  const isActorCategory = state.catTitle.includes('بازیگر');
  const html = items.map(i => buildCard(i, isActorCategory)).join('');
  if(reset) { document.getElementById('catResults').innerHTML = items.length ? `<div class="cards-grid">${html}</div>` : `<div class="empty-state"><i class="fa-solid fa-ghost empty-icon"></i><div class="empty-title">موردی یافت نشد</div></div>`; } 
  else if(items.length) { document.querySelector('#catResults .cards-grid').insertAdjacentHTML('beforeend', html); }
  if(items.length) state.catPage++;
}

async function openActor(id, name) {
  state.actorId = id; state.actorPage = 1; state.actorHasMore = true;
  document.getElementById('actorName').innerText = name || 'مشخصات هنرمند';
  document.getElementById('actorRole').innerText = 'در حال بارگذاری...';
  document.getElementById('actorAvatar').src = 'data:image/gif;base64,R0lGODlhAQABAAD/ACwAAAAAAQABAAACADs='; 
  showView('actor'); window.scrollTo(0,0);
  document.getElementById('actorResults').innerHTML = `<div class="cards-grid">${SKEL_CARDS}</div>`;
  await loadActorContent(true);
}
async function loadActorContent(reset = true) {
  if(reset) { state.actorPage = 1; state.actorHasMore = true; }
  if(state.actorLoading || !state.actorHasMore) return;
  state.actorLoading = true;
  if(state.actorPage > 1) document.getElementById('actorLoadingMore').style.display = 'flex';
  const data = await api({ action: 'actor_details', id: state.actorId, page: state.actorPage });
  state.actorLoading = false; document.getElementById('actorLoadingMore').style.display = 'none';
  if(data.error) { showToast(data.error); state.actorHasMore = false; return; }
  if (reset && data.actor) {
     document.getElementById('actorName').innerText = data.actor.name || 'بدون نام';
     document.getElementById('actorRole').innerText = data.actor.role || 'بازیگر';
     if(data.actor.image) document.getElementById('actorAvatar').src = getImg(data.actor.image);
  }
  let items = data.movies || [];
  state.actorHasMore = data.has_more;
  const html = items.map(i => buildCard(i)).join('');
  if(reset) { document.getElementById('actorResults').innerHTML = items.length ? `<div class="cards-grid">${html}</div>` : `<div class="empty-state"><i class="fa-solid fa-ghost empty-icon"></i><div class="empty-title">موردی یافت نشد</div></div>`; } 
  else if(items.length) { document.querySelector('#actorResults .cards-grid').insertAdjacentHTML('beforeend', html); }
  if(items.length) state.actorPage++;
}
function goBackFromActor() { if (state.currentDetailId) showDetail(state.currentDetailId); else showHome(); }

function showHome() { 
  setActiveTab('tab-home'); showView('home'); window.scrollTo(0,0);
  if (state.vitrinPage === 1 && !state.vitrinLoading) loadVitrin(); 
}

// LIBRARY & PLAYLIST LOGIC
function showLibrary() {
  setActiveTab('tab-library'); showView('library'); window.scrollTo(0,0);
  renderFavorites(); renderPlaylists();
}
function switchLibTab(tab) {
  document.getElementById('l-tab-favs').classList.remove('active');
  document.getElementById('l-tab-pl').classList.remove('active');
  document.getElementById('libContentFavs').style.display = 'none';
  document.getElementById('libContentPlaylists').style.display = 'none';
  
  if(tab === 'favs') {
    document.getElementById('l-tab-favs').classList.add('active');
    document.getElementById('libContentFavs').style.display = 'block';
  } else {
    document.getElementById('l-tab-pl').classList.add('active');
    document.getElementById('libContentPlaylists').style.display = 'block';
  }
}
function renderFavorites() {
  const cont = document.getElementById('favsResults');
  if(!state.favorites.length) { cont.innerHTML = `<div class="empty-state"><i class="fa-solid fa-box-open empty-icon"></i><div class="empty-title">لیست خالی است</div></div>`; return; }
  cont.innerHTML = `<div class="cards-grid">${state.favorites.map(fav => buildCard(fav)).join('')}</div>`;
}
function savePlaylists() { try { localStorage.setItem('mili_playlists', JSON.stringify(state.playlists)); } catch(e){} }
function createNewPlaylist() {
  const name = prompt('نام پلی‌لیست جدید را وارد کنید:');
  if(!name || !name.trim()) return;
  const newPl = { id: 'pl_' + Date.now(), name: name.trim(), items: [] };
  state.playlists.push(newPl); savePlaylists(); showToast('پلی‌لیست ایجاد شد');
  if(state.currentView === 'library') renderPlaylists();
  if(document.getElementById('addToPlSheet').classList.contains('open') && state.currentQualityData) {
      openAddToPlaylistSheet(state.currentQualityData.tempItem);
  }
}
function renderPlaylists() {
  const cont = document.getElementById('plResults');
  if(!state.playlists.length) { cont.innerHTML = `<div class="empty-state"><i class="fa-solid fa-box-open empty-icon"></i><div class="empty-title">پلی‌لیستی ندارید</div></div>`; return; }
  
  cont.innerHTML = state.playlists.map(pl => `
    <div class="playlist-card" onclick="openPlaylistDetail('${pl.id}', false)">
      <div class="playlist-info">
        <h3>${esc(pl.name)}</h3>
        <p>${pl.items.length} آیتم</p>
      </div>
      <div class="playlist-actions" onclick="event.stopPropagation()">
        <button class="pl-action-btn" onclick="sharePlaylist('${pl.id}')" title="اشتراک‌گذاری"><i class="fa-solid fa-share-nodes"></i></button>
        <button class="pl-action-btn" onclick="renamePlaylist('${pl.id}')" title="ویرایش نام"><i class="fa-solid fa-pen"></i></button>
        <button class="pl-action-btn" onclick="deletePlaylist('${pl.id}')" title="حذف" style="color:var(--red)"><i class="fa-solid fa-trash"></i></button>
      </div>
    </div>
  `).join('');
}
function renamePlaylist(id) {
  const pl = state.playlists.find(p => p.id === id); if(!pl) return;
  const newName = prompt('نام جدید:', pl.name);
  if(newName && newName.trim()) { pl.name = newName.trim(); savePlaylists(); renderPlaylists(); showToast('نام تغییر کرد'); }
}
function deletePlaylist(id) {
  if(!confirm('آیا از حذف این پلی‌لیست مطمئن هستید؟')) return;
  state.playlists = state.playlists.filter(p => p.id !== id);
  savePlaylists(); renderPlaylists(); showToast('پلی‌لیست حذف شد');
}
function sharePlaylist(id) {
  const pl = state.playlists.find(p => p.id === id); if(!pl) return;
  if(pl.items.length === 0) return showToast('پلی‌لیست خالی است');
  const data = [pl.name, pl.items.map(i=>i.id)];
  const b64 = utoa(JSON.stringify(data));
  const shareUrl = window.location.origin + window.location.pathname + '?p=' + b64;
  
  if (navigator.share) navigator.share({ title: pl.name, url: shareUrl }).catch(console.error);
  else { navigator.clipboard.writeText(shareUrl); showToast('لینک پلی‌لیست کپی شد!'); }
}
function openAddToPlaylistSheet(item) {
  state.currentQualityData = { tempItem: item }; 
  const cont = document.getElementById('addToPlList');
  if(state.playlists.length === 0) { cont.innerHTML = '<p style="text-align:center;color:var(--muted);font-size:12px;">پلی‌لیستی وجود ندارد.</p>'; }
  else {
      cont.innerHTML = state.playlists.map(pl => {
          const inList = pl.items.some(i => i.id === item.id);
          return `<div class="pl-sheet-item ${inList ? 'in-list' : ''}" onclick="togglePlaylistItem('${pl.id}', ${item.id}, '${escJS(item.title)}', '${escJS(item.image)}', ${item.type})">
            <div><div class="sheet-item-title">${esc(pl.name)}</div><div class="sheet-item-sub">${pl.items.length} / 100</div></div>
            <i class="fa-solid fa-check check"></i>
          </div>`;
      }).join('');
  }
  openSheet('addToPlSheet');
}
function togglePlaylistItem(pid, itemId, title, image, type) {
  const pl = state.playlists.find(p => p.id === pid); if(!pl) return;
  const idx = pl.items.findIndex(i => i.id === itemId);
  if(idx > -1) { pl.items.splice(idx, 1); showToast('از پلی‌لیست حذف شد'); } 
  else {
      if(pl.items.length >= 100) return showToast('حداکثر ۱۰۰ آیتم در هر لیست مجاز است');
      pl.items.push({id: itemId, title, image, type}); showToast('به پلی‌لیست اضافه شد');
  }
  savePlaylists(); openAddToPlaylistSheet({id:itemId, title, image, type});
  if(state.currentView === 'library') renderPlaylists(); 
}

// PLAYLIST DETAIL VIEW (Local & Shared)
async function openPlaylistDetail(idOrBase64, isShared = false) {
  state.currentViewPlaylistId = isShared ? null : idOrBase64;
  state.sharedQueue = [];
  document.getElementById('pldResults').innerHTML = `<div class="cards-grid">${SKEL_CARDS}</div>`;
  document.getElementById('pldActions').innerHTML = '';
  showView('playlist-detail'); window.scrollTo(0,0);
  
  if(!isShared) {
     const pl = state.playlists.find(p => p.id === idOrBase64);
     if(!pl) { goBackFromPlaylist(); return; }
     document.getElementById('pldTitle').innerText = pl.name;
     document.getElementById('pldCount').innerText = `${pl.items.length} آیتم (لوکال)`;
     document.getElementById('pldActions').innerHTML = `<button class="p-btn" onclick="sharePlaylist('${pl.id}')"><i class="fa-solid fa-share-nodes"></i> اشتراک</button>`;
     
     document.getElementById('pldResults').innerHTML = pl.items.length ? `<div class="cards-grid">${pl.items.map(i=>buildCard(i)).join('')}</div>` : `<div class="empty-state"><i class="fa-solid fa-box-open empty-icon"></i><div class="empty-title">خالی</div></div>`;
  } else {
     try {
       const data = JSON.parse(atou(idOrBase64)); 
       document.getElementById('pldTitle').innerText = data[0];
       document.getElementById('pldCount').innerText = `${data[1].length} آیتم (لینک اشتراکی)`;
       state.sharedQueue = data[1];
       document.getElementById('pldResults').innerHTML = `<div class="cards-grid"></div>`;
       await loadNextSharedBatch();
     } catch(e) { showToast('لینک نامعتبر است'); goBackFromPlaylist(); }
  }
}
async function loadNextSharedBatch() {
  if(state.sharedQueue.length === 0 || state.sharedLoading) return;
  state.sharedLoading = true;
  document.getElementById('pldLoadingMore').style.display = 'flex';
  
  const batchIds = state.sharedQueue.splice(0, 6);
  const promises = batchIds.map(id => api({action: 'details', id}));
  const results = await Promise.all(promises);
  
  let html = '';
  results.forEach(res => {
      if(!res.error && res.info) {
          html += buildCard({
             id: res.info.id, title: res.info.name || res.info.title,
             image: res.info.image, type: res.info.type,
             has_sub: res.info.is_sub==1, has_dub: res.info.is_dubbed==1
          });
      }
  });
  
  if(html) document.querySelector('#pldResults .cards-grid').insertAdjacentHTML('beforeend', html);
  state.sharedLoading = false; document.getElementById('pldLoadingMore').style.display = 'none';
  if(state.sharedQueue.length > 0 && document.querySelector('#pldResults .cards-grid').children.length < 6) loadNextSharedBatch();
}
function goBackFromPlaylist() { 
  if(state.currentViewPlaylistId) showLibrary(); else showHome(); 
}

// HOME SCROLL
async function loadVitrin(reset = true) {
  if (reset) { state.vitrinPage = 1; state.vitrinHasMore = true; document.getElementById('vitrinWrap').innerHTML = ''; document.getElementById('homeLoadingMore').style.display = 'none'; }
  if (state.vitrinLoading || !state.vitrinHasMore) return;
  state.vitrinLoading = true; if (state.vitrinPage > 1) document.getElementById('homeLoadingMore').style.display = 'flex';
  const data = await api({ action: 'vitrin', scroll_mode: 'true', page: state.vitrinPage, limit: 3 });
  state.vitrinLoading = false; document.getElementById('homeLoadingMore').style.display = 'none';
  if (data.error) { state.vitrinHasMore = false; return; }
  
  if (state.vitrinPage === 1 && data.slider && data.slider.length) buildHero(data.slider);
  else if (state.vitrinPage === 1 && (!data.slider || !data.slider.length)) document.getElementById('heroSection').style.display = 'none';
  
  const categories = data.categories || [];
  if (!categories.length) { state.vitrinHasMore = false; return; }
  
  const container = document.getElementById('vitrinWrap');
  const html = categories.map(cat => {
    const isActorCategory = cat.title && cat.title.includes('بازیگر');
    return `
    <div class="section">
      <div class="section-header">
        <h2 class="section-title"><i class="fa-solid fa-play"></i> ${esc(cat.title)}</h2>
        <button class="btn btn-sec" style="font-size:11px; padding:6px 14px;" onclick="openCategory(${cat.id}, '${escJS(cat.title)}')">مشاهده همه <i class="fa-solid fa-chevron-left"></i></button>
      </div>
      <div class="cards-scroll">${cat.items.map(item => buildCard(item, isActorCategory)).join('')}</div>
    </div>`;
  }).join('');
  
  if (state.vitrinPage === 1) container.innerHTML = html; else container.insertAdjacentHTML('beforeend', html);
  state.vitrinHasMore = data.has_more === true;
  if (state.vitrinHasMore) state.vitrinPage++;
}

// SEARCH
function triggerSearchBtn() {
  const val = document.getElementById('searchInput').value.trim(); if(!val) return;
  closeSheet('searchSheet'); state.currentSearchQuery = val;
  if(state.currentView !== 'search') state.currentSearchType = 2;
  setActiveTab(''); showView('search'); window.scrollTo(0,0);
  document.getElementById('searchViewTitle').textContent = `نتیجه برای: ${val}`;
  document.getElementById('searchResults').innerHTML = `<div class="cards-grid">${SKEL_CARDS}</div>`;
  setActiveFilter(state.currentSearchType); performSearch(val, state.currentSearchType, 1);
}
function setFilter(type) { state.currentSearchType = type; setActiveFilter(type); updateFabVisibility(); document.getElementById('searchResults').innerHTML = `<div class="cards-grid">${SKEL_CARDS}</div>`; performSearch(state.currentSearchQuery || '+', type, 1); }
function filterType(type, label) {
  setActiveTab(type===0?'tab-movies':'tab-series'); state.currentSearchQuery = '+'; state.currentSearchType = type;
  showView('search'); window.scrollTo(0,0);
  document.getElementById('searchViewTitle').textContent = label;
  document.getElementById('searchResults').innerHTML = `<div class="cards-grid">${SKEL_CARDS}</div>`; 
  setActiveFilter(type); performSearch('+', type, 1);
}
async function performSearch(q, type, page) {
  state.searchPage = page; state.searchLoading = true; if(page === 1) state.searchHasMore = true;
  const data = await api({ action: 'search', q, type, page });
  state.searchLoading = false; document.getElementById('searchLoadingMore').style.display = 'none';
  const items = data.info?.data || data.data || []; if(items.length < 1) state.searchHasMore = false;
  const html = items.map(i => buildCard(i)).join('');
  if(page === 1) { document.getElementById('searchResults').innerHTML = items.length ? `<div class="cards-grid">${html}</div>` : `<div class="empty-state"><i class="fa-solid fa-ghost empty-icon"></i><div class="empty-title">موردی یافت نشد</div></div>`; } 
  else if (items.length) { document.querySelector('#searchResults .cards-grid').insertAdjacentHTML('beforeend', html); }
}

window.addEventListener('scroll', () => {
  const isBottom = (window.innerHeight + window.scrollY) >= document.body.offsetHeight - 800;
  if (isBottom) {
    if (state.currentView === 'home' && state.vitrinHasMore && !state.vitrinLoading) loadVitrin(false);
    if (state.currentView === 'search' && state.searchHasMore && !state.searchLoading) { document.getElementById('searchLoadingMore').style.display = 'flex'; performSearch(state.currentSearchQuery, state.currentSearchType, state.searchPage + 1); }
    if (state.currentView === 'genre' && state.genreHasMore && !state.genreLoading) loadGenreContent(false);
    if (state.currentView === 'category' && state.catHasMore && !state.catLoading) loadCategoryContent(false);
    if (state.currentView === 'actor' && state.actorHasMore && !state.actorLoading) loadActorContent(false);
    if (state.currentView === 'playlist-detail' && state.sharedQueue.length > 0 && !state.sharedLoading) loadNextSharedBatch();
  }
});

function startHeroRotation() {
  if (state.heroTimer) clearInterval(state.heroTimer);
  if (state.heroData.length <= 1) return;
  state.heroTimer = setInterval(() => {
    let next = (state.heroIndex + 1) % state.heroData.length; state.heroIndex = next;
    const slides = document.querySelectorAll('.hero-slide');
    if (slides.length) {
      slides.forEach((slide, i) => { if (i === next) slide.classList.add('is-active'); else slide.classList.remove('is-active'); });
      document.getElementById('heroSlides').style.transform = `translateX(-${next * 100}%)`;
    }
  }, 5000);
}

function buildCard(item, forceActor = false) {
  const isActor = forceActor || item.role_id; 
  const img = getImg(item.image);
  let b = '';
  if (!isActor) {
      if(item.type==1) b+=`<span class="badge badge-series">سریال</span>`;
      if(item.has_sub||item.is_sub) b+=`<span class="badge badge-sub">زیرنویس</span>`;
      if(item.has_dub||item.is_dubbed) b+=`<span class="badge badge-dub">دوبله</span>`;
  }
  const clickAction = isActor ? `openActor(${item.id}, '${escJS(item.name || item.title || '')}')` : `showDetail(${item.id})`;
  return `
    <div class="card" onclick="${clickAction}">
      <img class="card-poster" src="${esc(img)}" loading="lazy" onerror="this.src='${FALLBACK_IMG}'">
      <div class="card-badges">${b}</div>
      <div class="card-body">
        <div class="card-title">${esc(item.name||item.title||'بدون عنوان')}</div>
        ${(item.year && !isActor) ? `<div class="card-year">${esc(String(item.year))}</div>`:''}
      </div>
    </div>`;
}

function buildHero(slides) {
  if(!slides.length) { document.getElementById('heroSection').style.display='none'; return; }
  state.heroData = slides;
  document.getElementById('heroSlides').innerHTML = slides.map((s,i) => `
    <div class="hero-slide ${i===0?'is-active':''}" onclick="showDetail(${s.id})">
      <img class="hero-slide-bg" src="${esc(getImg(s.image))}" onerror="this.src='${FALLBACK_IMG}'">
      <div class="hero-gradient"></div>
      <div class="hero-content">
        <div class="hero-badge"><i class="fa-solid ${s.type==1?'fa-tv':'fa-film'}"></i> ${s.type==1?'سریال':'فیلم'}</div>
        <div class="hero-title">${esc(s.name||s.title)}</div>
        <button class="hero-cta"><i class="fa-solid fa-play"></i> تماشا</button>
      </div>
    </div>`).join('');
  startHeroRotation();
}

async function showDetail(id) {
  state.currentDetailId = id; 
  showView('detail'); window.scrollTo(0,0);
  document.getElementById('detailContent').innerHTML = `<?php echo buildDetailSkeleton(); ?>`;
  const data = await api({ action: 'details', id });
  if (data.error) { document.getElementById('detailContent').innerHTML = `<div class="empty-state">خطا</div>`; return; }
  
  const info = data.info || {}; const rep = data.report || {}; const age = data.age || {};
  const isSeries = info.type == 1; const isFav = state.favorites.some(f => f.id == id);
  const jsonItem = `{id:${id}, title:'${escJS(info.title||info.name)}', image:'${escJS(info.image)}', type:${info.type}}`;

  let html = `
    <button class="btn-back detail-back" onclick="goBackFromDetail()"><i class="fa-solid fa-arrow-right"></i> بازگشت</button>
    <div class="detail-hero"><img class="detail-hero-bg" src="${esc(getImg(info.image||info.banner))}" onerror="this.src='${FALLBACK_IMG}'"><div class="detail-hero-grad"></div></div>
    <div class="detail-layout">
      <div class="detail-poster"><img src="${esc(getImg(info.image))}" onerror="this.src='${FALLBACK_IMG}'"></div>
      <div class="detail-info">
        <div class="detail-title">${esc(info.title||info.name)}</div>
        <div class="detail-tags">
          ${age.age ? `<span class="tag" style="border-color:${esc(age.color)};color:${esc(age.color)}"><i class="fa-solid fa-shield-alt"></i> ${esc(age.age)}</span>` : ''}
          ${info.imdb_rate ? `<span class="tag tag-gold"><i class="fa-solid fa-star"></i> ${esc(String(info.imdb_rate))}</span>` : ''}
          ${info.duration ? `<span class="tag"><i class="fa-solid fa-clock"></i> ${esc(info.duration)}</span>` : ''}
          ${info.year ? `<span class="tag"><i class="fa-solid fa-calendar"></i> ${esc(String(info.year))}</span>` : ''}
        </div>
        
        <div style="display:flex; gap:10px; margin-bottom:24px; flex-wrap:wrap;">
          ${(!isSeries) ? `<button class="btn btn-primary" onclick="openQualities(${id}, '${escJS(info.title||info.name)}', -1, -1)"><i class="fa-solid fa-play"></i> پخش فیلم</button>` : ''}
          <button class="btn btn-sec" id="favBtn-${id}" onclick="toggleFavorite(${id}, '${escJS(info.title||info.name)}', '${escJS(info.image)}', ${info.type})">
            ${isFav ? `<i class="fa-solid fa-bookmark" style="color:var(--gold)"></i> ذخیره شده` : `<i class="fa-regular fa-bookmark"></i> ذخیره`}
          </button>
          <button class="btn btn-sec" onclick="openAddToPlaylistSheet({id:${id}, title:'${escJS(info.title||info.name)}', image:'${escJS(info.image)}', type:${info.type}})"><i class="fa-solid fa-list-check"></i> افزودن به لیست</button>
          <button class="btn btn-sec" onclick="shareItem('${escJS(info.title||info.name)}', ${id})"><i class="fa-solid fa-share-nodes"></i> اشتراک</button>
        </div>
        
        ${info.description_ai ? `<div class="detail-ai-plot"><div style="font-weight:800;margin-bottom:8px;font-size:14px;"><i class="fa-solid fa-robot"></i> خلاصه هوش مصنوعی</div>${esc(info.description_ai)}</div>` : ''}
        ${info.description ? `<div class="detail-plot">${esc(info.description)}</div>` : ''}
      </div>
    </div>
  `;

  if (rep.visit || rep.reaction_message) {
    html += `<div class="stat-box">
      ${rep.visit ? `<div class="stat-item"><div class="stat-val">${esc(rep.visit)}</div><div class="stat-lbl">بازدید</div></div>` : ''}
      ${rep.reaction_message ? `<div class="stat-item"><div class="stat-val">${esc(rep.reaction_message)}</div><div class="stat-lbl">رضایت</div></div>` : ''}
    </div>`;
  }

  if (data.movie_screenshot?.length > 0) {
    html += `<div class="section"><div class="section-header"><h2 class="section-title"><i class="fa-solid fa-images"></i> تصاویر</h2></div>
             <div class="shot-list">${data.movie_screenshot.map(s => `<div class="shot-card"><img src="${esc(getImg(s.image))}" onerror="this.src='${FALLBACK_IMG}'" loading="lazy"></div>`).join('')}</div></div>`;
  }

  if (data.actor?.length > 0) {
    html += `<div class="section"><div class="section-header"><h2 class="section-title"><i class="fa-solid fa-users"></i> عوامل</h2></div>
             <div class="cast-list">${data.actor.map(a => `<div class="cast-card" onclick="openActor(${a.id}, '${escJS(a.name)}')"><div class="cast-avatar"><img src="${esc(getImg(a.image))}" onerror="this.src='${FALLBACK_IMG}'"></div><div class="cast-name">${esc(a.name)}</div><div class="cast-role">${esc(a.role)}</div></div>`).join('')}</div></div>`;
  }

  if (data.more_detail?.length > 0) {
    html += `<div class="section"><div class="section-header"><h2 class="section-title"><i class="fa-solid fa-circle-info"></i> اطلاعات تکمیلی</h2></div>
             <div class="meta-grid">${data.more_detail.map(m => `<div class="meta-item"><div class="meta-label">${esc(m.title)}</div><div class="meta-val">${esc(m.value)}</div></div>`).join('')}</div></div>`;
  }

  if (isSeries) {
    const sCount = info.season_count || (data.link ? data.link.length : 1);
    html += `<div class="section">
      <div class="section-header"><h2 class="section-title"><i class="fa-solid fa-list"></i> فصل‌ها و قسمت‌ها</h2></div>
      <div class="seasons-tabs">${Array.from({length: sCount}, (_,i)=>i+1).map(s=>`<button class="season-tab ${s===1?'active':''}" id="stab-${s}" onclick="loadSeason(${id},${s}, '${escJS(info.title)}')">فصل ${s}</button>`).join('')}</div>
      <div id="episodesContainer"><div class="episodes-list">${SKEL_EPISODES_HTML}</div></div>
    </div>`;
  }

  document.getElementById('detailContent').innerHTML = html;
  if (isSeries) loadSeason(id, 1, info.title);
}

// تابع اصلاح شده و بدون تکرار (دانلود یکباره با قابلیت پردازش موازی و سریع)
async function loadSeason(id, season, title) {
  document.querySelectorAll('.season-tab').forEach(t=>t.classList.remove('active'));
  document.getElementById('stab-'+season)?.classList.add('active');
  const cont = document.getElementById('episodesContainer');
  cont.innerHTML = `<div class="episodes-list">${SKEL_EPISODES_HTML}</div>`;
  
  const data = await api({ action: 'episodes', id, season });
  if (data.error || !data.data) { cont.innerHTML = '<div class="empty-state">قسمتی یافت نشد</div>'; return; }
  
  // ذخیره در استیت برای ساخت فایل متنی
  state.currentSeasonEpisodes = data.data;
  
  // ساخت دکمه دانلود گروهی
  let bulkDownloadBtn = `
  <div style="margin-bottom: 16px; display: flex; justify-content: flex-end;">
    <button id="btnBulkDownload" class="btn btn-primary" onclick="downloadSeasonBulkTXT(${id}, ${season}, '${escJS(title)}')">
      <i class="fa-solid fa-file-arrow-down"></i> دانلود همه قسمت‌ها (TXT)
    </button>
  </div>`;

  cont.innerHTML = bulkDownloadBtn + `<div class="episodes-list">${data.data.map(ep => `<div class="episode-row" onclick="openQualities(${id}, '${escJS(title)}', ${season}, ${ep.episode})"><div class="ep-num">${ep.episode}</div><div class="ep-title">${esc(ep.title || `قسمت ${ep.episode}`)}</div><i class="fa-solid fa-circle-play ep-play"></i></div>`).join('')}</div>`;
}

// تابع کاملاً بهینه شده برای دانلود همه قسمت‌ها در کسری از ثانیه
async function downloadSeasonBulkTXT(id, season, title) {
  if (!state.currentSeasonEpisodes || state.currentSeasonEpisodes.length === 0) {
    showToast('قسمتی برای دانلود یافت نشد');
    return;
  }

  showToast('در حال جمع‌آوری لینک‌ها... لطفاً چند لحظه صبر کنید');
  
  const btn = document.getElementById('btnBulkDownload');
  if (btn) {
    btn.disabled = true;
    btn.style.opacity = '0.7';
    btn.innerHTML = '<div class="spinner" style="width:14px;height:14px;border-width:2px;margin-left:8px;"></div> در حال پردازش...';
  }

  let text = `لینک‌های دانلود سریال ${title} - فصل ${season}\n\n`;

  try {
    // برای جلوگیری از فشار به سرور درخواست‌ها در دسته‌های 10 تایی ارسال می‌شوند (خیلی سریعتر از روش قبلی)
    const chunkArray = (arr, size) => arr.length ? [arr.slice(0, size), ...chunkArray(arr.slice(size), size)] : [];
    const chunks = chunkArray(state.currentSeasonEpisodes, 10);
    const results = [];
    
    for (const chunk of chunks) {
      const promises = chunk.map(ep => 
        api({ action: 'qualities', id, season, episode: ep.episode })
          .then(data => ({ epNum: ep.episode, data }))
          .catch(() => ({ epNum: ep.episode, error: true }))
      );
      const chunkResults = await Promise.all(promises);
      results.push(...chunkResults);
    }

    // مرتب‌سازی به ترتیب شماره قسمت‌ها
    results.sort((a, b) => a.epNum - b.epNum);

    for (const res of results) {
      if (!res.error && res.data && !res.data.error && res.data.qualities && res.data.qualities.length > 0) {
        const q = res.data.qualities[0]; 
        let dlUrl = `${API}?action=download&id=${id}&quality_id=${q.id}&season=${season}&episode=${res.epNum}`;
        // تبدیل به مسیر کامل و دقیق بدون باگ
        let fullUrl = new URL(dlUrl, window.location.href).href; 
        
        text += `قسمت ${res.epNum} (${q.title || q.type}):\n${fullUrl}\n\n`;
      }
    }

    const blob = new Blob([text], { type: "text/plain;charset=utf-8" });
    const a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    a.download = `Season_${season}_Links.txt`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    
    showToast('فایل با موفقیت دانلود شد!');
  } catch (err) {
    console.error(err);
    showToast('خطا در ایجاد فایل دانلود');
  } finally {
    // بازگردانی دکمه به حالت اول
    if (btn) {
      btn.disabled = false;
      btn.style.opacity = '1';
      btn.innerHTML = '<i class="fa-solid fa-file-arrow-down"></i> دانلود همه قسمت‌ها (TXT)';
    }
  }
}

function toggleFavorite(id, title, image, type) {
  let idx = state.favorites.findIndex(f => f.id == id);
  if(idx > -1) { state.favorites.splice(idx, 1); showToast('از علاقه‌مندی‌ها حذف شد'); } 
  else { state.favorites.push({ id, title, image, type }); showToast('به علاقه‌مندی‌ها اضافه شد'); }
  localStorage.setItem('mili_favs', JSON.stringify(state.favorites));
  updateFavBtnUI(id); if(state.currentView === 'library') renderFavorites();
}
function updateFavBtnUI(id) {
  const btn = document.getElementById('favBtn-' + id); if(!btn) return;
  const isFav = state.favorites.some(f => f.id == id);
  btn.innerHTML = isFav ? `<i class="fa-solid fa-bookmark" style="color:var(--gold)"></i> ذخیره شده` : `<i class="fa-regular fa-bookmark"></i> ذخیره`;
}

function shareItem(title, id) {
  const shareUrl = window.location.origin + window.location.pathname + '?id=' + id;
  if (navigator.share) { navigator.share({ title: title, url: shareUrl }).catch(console.error); } 
  else { navigator.clipboard.writeText(shareUrl); showToast('لینک کپی شد'); }
}

async function openQualities(id, title, season, episode) {
  const data = await api({ action: 'qualities', id, season: season===-1?undefined:season, episode: episode===-1?undefined:episode });
  if (data.error || !data.qualities?.length) { showToast('کیفیتی یافت نشد'); return; }
  state.allQualities = data.qualities; state.currentQualityData = { id, title, season, episode };
  playStream(data.qualities[0].id);
}

function buildSubtitlesMenu(tracks) {
  const list = document.getElementById('subTracksList');
  let html = `<button class="sub-track-item active" onclick="setSubtitle(-1)">خاموش</button>`;
  tracks.forEach((t, i) => {
      html += `<button class="sub-track-item" onclick="setSubtitle(${i})">${t.label}</button>`;
  });
  list.innerHTML = html;
  setSubtitle(-1); 

  const subBtn = document.getElementById('btnSubtitle');
  if(tracks.length === 0) {
      subBtn.style.opacity = '0.3';
      subBtn.style.pointerEvents = 'none';
  } else {
      subBtn.style.opacity = '1';
      subBtn.style.pointerEvents = 'auto';
  }
}

function setSubtitle(index) {
  const player = document.getElementById('mainPlayer');
  for(let i = 0; i < player.textTracks.length; i++) {
      player.textTracks[i].mode = 'hidden';
  }
  if(index >= 0 && player.textTracks[index]) {
      player.textTracks[index].mode = 'showing';
  }
  
  document.querySelectorAll('.sub-track-item').forEach((el, i) => {
      if(i === index + 1) el.classList.add('active');
      else el.classList.remove('active');
  });
  document.getElementById('subtitlesMenu').style.display = 'none';
}

async function playStream(qualityId) {
  const { id, title, season, episode } = state.currentQualityData;
  showView('player'); window.scrollTo(0,0); state.playerFromId = id;
  document.getElementById('playerTitle').textContent = title;
  document.getElementById('playerSubtitle').textContent = season !== -1 ? `فصل ${season} قسمت ${episode}` : '';
  
  const player = document.getElementById('mainPlayer'); 
  player.pause();
  closeAllSheets();
  
  const data = await api({ action: 'stream', id, quality_id: qualityId, season: season===-1?undefined:season, episode: episode===-1?undefined:episode });
  if (data.error) { showToast(data.error); return; }
  
  player.src = data.url;
  
  while (player.firstChild) {
      player.removeChild(player.firstChild);
  }
  
  let tracksData = [];
  
  if(data.vtt) {
      const proxyVtt = `${API}?action=proxy_sub&url=${encodeURIComponent(data.vtt)}`;
      tracksData.push({kind:'subtitles', src: proxyVtt, srclang:'fa', label:'زیرنویس VTT'});
  }
  if(data.srt) {
      const proxySrt = `${API}?action=proxy_sub&url=${encodeURIComponent(data.srt)}`;
      tracksData.push({kind:'subtitles', src: proxySrt, srclang:'fa', label:'زیرنویس SRT'});
  }
  
  tracksData.forEach((t) => {
      const track = document.createElement('track');
      track.kind = t.kind;
      track.src = t.src;
      track.srclang = t.srclang;
      track.label = t.label;
      player.appendChild(track);
  });
  
  buildSubtitlesMenu(tracksData);
  player.play().catch(e => console.log('Autoplay blocked', e));
  
  document.getElementById('qualityList').innerHTML = state.allQualities.map(q => `<div class="sheet-item" onclick="playStream(${q.id})"><div class="sheet-item-left"><i class="fa-solid fa-circle-play sheet-item-ico"></i><div><div class="sheet-item-title">${esc(q.title || q.type)}</div><div class="sheet-item-sub">${q.type.toLowerCase().includes('dub')?'دوبله':'زیرنویس'}</div></div></div><div class="sheet-item-right">${esc(q.size||'')}</div></div>`).join('');
    
  let dHtml = '';
  if(data.srt) dHtml += `<div class="sheet-item"><div class="sheet-item-left"><i class="fa-solid fa-closed-captioning sheet-item-ico"></i><div class="sheet-item-title">زیرنویس SRT</div></div><a class="btn btn-sec" href="${data.srt}" target="_blank">دانلود</a></div>`;
  if(data.vtt) dHtml += `<div class="sheet-item"><div class="sheet-item-left"><i class="fa-solid fa-closed-captioning sheet-item-ico"></i><div class="sheet-item-title">زیرنویس VTT</div></div><a class="btn btn-sec" href="${data.vtt}" target="_blank">دانلود</a></div>`;
  
  dHtml += state.allQualities.map(q => {
      let dlUrl = `${API}?action=download&id=${id}&quality_id=${q.id}${season!==-1?`&season=${season}&episode=${episode}`:''}`;
      let fullUrl = new URL(dlUrl, window.location.href).href; 
      let pureUrl = fullUrl.replace(/^https?:\/\//i, ''); 
      
      let admIntent = `intent://${pureUrl}#Intent;package=com.dv.adm;scheme=https;S.title=${encodeURIComponent(title)};end;`;
      let mxIntent = `intent://${pureUrl}#Intent;package=com.mxtech.videoplayer.ad;scheme=https;S.title=${encodeURIComponent(title)};end;`;
      let vlcIntent = `intent://${pureUrl}#Intent;package=org.videolan.vlc;scheme=https;S.title=${encodeURIComponent(title)};end;`;
      
      return `
      <div class="sheet-item" style="flex-wrap:wrap; gap:10px; cursor:default;">
        <div class="sheet-item-left" style="width:100%">
          <i class="fa-solid fa-video sheet-item-ico"></i>
          <div><div class="sheet-item-title">${esc(q.title || q.type)} - ${esc(q.size||'')}</div><div class="sheet-item-sub">${q.type.toLowerCase().includes('dub')?'دوبله':'زیرنویس'}</div></div>
        </div>
        <div style="display:flex; gap:6px; width:100%; flex-wrap:wrap;">
          <a class="btn btn-primary" style="flex:1; min-width:45%; padding:8px;" href="${dlUrl}" target="_blank"><i class="fa-solid fa-download"></i> دانلود</a>
          <a class="btn btn-sec" style="flex:1; min-width:45%; padding:8px; border-color:#32a852; color:#32a852" href="${admIntent}"><i class="fa-solid fa-bolt"></i> ارسال ADM</a>
          <a class="btn btn-sec" style="flex:1; min-width:45%; padding:8px; border-color:#2196f3; color:#2196f3" href="${mxIntent}"><i class="fa-solid fa-play"></i> پخش MX Player</a>
          <a class="btn btn-sec" style="flex:1; min-width:45%; padding:8px; border-color:#ff9800; color:#ff9800" href="${vlcIntent}"><i class="fa-solid fa-play"></i> پخش VLC</a>
        </div>
      </div>`;
  }).join('');
  
  document.getElementById('downloadList').innerHTML = dHtml;
}

// تابع بروز شده دانلود لینک‌های خارجی پلیر
function downloadAllLinksTXT() {
  const { id, season, episode } = state.currentQualityData;
  let text = "لینک‌های دانلود - " + document.getElementById('playerTitle').textContent + "\n\n";
  state.allQualities.forEach(q => {
    let dlUrl = `${API}?action=download&id=${id}&quality_id=${q.id}${season!==-1?`&season=${season}&episode=${episode}`:''}`;
    let fullUrl = new URL(dlUrl, window.location.href).href; 
    text += `${q.title || q.type}:\n${fullUrl}\n\n`;
  });
  const blob = new Blob([text], { type: "text/plain;charset=utf-8" });
  const a = document.createElement("a"); a.href = URL.createObjectURL(blob); a.download = "miligram_links.txt";
  document.body.appendChild(a); a.click(); document.body.removeChild(a); showToast('فایل TXT در حال دانلود است');
}


function toggleFullscreen() {
  const wrapper = document.getElementById('customPlayerWrapper');
  const video = document.getElementById('mainPlayer');

  if (!(document.fullscreenElement || document.webkitFullscreenElement)) { 
      if (wrapper.requestFullscreen) wrapper.requestFullscreen(); 
      else if (wrapper.webkitRequestFullscreen) wrapper.webkitRequestFullscreen(); 
      else if (video.webkitEnterFullscreen) video.webkitEnterFullscreen(); 
  } 
  else { 
      if (document.exitFullscreen) document.exitFullscreen();
      else if (document.webkitExitFullscreen) document.webkitExitFullscreen();
  }
}
function goBackFromDetail() { 
  if (state.currentGenreId && state.currentView === 'detail') showView('genre');
  else if (state.catId && state.currentView === 'detail') showView('category');
  else if (state.currentSearchQuery && state.currentSearchQuery!=='+') showView('search');
  else showHome();
}
function goBackFromPlayer() { document.getElementById('mainPlayer').src=''; if(state.playerFromId) showDetail(state.playerFromId); else showHome(); }

// INIT APP
function handleSharedLink() {
  const urlParams = new URLSearchParams(window.location.search);
  const sharedId = urlParams.get('id');
  const sharedPlaylist = urlParams.get('p');
  
  if (sharedPlaylist) {
     setTimeout(() => {
        openPlaylistDetail(sharedPlaylist, true);
        window.history.replaceState({}, document.title, window.location.origin + window.location.pathname);
     }, 100);
  } else if (sharedId && !isNaN(sharedId)) {
    setTimeout(() => {
      showDetail(parseInt(sharedId));
      window.history.replaceState({}, document.title, window.location.origin + window.location.pathname);
    }, 100);
  } else {
    loadVitrin();
  }
}

document.addEventListener("DOMContentLoaded", () => {
    handleSharedLink();
    
    // Custom Player Logic setup
    const video = document.getElementById('mainPlayer');
    const overlay = document.getElementById('playerOverlay');
    const btnPlayPause = document.getElementById('btnPlayPause');
    const centerPlayPause = document.getElementById('centerPlayPause');
    const progressBar = document.getElementById('progressBar');
    const progressThumb = document.getElementById('progressThumb');
    const progressContainer = document.getElementById('progressContainer');
    const timeDisplay = document.getElementById('timeDisplay');
    const wrapper = document.getElementById('customPlayerWrapper');
    const subtitlesMenu = document.getElementById('subtitlesMenu');

    let hideControlsTimeout;
    function showControls() {
        overlay.classList.remove('hidden');
        wrapper.style.cursor = 'default';
        clearTimeout(hideControlsTimeout);
        hideControlsTimeout = setTimeout(() => {
            if(!video.paused) {
                overlay.classList.add('hidden');
                wrapper.style.cursor = 'none';
                subtitlesMenu.style.display = 'none';
            }
        }, 3500);
    }

    wrapper.addEventListener('mousemove', showControls);
    wrapper.addEventListener('touchstart', showControls);
    
    function togglePlay() {
        if(video.paused) video.play();
        else video.pause();
    }

    btnPlayPause.addEventListener('click', (e) => { e.stopPropagation(); togglePlay(); });
    centerPlayPause.addEventListener('click', (e) => { e.stopPropagation(); togglePlay(); });
    overlay.addEventListener('click', (e) => {
        if(e.target === overlay || e.target === wrapper) togglePlay(); 
        subtitlesMenu.style.display = 'none';
    });

    video.addEventListener('play', () => {
        btnPlayPause.innerHTML = '<i class="fa-solid fa-pause"></i>';
        centerPlayPause.innerHTML = '<i class="fa-solid fa-pause"></i>';
        centerPlayPause.style.opacity = '0';
        showControls();
    });
    
    video.addEventListener('pause', () => {
        btnPlayPause.innerHTML = '<i class="fa-solid fa-play"></i>';
        centerPlayPause.innerHTML = '<i class="fa-solid fa-play"></i>';
        centerPlayPause.style.opacity = '1';
        showControls();
    });

    function formatTime(seconds) {
        if(isNaN(seconds)) return "00:00";
        const h = Math.floor(seconds / 3600);
        const m = Math.floor((seconds % 3600) / 60);
        const s = Math.floor(seconds % 60);
        if(h > 0) return `${h}:${m<10?'0'+m:m}:${s<10?'0'+s:s}`;
        return `${m<10?'0'+m:m}:${s<10?'0'+s:s}`;
    }

    video.addEventListener('timeupdate', () => {
        const current = video.currentTime;
        const duration = video.duration;
        if(!duration) return;
        const percent = (current / duration) * 100;
        progressBar.style.width = percent + '%';
        timeDisplay.textContent = formatTime(current) + ' / ' + formatTime(duration);
    });

    video.addEventListener('loadedmetadata', () => {
        timeDisplay.textContent = '00:00 / ' + formatTime(video.duration);
    });

    progressContainer.addEventListener('click', (e) => {
        e.stopPropagation();
        const rect = progressContainer.getBoundingClientRect();
        let pos = (e.clientX - rect.left) / rect.width;
        if(pos < 0) pos = 0; if(pos > 1) pos = 1;
        video.currentTime = pos * video.duration;
    });

    document.getElementById('btnSubtitle').addEventListener('click', (e) => {
        e.stopPropagation();
        subtitlesMenu.style.display = subtitlesMenu.style.display === 'flex' ? 'none' : 'flex';
    });
});
</script>
</body>
</html>