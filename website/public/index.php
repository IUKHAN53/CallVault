<?php
require __DIR__ . '/../lib/stats.php';

record_visit();
$s = stats();
$apk = latest_apk();
$apkSize = $apk ? round(filesize($apk) / 1048576, 1) : null;
$fmt = fn(int $n) => number_format($n);
?><!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>CallVault — call recorder for Android</title>
<meta name="description" content="Record phone and WhatsApp calls on Android with both voices clear. No root. Free and open source.">
<meta property="og:title" content="CallVault — call recorder for Android">
<meta property="og:description" content="Record phone and WhatsApp calls on Android with both voices clear. No root needed.">
<meta property="og:url" content="https://callvault.iukhan.tech">
<meta name="theme-color" content="#0E5E4F">
<link rel="icon" href="data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 32 32'><rect width='32' height='32' rx='8' fill='%230E5E4F'/><circle cx='16' cy='16' r='5' fill='%23F2B33D'/></svg>">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Bricolage+Grotesque:opsz,wght@12..96,500;12..96,700&family=Instrument+Sans:wght@400;500;600&family=Noto+Nastaliq+Urdu:wght@500&display=swap" rel="stylesheet">
<style>
  :root {
    --bg: #EEF2F0;
    --surface: #FFFFFF;
    --ink: #10282A;
    --muted: #556A6C;
    --line: #CFDAD6;
    --brand: #0E5E4F;
    --brand-ink: #FFFFFF;
    --rec: #F2B33D;
    --them: #4F8F86;
  }
  @media (prefers-color-scheme: dark) {
    :root {
      --bg: #0F1D1E; --surface: #162829; --ink: #E4EEEB; --muted: #93A9A6;
      --line: #2A4042; --brand: #5CC4AE; --brand-ink: #062A23; --them: #3E7C74;
    }
  }
  * { box-sizing: border-box; }
  html { -webkit-text-size-adjust: 100%; }
  body {
    margin: 0; background: var(--bg); color: var(--ink);
    font: 400 17px/1.6 "Instrument Sans", system-ui, sans-serif;
  }
  a { color: var(--brand); }
  a:focus-visible, .dl:focus-visible { outline: 3px solid var(--rec); outline-offset: 3px; border-radius: 6px; }
  .wrap { max-width: 1040px; margin: 0 auto; padding: 0 24px; }

  header.top { display: flex; align-items: center; gap: 10px; padding: 22px 0; font-weight: 600; }
  .dot { width: 12px; height: 12px; border-radius: 50%; background: var(--rec); box-shadow: 0 0 0 4px color-mix(in srgb, var(--rec) 30%, transparent); }
  header.top a { margin-left: auto; font-weight: 500; text-decoration: none; color: var(--muted); }
  header.top a:hover { color: var(--ink); }

  .hero { display: grid; grid-template-columns: 1.05fr 1fr; gap: 48px; align-items: center; padding: 40px 0 72px; }
  h1 {
    font-family: "Bricolage Grotesque", sans-serif; font-weight: 700;
    font-size: clamp(44px, 7vw, 80px); line-height: 0.98; letter-spacing: -0.03em; margin: 0 0 18px;
  }
  .lede { font-size: 20px; color: var(--muted); max-width: 30em; margin: 0 0 8px; }
  .urdu { font-family: "Noto Nastaliq Urdu", serif; font-size: 22px; line-height: 2.1; color: var(--ink); margin: 0 0 26px; direction: rtl; text-align: left; unicode-bidi: plaintext; }

  .dl {
    display: inline-flex; align-items: center; gap: 12px; padding: 16px 26px; border-radius: 14px;
    background: var(--brand); color: var(--brand-ink); text-decoration: none; font-weight: 600; font-size: 18px;
    transition: transform .15s ease, filter .15s ease;
  }
  .dl:hover { filter: brightness(1.08); }
  .dl:active { transform: translateY(1px); }
  .dl svg { width: 22px; height: 22px; }
  .dl small { font-weight: 500; opacity: .8; font-size: 14px; }
  .counts { margin: 16px 0 0; color: var(--muted); font-size: 15px; }
  .counts b { color: var(--ink); font-variant-numeric: tabular-nums; }

  /* Hero waveform: you (top) and them (bottom) on one call */
  .wave { background: var(--surface); border: 1px solid var(--line); border-radius: 22px; padding: 22px 22px 16px; }
  .wave-head { display: flex; justify-content: space-between; font-size: 14px; color: var(--muted); margin-bottom: 8px; }
  .wave-head span:first-child::before { content: ""; display: inline-block; width: 8px; height: 8px; border-radius: 50%; background: var(--rec); margin-right: 8px; vertical-align: 1px; }
  .wave svg { width: 100%; height: auto; display: block; }
  .wave .you rect { fill: var(--brand); }
  .wave .them rect { fill: var(--them); }
  .wave .mid { stroke: var(--line); }
  .wave .lbl { font-size: 12px; fill: var(--muted); font-family: "Instrument Sans", sans-serif; }
  .wave rect { transform-box: fill-box; transform-origin: center bottom; animation: rise .6s cubic-bezier(.2,.8,.2,1) both; }
  .wave .them rect { transform-origin: center top; }
  @keyframes rise { from { transform: scaleY(0); } }
  @media (prefers-reduced-motion: reduce) { .wave rect { animation: none; } }

  section { padding: 56px 0; border-top: 1px solid var(--line); }
  h2 { font-family: "Bricolage Grotesque", sans-serif; font-weight: 700; font-size: 34px; letter-spacing: -0.02em; margin: 0 0 24px; }
  .cols { display: grid; grid-template-columns: 1fr 1fr; gap: 56px; }

  ol.steps { list-style: none; counter-reset: s; padding: 0; margin: 0; max-width: 36em; }
  ol.steps li { counter-increment: s; position: relative; padding: 0 0 26px 56px; }
  ol.steps li::before {
    content: counter(s); position: absolute; left: 0; top: -2px; width: 36px; height: 36px; border-radius: 50%;
    display: grid; place-items: center; background: var(--surface); border: 1px solid var(--line);
    font-family: "Bricolage Grotesque", sans-serif; font-weight: 700;
  }
  ol.steps li:not(:last-child)::after { content: ""; position: absolute; left: 17px; top: 38px; bottom: 4px; width: 2px; background: var(--line); }
  ol.steps h3 { margin: 0 0 4px; font-size: 18px; }
  ol.steps p { margin: 0; color: var(--muted); }

  ul.plain { padding: 0; margin: 0; list-style: none; }
  ul.plain li { padding: 12px 0; border-bottom: 1px solid var(--line); }
  ul.plain li:last-child { border-bottom: 0; }
  ul.plain strong { display: block; }
  ul.plain span { color: var(--muted); }

  footer { padding: 40px 0 56px; border-top: 1px solid var(--line); color: var(--muted); font-size: 15px; }
  footer p { margin: 0 0 8px; max-width: 60em; }

  @media (max-width: 820px) {
    .hero, .cols { grid-template-columns: 1fr; gap: 36px; }
    .hero { padding-top: 16px; }
  }
</style>
</head>
<body>
<div class="wrap">
  <header class="top"><span class="dot" aria-hidden="true"></span>CallVault<a href="https://iukhan.tech/work/callvault">About the project</a></header>

  <main>
    <div class="hero">
      <div>
        <h1>Every call, both voices.</h1>
        <p class="lede">CallVault records your phone and WhatsApp calls on Android. Your voice and theirs come out clear, with no root and nothing to tap during the call.</p>
        <p class="urdu" lang="ur">ہر کال، دونوں آوازیں صاف</p>
        <?php if ($apk): ?>
          <a class="dl" href="/download">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 3v12m0 0-5-5m5 5 5-5M4 21h16"/></svg>
            Download APK <small><?= htmlspecialchars(APP_VERSION) ?> · <?= $apkSize ?> MB</small>
          </a>
        <?php else: ?>
          <p>The download is being prepared. Check back shortly.</p>
        <?php endif; ?>
        <p class="counts"><b><?= $fmt($s['downloads']) ?></b> downloads, <b><?= $fmt($s['visitors']) ?></b> visitors</p>
      </div>

      <figure class="wave" aria-label="Illustration of a recorded call with your voice and the other person's voice on separate tracks">
        <div class="wave-head"><span>Recording</span><span>04:12</span></div>
        <?= waveform_svg() ?>
      </figure>
    </div>

    <section id="setup">
      <div class="cols">
        <div>
          <h2>Set it up once</h2>
          <ol class="steps">
            <li><h3>Install CallVault</h3><p>Download the APK above and open it. Allow installs from your browser when Android asks.</p></li>
            <li><h3>Install Shizuku</h3><p>Get <a href="https://github.com/thedjchi/Shizuku/releases">Shizuku</a> (the thedjchi build). It gives CallVault the access it needs to hear the call itself instead of the speaker.</p></li>
            <li><h3>Start Shizuku</h3><p>Turn on Developer options and Wireless debugging, then start Shizuku from its app. Turn on its start-on-boot option so it survives restarts.</p></li>
            <li><h3>Open CallVault</h3><p>Allow Shizuku, pick a folder for recordings, and you're done. Calls record automatically from now on.</p></li>
          </ol>
        </div>
        <div>
          <h2>Good to know</h2>
          <ul class="plain">
            <li><strong>Android 11 or newer</strong><span>Tested on a Galaxy S26 Ultra running Android 17.</span></li>
            <li><strong>Recordings stay on your phone</strong><span>The app has no internet permission. Files are saved as small Opus audio in the folder you choose.</span></li>
            <li><strong>Built-in player and manager</strong><span>Search by name or number, play at up to 2x, and share or delete recordings. Calls with the same person are grouped.</span></li>
            <li><strong>Check your local law</strong><span>Some places require the other person's consent before you record a call.</span></li>
          </ul>
        </div>
      </div>
    </section>
  </main>

  <footer>
    <p>CallVault is a fork of <a href="https://github.com/kitsumed/ShizuCallRecorder">ShizuCallRecorder</a> by kitsumed, licensed under GPL-3.0. <a href="/source/<?= htmlspecialchars(basename(latest_source() ?? '')) ?>">Download the source code</a>.</p>
    <p>Made by <a href="https://iukhan.tech">Irfan Ullah Khan</a>.</p>
  </footer>
</div>
</body>
</html>
