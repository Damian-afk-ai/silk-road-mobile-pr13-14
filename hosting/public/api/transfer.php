<?php
require __DIR__ . '/common.php';
method('POST');
// Bounded, non-persistent transfer measurement. This is not a database write.
$length = (int)($_SERVER['CONTENT_LENGTH'] ?? 0);
if ($length > 262144) reply(413, ['error' => ['code' => 'PAYLOAD_TOO_LARGE', 'message' => 'Máximo 256 KiB.']]);
if (strtolower(trim(explode(';', $_SERVER['CONTENT_TYPE'] ?? '')[0])) !== 'application/octet-stream') {
    reply(415, ['error' => ['code' => 'UNSUPPORTED_MEDIA_TYPE', 'message' => 'Usa application/octet-stream.']]);
}
$body = file_get_contents('php://input', false, null, 0, 262145);
if (strlen($body) > 262144) reply(413, ['error' => ['code' => 'PAYLOAD_TOO_LARGE', 'message' => 'Máximo 256 KiB.']]);
reply(200, ['receivedBytes' => strlen($body), 'sha256' => hash('sha256', $body), 'persisted' => false]);
