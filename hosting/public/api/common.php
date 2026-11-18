<?php
declare(strict_types=1);
ini_set('display_errors', '0');
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');
header('X-Content-Type-Options: nosniff');
function reply(int $status, array $data): void {
    http_response_code($status);
    echo json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR);
    exit;
}
function method(string $expected): void {
    if ($_SERVER['REQUEST_METHOD'] !== $expected) {
        header('Allow: ' . $expected);
        reply(405, ['error' => ['code' => 'METHOD_NOT_ALLOWED', 'message' => 'Método no permitido.']]);
    }
}
set_exception_handler(function(Throwable $e): void {
    error_log('SilkRoad API: ' . get_class($e));
    reply(500, ['error' => ['code' => 'INTERNAL_ERROR', 'message' => 'Error interno del servicio.']]);
});
