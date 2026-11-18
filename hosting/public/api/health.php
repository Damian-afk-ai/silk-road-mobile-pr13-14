<?php
require __DIR__ . '/common.php';
method('GET');
reply(200, ['status' => 'ok', 'service' => 'Silk Road Mobile API', 'version' => '1.3-hostinger', 'time' => gmdate('c')]);
