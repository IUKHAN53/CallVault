<?php
// Visitor + download counting for callvault.iukhan.tech.
// Privacy: only a salted SHA-256 of (IP, user agent) is stored, never the IP itself.

const APP_VERSION = 'v2.0.0';
const DATA_DIR = __DIR__ . '/../data';
const PUBLIC_DIR = __DIR__ . '/../public';
const BOT_UA = '/bot|crawl|spider|slurp|preview|facebookexternalhit|whatsapp|telegram|curl|wget|python|headless|monitor|uptime/i';

function db(): PDO {
    static $pdo = null;
    if ($pdo) return $pdo;
    $pdo = new PDO('sqlite:' . DATA_DIR . '/stats.sqlite', null, null, [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]);
    $pdo->exec('PRAGMA journal_mode=WAL; PRAGMA busy_timeout=3000;');
    $pdo->exec('CREATE TABLE IF NOT EXISTS visitors (hash TEXT PRIMARY KEY, first_seen INTEGER NOT NULL)');
    $pdo->exec('CREATE TABLE IF NOT EXISTS downloads (id INTEGER PRIMARY KEY, hash TEXT NOT NULL, at INTEGER NOT NULL)');
    $pdo->exec('CREATE INDEX IF NOT EXISTS downloads_hash_at ON downloads(hash, at)');
    return $pdo;
}

function visitor_hash(): string {
    $saltFile = DATA_DIR . '/salt';
    if (!is_file($saltFile)) file_put_contents($saltFile, bin2hex(random_bytes(32)), LOCK_EX);
    $salt = trim(file_get_contents($saltFile));
    return hash('sha256', $salt . '|' . ($_SERVER['REMOTE_ADDR'] ?? '') . '|' . ($_SERVER['HTTP_USER_AGENT'] ?? ''));
}

function is_bot(): bool {
    $ua = $_SERVER['HTTP_USER_AGENT'] ?? '';
    return $ua === '' || preg_match(BOT_UA, $ua) === 1;
}

/** Counts this visitor once, ever. Never breaks the page if the DB is unavailable. */
function record_visit(): void {
    if (is_bot()) return;
    try {
        db()->prepare('INSERT OR IGNORE INTO visitors (hash, first_seen) VALUES (?, ?)')->execute([visitor_hash(), time()]);
    } catch (Throwable $e) { error_log('callvault record_visit: ' . $e->getMessage()); }
}

/** Counts a download, ignoring repeat clicks from the same visitor within 10 minutes. */
function record_download(): void {
    if (is_bot()) return;
    try {
        $h = visitor_hash();
        $now = time();
        $recent = db()->prepare('SELECT 1 FROM downloads WHERE hash = ? AND at > ? LIMIT 1');
        $recent->execute([$h, $now - 600]);
        if (!$recent->fetchColumn()) db()->prepare('INSERT INTO downloads (hash, at) VALUES (?, ?)')->execute([$h, $now]);
        // A download implies a visit (e.g. someone used a shared /download link directly).
        db()->prepare('INSERT OR IGNORE INTO visitors (hash, first_seen) VALUES (?, ?)')->execute([$h, $now]);
    } catch (Throwable $e) { error_log('callvault record_download: ' . $e->getMessage()); }
}

function stats(): array {
    try {
        return [
            'visitors' => (int) db()->query('SELECT COUNT(*) FROM visitors')->fetchColumn(),
            'downloads' => (int) db()->query('SELECT COUNT(*) FROM downloads')->fetchColumn(),
        ];
    } catch (Throwable $e) {
        error_log('callvault stats: ' . $e->getMessage());
        return ['visitors' => 0, 'downloads' => 0];
    }
}

function newest(string $glob): ?string {
    $files = glob($glob) ?: [];
    usort($files, fn($a, $b) => filemtime($b) <=> filemtime($a));
    return $files[0] ?? null;
}

function latest_apk(): ?string { return newest(PUBLIC_DIR . '/files/*.apk'); }
function latest_source(): ?string { return newest(PUBLIC_DIR . '/source/*.tar.gz'); }

/** Decorative two-track waveform: "you" above the line, "them" below, taking turns like a real call. */
function waveform_svg(): string {
    $bars = 56; $w = 600; $h = 230; $mid = $h / 2; $gap = 3;
    $bw = ($w - 60) / $bars - $gap;
    mt_srand(7);
    $you = $them = '';
    for ($i = 0; $i < $bars; $i++) {
        $phase = intdiv($i, 9) % 2;            // speakers alternate every few bars
        $edge = min($i % 9, 8 - $i % 9) / 4;   // softer at turn boundaries
        $talk = 0.35 + 0.65 * min(1, $edge);
        $loud = (mt_rand(25, 100) / 100) * $talk * ($mid - 18);
        $quiet = mt_rand(4, 14) / 100 * ($mid - 18);
        [$a, $b] = $phase === 0 ? [$loud, $quiet] : [$quiet, $loud];
        $x = 60 + $i * ($bw + $gap);
        $d = round($i * 0.012, 3);
        $you .= sprintf('<rect x="%.1f" y="%.1f" width="%.1f" height="%.1f" rx="2" style="animation-delay:%ss"/>', $x, $mid - 3 - $a, $bw, $a, $d);
        $them .= sprintf('<rect x="%.1f" y="%.1f" width="%.1f" height="%.1f" rx="2" style="animation-delay:%ss"/>', $x, $mid + 3, $bw, $b, $d);
    }
    return '<svg viewBox="0 0 ' . $w . ' ' . $h . '" role="img" aria-hidden="true">'
        . '<text class="lbl" x="0" y="' . ($mid - 10) . '">You</text>'
        . '<text class="lbl" x="0" y="' . ($mid + 22) . '">Them</text>'
        . '<line class="mid" x1="60" x2="' . $w . '" y1="' . $mid . '" y2="' . $mid . '" stroke-width="1"/>'
        . '<g class="you">' . $you . '</g><g class="them">' . $them . '</g></svg>';
}
