<?php
require __DIR__ . '/../lib/stats.php';

$apk = latest_apk();
if (!$apk) {
    http_response_code(404);
    header('Content-Type: text/plain; charset=utf-8');
    echo "The download isn't available right now. Try again in a few minutes.";
    exit;
}
record_download();
header('Cache-Control: no-store');
header('Location: /files/' . rawurlencode(basename($apk)), true, 302);
