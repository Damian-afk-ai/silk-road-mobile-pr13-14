<?php
require __DIR__ . '/common.php';
method('GET');
$id = $_GET['id'] ?? null;
if ($id !== null && (!is_string($id) || !ctype_digit($id) || (int)$id < 1 || strlen($id) > 8)) {
    reply(400, ['error' => ['code' => 'INVALID_ID', 'message' => 'El ID debe ser un entero positivo.']]);
}
// Cache outside the document root: upstream is called at most once per ten minutes.
$cache = sys_get_temp_dir() . '/silkroad-devnationqro-catalog-v1.json';
$lock = fopen($cache . '.lock', 'c');
if (!$lock || !flock($lock, LOCK_EX)) throw new RuntimeException('Cache unavailable');
try {
    $raw = is_file($cache) ? file_get_contents($cache) : false;
    if ($raw === false || filemtime($cache) < time() - 600) {
        $ch = curl_init('https://dummyjson.com/products?limit=0');
        curl_setopt_array($ch, [CURLOPT_RETURNTRANSFER => true, CURLOPT_CONNECTTIMEOUT => 5,
            CURLOPT_TIMEOUT => 15, CURLOPT_FOLLOWLOCATION => false, CURLOPT_SSL_VERIFYPEER => true]);
        $fresh = curl_exec($ch);
        $status = curl_getinfo($ch, CURLINFO_HTTP_CODE);
        curl_close($ch);
        $decoded = is_string($fresh) ? json_decode($fresh, true) : null;
        if ($status === 200 && is_array($decoded['products'] ?? null) && count($decoded['products']) > 0) {
            file_put_contents($cache, $fresh, LOCK_EX);
            $raw = $fresh;
        } elseif ($raw === false) {
            reply(502, ['error' => ['code' => 'CATALOG_UNAVAILABLE', 'message' => 'Catálogo temporalmente no disponible.']]);
        } else {
            header('X-SilkRoad-Cache: stale');
        }
    }
} finally {
    flock($lock, LOCK_UN);
    fclose($lock);
}
$catalog = json_decode($raw, true, 512, JSON_THROW_ON_ERROR);
$products = array_map(function(array $p): array {
    return array_intersect_key($p, array_flip(['id','title','price','description','category','thumbnail']));
}, $catalog['products']);
if ($id !== null) {
    foreach ($products as $p) {
        if ($p['id'] === (int)$id) reply(200, ['product' => $p]);
    }
    reply(404, ['error' => ['code' => 'PRODUCT_NOT_FOUND', 'message' => 'Producto inexistente.']]);
}
reply(200, ['products' => $products, 'total' => count($products), 'source' => 'DummyJSON', 'servedBy' => 'silkroad.devnationqro.com']);
